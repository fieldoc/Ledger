# Microphone Capability Unification

**Date**: 2026-04-07
**Status**: Implemented (VoiceIntentRouter, unified mic on both screens)
**Implemented:** 2026-04-09

## Problem

Users encounter three different microphone experiences across three screens, each with different capabilities:

| Screen | Mic Button | Can Add Tasks | Can Plan Day | Visual |
|--------|-----------|---------------|-------------|--------|
| TaskWallScreen | Header toolbar (48dp) | Yes (6 intents) | No | WaveformVisualizer |
| CalendarScreen | Header + FAB (both mic icons) | No | Yes | WaveformVisualizer |
| PhoneHomeScreen | Bottom hub pill | Yes (6 intents) | No | ListeningPulse |

**Core confusion**: A mic icon on the calendar screen looks identical to the task wall mic icon, but does something completely different. Users expect "mic = add a task" everywhere, but calendar mic means "plan my day."

## Design Decisions

### 1. Every mic button should offer both capabilities

**Rule**: Wherever you see a mic icon, you can both add tasks and plan your day.

**Implementation**: When the user taps/clicks the mic, the voice pipeline starts identically everywhere. The AI (Gemini) determines intent from what the user says:
- "Add buy groceries to my shopping list" → task voice pipeline (ADD intent)
- "Plan my afternoon" or "What should I do today?" → day organizer pipeline
- "Mark dentist as done" → task voice pipeline (COMPLETE intent)

This eliminates the need for separate buttons and separate mental models.

### 2. Remove the duplicate VoiceFab from CalendarScreen

The CalendarScreen currently has TWO mic triggers (PlanDayButton in header + VoiceFab at bottom-right). Both do the same thing. Remove the VoiceFab and keep only the header button, matching the TaskWallScreen pattern (one mic button in the header toolbar area).

### 3. Unified voice entry point in ViewModel

Currently:
- Task voice: `startVoiceInput()` → `VoiceParsingCoordinator` → Gemini task parsing
- Day organizer: `startDayOrganizer()` → `DayOrganizerCoordinator` → Gemini day planning

**New flow**: A single `startVoiceCapture()` entry point. The transcription goes to a unified Gemini prompt that classifies intent first:
- If planning intent → route to `DayOrganizerCoordinator`
- If task intent → route to `VoiceParsingCoordinator`

### 4. Consistent visual treatment

All screens use `WaveformVisualizer` during listening (the premium, calm animation). The phone's `ListeningPulse` is replaced with `WaveformVisualizer` inside the bottom sheet for consistency.

### 5. Consistent overlay treatment per form factor

- **Wall/tablet** (TaskWallScreen, CalendarScreen): Full-screen dim + centered WaveformVisualizer + preview card overlay
- **Phone** (PhoneHomeScreen): Bottom sheet with WaveformVisualizer + preview content

The phone keeps the bottom sheet because it's the appropriate mobile pattern; the wall keeps the full-screen overlay because it's the appropriate kiosk pattern.

## Architecture

### Intent Router (new)

```
VoiceIntentRouter.kt
  Input: raw transcription text
  Output: RoutedIntent.TaskAction | RoutedIntent.DayPlanning
  
  - Uses Gemini with a lightweight classification prompt
  - Returns routing decision + original transcription
  - Falls back to TaskAction if classification is ambiguous
```

### Modified Voice Flow

```
User taps mic (any screen)
  → VoiceCaptureManager.startListening()
  → Speech recognition → raw transcription
  → VoiceIntentRouter.classifyIntent(transcription)
  → if TaskAction:
      → VoiceParsingCoordinator.handleRawTranscription(transcription)
      → Preview card (add/complete/delete/reschedule/query/amend)
  → if DayPlanning:
      → DayOrganizerCoordinator.startWithTranscription(transcription)
      → Day plan overlay
```

### Conditions for Day Planning availability

Day organizer requires: Gemini API key + calendar scope. When these aren't available:
- Intent router still classifies, but DayPlanning intents get a friendly error: "Day planning requires calendar access. Would you like to add a task instead?"
- The mic button is still always visible (it can always add tasks)

### Changes per screen

**TaskWallScreen**:
- Mic button stays in header (no visual change)
- Voice overlay gains ability to show DayOrganizerOverlay when day planning intent detected
- New: after transcription, if intent is day planning, transition to DayOrganizerOverlay

**CalendarScreen**:
- Remove VoiceFab (bottom-right FAB)
- PlanDayButton in header → rename to VoiceButton (same position, same look)
- Voice now routes through unified pipeline — can add tasks OR plan day
- DayOrganizerOverlay still renders when day planning intent is detected

**PhoneHomeScreen**:
- PhoneCaptureHub mic button stays (no visual change)
- PhoneVoiceBottomSheet: replace ListeningPulse with WaveformVisualizer
- Add day planning capability: when intent is DayPlanning, bottom sheet shows day plan preview (adapted DayOrganizerOverlay content for sheet format)

## Files to modify

1. **New**: `capture/router/VoiceIntentRouter.kt` — intent classification
2. **Modify**: `viewmodel/TaskWallViewModel.kt` — unified `startVoiceCapture()`, route after classification
3. **Modify**: `ui/screens/CalendarScreen.kt` — remove VoiceFab, rename PlanDayButton, wire unified voice
4. **Modify**: `ui/screens/TaskWallScreen.kt` — add DayOrganizerOverlay rendering for day planning intents
5. **Modify**: `ui/screens/PhoneHomeScreen.kt` — wire unified voice, add day planning preview
6. **Modify**: `ui/components/PhoneVoiceBottomSheet.kt` — replace ListeningPulse with WaveformVisualizer, add day plan preview state
7. **Modify**: `capture/repository/GeminiCaptureRepository.kt` — add intent classification method

## Out of scope

- Changing the WaveformVisualizer animation itself
- Changing the DayOrganizerOverlay plan preview UI
- Adding new voice intents beyond what exists
- Camera/scan functionality on phone
