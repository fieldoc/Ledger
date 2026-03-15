# Phone/Wall UI Unification Implementation Plan

**Date:** 2026-02-22  
**Status:** Ready for implementation

## Goal
Unify phone mode with wall mode visual language by sharing task primitives and using `LocalWallColors` end-to-end, while keeping phone interactions optimized for ~393dp touch layouts.

## Non-goals
- No behavior or data-flow changes to task loading/auth/capture pipelines.
- No wall-mode redesign.
- No new ViewModel type.

## Current Baseline (verified)
- `TaskStatusIndicator`, `DueDateBadge`, and `AnimatedTaskCompletion` exist only as `private` functions in `TaskItem.kt`.
- Phone surfaces still mix in `MaterialTheme.colorScheme` in:
  - `PhoneVoiceBottomSheet.kt`
  - `PhoneSettingsSheet.kt`
  - `PhoneHomeScreen.kt`
  - `ParsedCapturePreviewScreen.kt`
- `PhoneCaptureBar` is text-only (no icons).
- `PhoneHomeScreen` still uses `PhoneTaskRow` + `Checkbox`, not a shared visual primitive card.
- `MainActivity.kt` already owns wall theme/sync state; phone settings currently does not receive theme/sync callbacks.

## Implementation Order

### Phase 1: Foundation and shared primitives
**Files:**
- `app/build.gradle.kts`
- `app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt`
- `app/src/main/java/com/example/todowallapp/ui/components/SharedTaskPrimitives.kt` (new)

**Work:**
1. Add `material-icons-extended` dependency (BOM managed).
2. Extract these from `TaskItem.kt` into `SharedTaskPrimitives.kt` as `internal`:
- `AnimatedTaskCompletion`
- `TaskStatusIndicator`
- `DueDateBadge`
- month/day formatter used by due-date rendering
3. Keep behavior identical for wall mode by switching `TaskItem.kt` to the shared implementations.

**Done criteria:**
- `TaskItem` compiles and renders unchanged.
- No duplicate private implementations remain in `TaskItem.kt`.

### Phase 2: Phone task card and phone home layout
**Files:**
- `app/src/main/java/com/example/todowallapp/ui/components/PhoneTaskItem.kt` (new)
- `app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt`

**Work:**
1. Create `PhoneTaskItem` using shared primitives:
- compact `TaskStatusIndicator` sizing
- shared `DueDateBadge`
- `AnimatedTaskCompletion`
- light card surface + subtle top rim + 4dp urgency accent bar
2. Replace `PhoneTaskRow` usage in `PhoneHomeScreen` with `PhoneTaskItem`.
3. Remove the static "Phone Mode" header.
4. Style pending retries and message rows with card-like surfaces (`surfaceCard`, rounded corners) instead of raw text rows.

**Done criteria:**
- Phone task rows no longer use `Checkbox`.
- Task toggle remains entire-row/card tap.

### Phase 3: Capture bar and bottom sheets
**Files:**
- `app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureBar.kt`
- `app/src/main/java/com/example/todowallapp/ui/components/PhoneVoiceBottomSheet.kt`
- `app/src/main/java/com/example/todowallapp/ui/components/PhoneSettingsSheet.kt`

**Work:**
1. Rebuild `PhoneCaptureBar` with outlined Material icons:
- `CameraAlt`, `Mic`, `Sync`, `Settings`
- icon+label action cells with consistent hit area
- colors from `LocalWallColors`
2. Theme `PhoneVoiceBottomSheet` with `LocalWallColors` only.
- Keep state machine and callbacks unchanged.
- Replace all `MaterialTheme.colorScheme` usage.
3. Expand `PhoneSettingsSheet` to include:
- theme mode control (`ThemeMode` cycle)
- sync interval control (cycle through supported values)
- existing Gemini key actions
- switch mode + sign out
- themed `OutlinedTextField` colors from `LocalWallColors`

**Done criteria:**
- No `MaterialTheme.colorScheme` references remain in these files.
- Settings sheet exposes callbacks for theme/sync changes.

### Phase 4: Parsed capture preview theming
**File:**
- `app/src/main/java/com/example/todowallapp/ui/screens/ParsedCapturePreviewScreen.kt`

**Work:**
1. Keep screen behavior and recursive task editing flow unchanged.
2. Replace unstyled text actions with card/pill treatments aligned to wall palette.
3. Replace `MaterialTheme.colorScheme` usage with `LocalWallColors`.
4. Apply custom `OutlinedTextFieldDefaults.colors` to all outline fields.

**Done criteria:**
- Parsing/review interactions behave the same.
- Visual treatment is card-based and uses wall tokens only.

### Phase 5: MainActivity wiring for new phone settings inputs
**File:**
- `app/src/main/java/com/example/todowallapp/MainActivity.kt`

**Work:**
1. In `TaskWallApp`, collect `syncIntervalMinutes` from `wallViewModel` (if not already collected there).
2. Extend `PhoneModeContent` params to include:
- `themeMode: ThemeMode`
- `lightStartHour: Int`
- `lightEndHour: Int`
- `syncIntervalMinutes: Int`
- `onThemeModeChange: (ThemeMode) -> Unit`
- `onSyncIntervalChange: (Int) -> Unit`
3. Pass those into `PhoneSettingsSheet` and wire callbacks to existing wall VM APIs:
- `updateThemeSettings(mode, lightStartHour, lightEndHour)`
- `updateSyncInterval(minutes)`

**Done criteria:**
- Theme and sync controls in phone settings update persisted wall preferences.
- No ViewModel API changes required.

## Verification Plan

### Build and lint
Run on Windows PowerShell:
```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lint
.\gradlew.bat :app:testDebugUnitTest
```

### Source checks
Ensure zero `MaterialTheme.colorScheme` in phone-mode surfaces:
```powershell
rg -n "MaterialTheme\.colorScheme" \
  app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureBar.kt \
  app/src/main/java/com/example/todowallapp/ui/components/PhoneVoiceBottomSheet.kt \
  app/src/main/java/com/example/todowallapp/ui/components/PhoneSettingsSheet.kt \
  app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt \
  app/src/main/java/com/example/todowallapp/ui/screens/ParsedCapturePreviewScreen.kt
```

### Manual smoke checklist
1. Phone task tap toggles completion and shows shared completion styling.
2. Due badges and urgency accents match wall palette.
3. Capture bar actions remain functional (camera/voice/refresh/settings).
4. Voice sheet states (idle/listening/processing/preview/error) still transition correctly.
5. Settings sheet can save/clear Gemini key, switch mode, sign out, change theme, and change sync interval.
6. Parsed capture review supports edit/remove/assign-list/add-all without regressions.

## Risks and mitigations
- Shared primitive extraction can accidentally change wall visuals.
  - Mitigation: keep signatures stable; do a visual regression check on wall task rows.
- Phone settings wiring can drift from wall settings semantics.
  - Mitigation: call the same wall VM update methods already used by wall settings.
- Recursive parsed-task editor can regress during restyling.
  - Mitigation: keep recursion and callbacks untouched; constrain changes to styling.

## Rollout notes
- Implement in phase order; each phase should compile independently.
- Prefer small commits per phase during execution.
