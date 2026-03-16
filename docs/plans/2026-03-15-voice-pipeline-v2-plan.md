# Voice Pipeline V2 Implementation Plan

**Spec**: `docs/superpowers/specs/2026-03-15-voice-pipeline-v2-design.md`

## Execution Order

Steps 1-3 are data model + backend (no UI). Step 4 is ViewModel. Step 5 is UI. Step 6 is verification.

### Step 1: New data types + update VoiceInputState
**Files**: `GeminiCaptureRepository.kt`, `VoiceCaptureManager.kt`
- Add `VoiceIntent` enum, `PreferredTime` enum, `ParsedVoiceTaskItem`, `ParsedVoiceResponse`
- Update `VoiceInputState.Preview` to hold `ParsedVoiceResponse`
- Update `showPreview()` to accept `ParsedVoiceResponse`
- Add convenience `showPreviewFallback(rawText)` for no-API-key path

### Step 2: New prompt + JSON parser
**Files**: `GeminiCaptureRepository.kt`
- Add `buildVoicePromptV2()` with all 6 improvements baked into prompt
- Add `parseVoiceResponseJson()` to parse new schema
- Add `parseVoiceInputV2()` public method
- Keep old `parseVoiceInput` + `ParsedVoiceTask` (other code may reference)

### Step 3: Update VoiceParsingCoordinator
**Files**: `VoiceParsingCoordinator.kt`
- Replace individual parsed fields with `ParsedVoiceResponse?`
- Call `parseVoiceInputV2` instead of `parseVoiceInput`
- Update `showPreview` calls to use new signature

### Step 4: Update ViewModels
**Files**: `TaskWallViewModel.kt`, `PhoneCaptureViewModel.kt`
- `confirmVoiceTasks()` replaces `confirmVoiceTask()`
- Handle ADD/COMPLETE/DELETE/RESCHEDULE intents with confirm-then-execute
- QUERY/AMEND: show info or modify last task
- Both ViewModels symmetric via coordinator

### Step 5: Update UI preview card
**Files**: `TaskWallScreen.kt`
- Multi-task list in preview overlay
- Intent labels for non-ADD
- Duplicate warning lines
- Batch confirm on encoder click

### Step 6: Verify
- Build compiles
- Manual review of prompt quality
