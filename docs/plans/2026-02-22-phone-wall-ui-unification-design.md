# Phone Mode UI Unification Design

**Date**: 2026-02-22
**Status**: Approved
**Target device**: Galaxy A54 (393dp wide, 6.4" FHD+)
**Tablet fallback**: Phone mode on wall tablet must remain serviceable

## Context

Phone mode is a capture-first companion to the wall display. Its primary job is adding tasks (voice, camera OCR). Secondary job: scanning existing tasks to avoid duplicates. The wall remains the trust anchor for the full task map.

Currently, phone mode uses stock Material components (Checkbox, raw text buttons, default ModalBottomSheet theming) that feel like a different app from the polished wall mode. This design unifies the visual language while keeping phone layouts optimized for 393dp width and touch interaction.

## Approach: Shared Component Library

Extract visual primitives from wall mode into shared composables. Build phone-specific screens from them. Two separate task item components (wall's `TaskItem`, phone's `PhoneTaskItem`), each purpose-built for its context.

### Shared primitives (already exist, used by both modes):
- `TaskStatusIndicator` — circular ring + checkmark fill (no subtask progress arc on phone)
- `DueDateBadge` — relative date labels with urgency colors
- `AnimatedTaskCompletion` — alpha fade + strikethrough wrapper
- `LocalWallColors` — exclusive color system for both modes
- `WallShapes`, `WallAnimations` constants

### Phone-specific components (new or rebuilt):
- `PhoneTaskItem` — light card
- `PhoneCaptureBar` — icons + labels
- `PhoneVoiceBottomSheet` — pulse indicator, no partial text
- `PhoneSettingsSheet` — merged settings
- `ParsedCapturePreviewScreen` — themed properly

## Component Specs

### 1. PhoneTaskItem

Light card, ~56-60dp height. Layout:

```
┌─[urgency bar 4dp]──────────────────────────┐
│  ○  Task title here                Tomorrow │
└─────────────────────────────────────────────┘
```

- **Status indicator**: `TaskStatusIndicator` at 24dp (vs wall's 36dp). No progress arc.
- **Title**: `titleSmall` (16sp), single line, ellipsis overflow.
- **Due date**: `DueDateBadge` (shared) on the right.
- **Surface**: `surfaceCard`, `CardCornerRadius` (16dp), 1dp rim highlight.
- **Urgency**: Left-edge gradient bar, 4dp wide (wall uses 6dp).
- **Completion**: `AnimatedTaskCompletion` wrapper (shared).
- **Touch**: Entire card clickable, toggles completion.
- **Excluded**: notes, subtasks, encoder glow, ambient mode, scale animation, scheduled badge.

At 393dp with 12dp screen padding + 12dp card padding, title gets ~300dp (~35-40 chars).

### 2. PhoneCaptureBar

Pinned bottom bar with 4 actions, Material icons above labels.

- Container: `surfaceElevated`, `RoundedCornerShape(18.dp)`.
- Icons: `CameraAlt`, `Mic`, `Sync`, `Settings` from `material-icons-extended`.
- Icon tint: `textSecondary`. Label: `textMuted`, `labelSmall`.
- Each action: Column with icon + label, equal weight, 48dp min touch target.
- Pinned with `navigationBarsPadding`.

### 3. PhoneVoiceBottomSheet

Bottom sheet with pulse indicator during listening.

- Container: `ModalBottomSheet` with `containerColor = surfaceElevated`.
- **Idle**: "Tap to start" prompt + Start/Cancel buttons.
- **Listening**: Pulsing circle (accent color, animates radius with audio amplitude). No partial transcription.
- **Processing**: Simple loading indicator.
- **Preview**: Transcribed text + Add Task / Retry / Cancel.
- **Error**: Error message + Dismiss / Retry.
- All colors via `LocalWallColors`.

### 4. PhoneSettingsSheet

Merged settings from both modes.

Items:
- Theme mode (cycle: Dark/Auto/Light)
- Sync interval (cycle through options)
- Gemini API key (OutlinedTextField, Save/Clear)
- Switch Mode
- Sign Out

Uses `LocalWallColors` exclusively. `OutlinedTextField` gets custom colors.

### 5. ParsedCapturePreviewScreen

Same layout, themed properly:
- List sections: `surfaceCard` background, `CardCornerRadius` corners.
- `OutlinedTextField`: custom colors via `OutlinedTextFieldDefaults.colors()` using `LocalWallColors`.
- Action buttons: pill-shaped backgrounds instead of bare text.
- "Delete" buttons: `urgencyOverdue` color.
- Bottom actions ("Cancel", "Add All"): visual weight via background fill.
- All `MaterialTheme.colorScheme` refs replaced.

### 6. PhoneHomeScreen

- Remove "Phone Mode" text header.
- Task lists as sections: `titleSmall` header, `dividerColor` separator.
- Pending retries in `surfaceCard` cards.
- Error/info messages in `surfaceCard` with rounded corners.

### 7. Color System Cleanup

All phone components use `LocalWallColors.current.*` exclusively:
- `MaterialTheme.colorScheme.primary` -> `accentPrimary`
- `MaterialTheme.colorScheme.error` -> `urgencyOverdue`

Applies to: PhoneVoiceBottomSheet, PhoneSettingsSheet, PhoneHomeScreen, ParsedCapturePreviewScreen.

## What's NOT Changing

- Wall mode components untouched (TaskItem, SettingsPanel, WaveformVisualizer, TaskWallScreen).
- Typography scale stays as-is (works for both phone and wall distances).
- ModeSelectorScreen stays as-is (acceptable, shown once).
- No new ViewModel — PhoneCaptureViewModel continues to own phone state.
- No subtask display on phone (wall is the full-map display).
