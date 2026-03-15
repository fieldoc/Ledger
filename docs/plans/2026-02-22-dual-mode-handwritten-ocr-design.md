# Dual-Mode App + Handwritten OCR Task Capture

**Date:** 2026-02-22  
**Status:** Approved and expanded to implementation recipe

## 0. Purpose

This document now serves as both:

1. The design decision record.
2. The execution recipe for implementation.

Goal: implement dual-mode app behavior and handwritten task capture with minimal rework, clear module boundaries, and predictable error handling.

## 1. Product Decisions (Locked)

1. Single APK, two runtime modes: `Wall` and `Phone`.
2. Persist selected mode; after auth, launch directly into that mode.
3. Phone mode is capture-first (camera + voice), browse-second.
4. Camera capture uses ML Kit `GmsDocumentScanner` (turnkey document UX).
5. OCR/structure extraction uses Gemini Flash multimodal.
6. User confirms parsed tasks before any writes to Google Tasks.
7. Voice in phone mode reuses `SpeechRecognizer` path only (no Gemini voice parsing in this scope).
8. Gemini API key is user-provided and stored securely.

## 2. Current Baseline (Code Reality on 2026-02-22)

1. App currently has one activity and one large view model:
   `MainActivity.kt` and `TaskWallViewModel.kt` (~1,154 lines, 24 UI state fields, 16+ public functions).
2. Wall behavior is globally forced in `MainActivity`:
   immersive flags + keep-screen-on are always enabled.
3. Task data source exists through `GoogleTasksRepository`:
   list/read/complete/uncomplete/create single task.
4. Voice capture exists (`VoiceCaptureManager`) and task create path exists from voice preview.
5. DataStore already exists via `Context.dataStore` and stores wall settings.
6. No navigation library in use; routing is `when` branches in Compose.
7. **Calendar integration already exists**: `GoogleCalendarRepository`, `CalendarScreen`,
   and a task-to-calendar promotion system (`PromotionDraft`/`PromotionSheet`).
   The authenticated view uses a `HorizontalPager` to switch between `TaskWallScreen`
   and `CalendarScreen`. The ViewModel contains ~200 lines of calendar logic.
   The mode selector must sit above this pager in the composable tree.

Implementation must preserve existing wall and calendar behavior while adding phone behavior cleanly.

## 3. Architecture Blueprint

### 3.1 Domain additions

Add app mode domain:

```kotlin
enum class AppMode { WALL, PHONE }
```

Persisted mode preference:

- `mode_preference`: `WALL`, `PHONE`, or absent (first run).

Phone capture domain models (new package `capture/model`):

- `ParsedCapture`
- `ParsedListDraft`
- `ParsedTaskDraft` (supports recursive `subtasks`)
- `ListTarget` (`EXISTING`, `NEW_LIST`)

### 3.2 Layering

1. UI layer:
   mode selector, phone screens, preview editor.
2. ViewModel layer — **two ViewModels, not one**:
   - `TaskWallViewModel`: existing wall + calendar logic, untouched.
   - `PhoneCaptureViewModel` (new): owns scanner state, Gemini parsing state,
     preview editor state, phone voice sheet state.
   - Shared data (task lists, auth state) accessed through the repository layer,
     not by cramming both modes into one ViewModel.
   - `TaskWallViewModel` is already 1,154 lines with calendar integration.
     Extending it further is not viable.
3. Data layer:
   existing `GoogleTasksRepository` + new Gemini and scanner adapters.
4. Secure config layer:
   `GeminiKeyStore` using `EncryptedSharedPreferences`.

### 3.3 Why this structure

1. Keeps existing wall flow stable — `TaskWallViewModel` is not modified for phone features.
2. Adds phone feature in isolated packages with its own ViewModel.
3. Avoids introducing navigation framework unless needed.
4. Enables incremental implementation with small, testable slices.
5. Shared repository layer prevents data duplication between modes.

### 3.4 Auth and repository lifecycle contract (critical)

1. Root app state in `MainActivity` owns authenticated session state.
2. On successful sign-in, initialize shared repositories exactly once with the active account:
   `GoogleTasksRepository.initialize(account)` and, when available, `GoogleCalendarRepository.initialize(accountWithCalendarScope)`.
3. Both `TaskWallViewModel` and `PhoneCaptureViewModel` are created with the same repository instances through factories.
4. `PhoneCaptureViewModel` must gate all repository calls behind `sessionReady = true`.
5. No ViewModel may assume repository readiness before this initialization contract is satisfied.

## 4. Dependency and Compatibility Plan

### 4.1 New dependencies

Add in `gradle/libs.versions.toml` and `app/build.gradle.kts`:

1. `com.google.android.gms:play-services-mlkit-document-scanner`
2. `com.google.ai.client.generativeai:generativeai`
3. `androidx.security:security-crypto`
4. `androidx.work:work-runtime-ktx` (only if offline "save and retry later" is implemented now)
5. Optional but recommended:
   `com.google.code.gson:gson` as explicit dependency for deterministic JSON parsing code.

Use latest stable versions compatible with:

- `compileSdk = 35`
- `minSdk = 23`
- Kotlin `2.0.21`

### 4.2 Manifest and runtime requirements

1. Keep `INTERNET` and `ACCESS_NETWORK_STATE`.
2. Camera permission:
   verify ML Kit document scanner behavior in this app path; if needed, add `android.permission.CAMERA`.
3. Keep `RECORD_AUDIO` for voice.
4. Add ProGuard/R8 keep rules for Gemini and scanner if release build strips required classes.

### 4.3 Play Services compatibility gate

At runtime, scanner launch should handle:

1. Google Play Services unavailable/outdated.
2. Scanner intent resolution failure.

Fallback behavior:

- show actionable error with retry and "open settings" option where possible.

## 5. End-to-End Information Flow

### 5.1 Mode boot flow

1. Auth resolved.
2. Initialize shared repositories with the authenticated account.
3. Mark app session ready.
4. Read persisted mode from DataStore.
5. If mode absent -> show `ModeSelectorScreen`.
6. If mode present -> route to selected mode.
7. Switching mode sets preference and routes without process restart.

### 5.2 Camera capture flow

1. User taps camera in phone capture bar.
2. Guard: Gemini key exists.
3. Launch `GmsDocumentScanner`.
4. Receive scanned page URI.
5. Decode, downscale, and compress image.
6. Send image + prompt + existing list names to Gemini.
7. Parse and validate JSON.
8. Show preview editor.
9. User confirms -> commit to Google Tasks.
10. Refresh task lists and return to phone home.

### 5.3 Commit flow to Google Tasks

1. Resolve each parsed list target (`existing` vs `new`).
2. Create missing lists first.
3. Create tasks depth-first so parent IDs exist before child inserts.
4. Include due date when present.
5. Return commit summary: created lists/tasks, failed items.

## 6. Algorithms and Data Contracts

### 6.1 Gemini response contract (strict)

Request JSON only with this shape:

```json
{
  "lists": [
    {
      "name": "string",
      "target": "existing|new_list",
      "existingListId": "string|null",
      "tasks": [
        {
          "title": "string",
          "dueDate": "YYYY-MM-DD|null",
          "subtasks": []
        }
      ]
    }
  ]
}
```

Validation rules:

1. `lists` non-null.
2. `title` non-blank after trim.
3. `dueDate` parseable ISO date or null.
4. Supported nesting depth for this phase is 2 levels (parent + child), matching current task UI.
5. Any parsed node deeper than level 2 is auto-flattened into a level-2 child title using path prefixing (to avoid silent data loss), and a warning is surfaced in preview.
6. Drop empty/invalid nodes and keep a warning list for UI.

### 6.2 Local list routing safety net

Even if Gemini routes lists, validate locally:

1. If `target=existing` but `existingListId` missing/not found:
   try normalized name match.
2. Name normalization:
   lowercase, trim, collapse whitespace, remove punctuation.
3. Match priority:
   exact normalized match -> startsWith/contains -> fallback `new_list`.

### 6.3 Task commit algorithm (deterministic)

Pseudo flow:

```text
for each parsed list:
  resolve final listId (existing or create new)
  normalize task tree to max depth 2
  create tasks recursively:
    createTask(listId, title, dueDate, parentId?)
    for child in subtasks:
      recurse(child, parentId = createdTaskId)
```

Implementation details:

1. Sequential inserts within one list (parent dependency).
2. Optional parallelism across independent lists only after basic path is stable.
3. Hard timeout per network operation with user-visible retry.
4. Return partial success summary when atomicity is impossible.

## 7. Concrete File Plan

### 7.1 New files (proposed)

1. `app/src/main/java/com/example/todowallapp/data/model/AppMode.kt`
2. `app/src/main/java/com/example/todowallapp/data/repository/ModePreferenceRepository.kt`
3. `app/src/main/java/com/example/todowallapp/viewmodel/PhoneCaptureViewModel.kt`
4. `app/src/main/java/com/example/todowallapp/capture/model/ParsedCapture.kt`
5. `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt`
6. `app/src/main/java/com/example/todowallapp/capture/repository/ScannerRepository.kt`
7. `app/src/main/java/com/example/todowallapp/security/GeminiKeyStore.kt`
8. `app/src/main/java/com/example/todowallapp/ui/screens/ModeSelectorScreen.kt`
9. `app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt`
10. `app/src/main/java/com/example/todowallapp/ui/screens/ParsedCapturePreviewScreen.kt`
11. `app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureBar.kt`
12. `app/src/main/java/com/example/todowallapp/ui/components/PhoneVoiceBottomSheet.kt`
13. `app/src/main/java/com/example/todowallapp/ui/components/PhoneSettingsSheet.kt`

If offline retry is in this scope:

14. `app/src/main/java/com/example/todowallapp/capture/repository/PendingCaptureStore.kt`
15. `app/src/main/java/com/example/todowallapp/capture/work/CaptureRetryWorker.kt`

### 7.2 Existing files to modify

1. `app/src/main/java/com/example/todowallapp/MainActivity.kt`
2. `app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt`
3. `app/src/main/java/com/example/todowallapp/ui/components/SettingsPanel.kt`
4. `app/src/main/AndroidManifest.xml`
5. `app/build.gradle.kts`
6. `gradle/libs.versions.toml`
7. `app/proguard-rules.pro`

## 8. Stage-by-Stage Implementation Recipe

### Stage 1: Dependency and build plumbing

1. Add dependencies.
2. Compile debug and release.
3. Fix R8/proguard if needed.

Done when:

- `:app:compileDebugKotlin` passes.
- `:app:compileReleaseKotlin` passes.
- `assembleDebug` passes.
- `assembleRelease` passes.

### Stage 2: App mode persistence and routing

1. Add `AppMode` enum.
2. Add mode key in DataStore and repository helpers.
3. Add `ModeSelectorScreen`.
4. Update `TaskWallApp` root routing:
   sign-in -> selector or persisted mode destination.
5. Add switch actions from both modes back to selector.

Done when:

- Fresh install shows selector after auth.
- Relaunch auto-enters previously selected mode.

### Stage 3: Window/system behavior split by mode

1. Replace global always-on/immersive forcing with mode-aware application.
2. Wall mode:
   keep current behavior unchanged.
3. Phone mode:
   disable immersive and keep-screen-on.

Technique:

- Use an activity-level controller called from Compose via state changes.

Done when:

- Visual system chrome differs correctly by mode.

### Stage 4: Phone mode shell and browser

1. Create `PhoneCaptureViewModel`:
   owns phone-specific state (scanner, Gemini parsing, preview editing, voice sheet).
   Reads shared task/list data from shared repository instances provided by the root authenticated session.
   It does not initialize repositories itself.
2. Create `PhoneHomeScreen` with:
   simple expandable task lists,
   task completion by tap,
   bottom capture bar.
3. Add minimal phone settings sheet:
   Gemini key entry, switch mode, sign out.
4. `TaskWallViewModel` is not touched in this stage.

Done when:

- Phone mode can browse and complete tasks without wall UI artifacts.

### Stage 5: Secure Gemini key management

1. Implement `GeminiKeyStore` using `EncryptedSharedPreferences`.
2. Add set/get/clear APIs.
3. Add key validation call (lightweight Gemini request).
4. Gate camera action if key missing.

Done when:

- Invalid/missing key blocks scanner with clear prompt.

### Stage 6: Scanner integration

1. Wrap ML Kit scanner launch in `ScannerRepository`.
2. Use `StartIntentSenderForResult` contract.
3. Handle success, cancel, and service errors.
4. Decode scanned URI safely:
   bounded resolution and JPEG compression before upload.

Compatibility checks:

1. First run permission flow.
2. Play Services unavailable path.

Done when:

- Camera capture yields image bytes consistently on supported devices.

### Stage 7: Gemini parsing service

1. Build prompt with:
   existing list names and IDs,
   strict JSON instructions,
   fallback behavior for ambiguous headings.
2. Call Gemini Flash model.
3. Parse JSON to typed DTO.
4. Retry once with stricter prompt on malformed JSON.
5. Return structured errors for UI mapping.

Done when:

- Parsed DTO is always validated before UI preview.

### Stage 8: Parsed preview editor

1. Build grouped list UI for parsed output.
2. Support edits:
   task title edit, delete item, list reassign.
3. Show badges for existing/new list targets.
4. Actions: `Add All`, `Cancel`.

Done when:

- User can correct obvious OCR mistakes without retaking photo.

### Stage 9: Google Tasks write pipeline extensions

1. Extend `GoogleTasksRepository`:
   `createTaskList(title)`,
   `createTask(taskListId, title, dueDate, parentId?)`.
2. Implement recursive commit orchestrator over the normalized max-depth-2 tree.
3. Return commit summary object:
   created lists/tasks and failures.
4. Trigger refresh on completion.

Done when:

- Parent/child hierarchy is correctly created in Google Tasks.

### Stage 10: Voice in phone mode bottom sheet UX

1. Reuse `VoiceCaptureManager`.
2. Build `PhoneVoiceBottomSheet` states:
   listening, processing, preview, error.
3. Reuse existing confirm path to create a single task.
4. Keep wall voice UX unchanged.

Done when:

- Phone mic flow works fully without wall full-screen overlays.

### Stage 11: Offline and retry policy

Required behavior from design:

- on network failure after scan, offer "save photo and retry later".

Implementation options:

1. Simple now:
   persist pending capture file + metadata, manual retry from phone home.
2. Full now:
   add `WorkManager` auto-retry on network restored.

Recommended for first pass:

- implement simple manual retry; add WorkManager in follow-up.

Done when:

- User can preserve scan intent if parse call fails offline.

### Stage 12: QA and hardening

1. Add unit tests and instrumentation tests (see section 9).
2. Validate low-memory and orientation resilience where applicable.
3. Validate release build and minified run.
4. Validate both modes on device, including mode switching loops.

Done when:

- Test matrix green and no regressions in wall mode behavior.

## 9. Test Plan (Must Implement)

### 9.1 Unit tests

1. `Task.getUrgencyLevel()` edge dates.
2. Gemini JSON parser:
   valid JSON, fenced JSON, malformed JSON, deep nesting flatten-to-depth-2 behavior.
3. List routing fallback algorithm.
4. Commit recursion:
   parent before child ordering.
5. Gemini key store read/write/clear.

### 9.2 Integration/instrumented tests

1. Auth -> mode selector -> mode persistence.
2. Mode switching from both modes.
3. Phone screen states:
   loading/empty/error/syncing.
4. Phone voice sheet state transitions.
5. Preview editing interactions.

### 9.3 Manual test checklist

1. Wall mode remains immersive and always-on.
2. Phone mode shows system bars and standard touch UX.
3. Scanner success path and cancel path.
4. Gemini parse success and malformed response retry path.
5. Commit creates expected lists/subtasks in Google Tasks web UI.
6. Missing key path and key validation errors.
7. Offline parse fail -> save for later path.

## 10. Risk Register and Mitigations

1. Risk: One large ViewModel gets more complex.
   Mitigation: separate `PhoneCaptureViewModel` from Stage 4 onward;
   `TaskWallViewModel` is not modified for phone features.
   Shared data accessed through repository layer.
2. Risk: Gemini non-deterministic JSON quality.
   Mitigation: strict prompt + typed validation + one retry.
3. Risk: Scanner device compatibility variance.
   Mitigation: explicit Play Services checks and graceful fallback.
4. Risk: Partial writes to Google Tasks.
   Mitigation: summary with failed items and retry option.
5. Risk: Wall regressions from shared code paths.
   Mitigation: stage gating and regression checks per stage.

## 11. Rollout Strategy

1. Implement in feature stages behind an internal flag if needed.
2. Keep wall mode default for existing users until phone mode is stable.
3. Merge in small PRs aligned to stages above.
4. Each stage must pass compile + core tests before next stage starts.

## 12. Final Scope Boundaries

In scope:

1. Dual-mode architecture and mode persistence.
2. Phone mode capture UX.
3. Handwritten scan parse and preview-confirm commit.
4. Secure Gemini key storage.
5. Phone voice sheet with existing recognizer path.

Out of scope for this phase:

1. Gemini-powered voice understanding.
2. Advanced task structure editing (drag/reparent).
3. Full background auto retry orchestration unless explicitly chosen in Stage 11 option 2.

---

This plan is now implementation-ready. Execute stages in order to minimize wall-mode regression risk.
