# Day Organizer

Load when touching `DayOrganizerCoordinator`, `DayOrganizerOverlay`, `DayPlan`, `PlanBlock`, ghost-block rendering in `CalendarDayView`, or plan-undo/sparkle-badge logic.

## Architecture

- **`DayOrganizerCoordinator`** runs a 6-state planning state machine: `Idle → Processing → PlanReady → Confirming → PartialSuccess / Error`.
- **Constructor**: `DayOrganizerCoordinator(geminiCaptureRepository, geminiKeyStore, calendarRepository, tasksRepository)` — **no `VoiceCaptureManager`**. The coordinator receives text; it does NOT own voice capture.
- **Entry points**: `generatePlan(transcription, scope, ...)` and `adjustPlan(adjustmentText)`.
- **Model**: `DayPlan` = list of `PlanBlock`s with 12 categories, per-block confidence + flexibility, `EnergyProfile` setting.

## UI

- **`DayOrganizerOverlay`** renders the conversation UI and per-block, encoder-navigable plan preview.
- **Ghost blocks** render in `CalendarDayView` during preview, before acceptance.
- Accepted plans write events to Google Calendar via `GoogleCalendarRepository`.
- Plan acceptance: **8-second undo** (`PlanUndoState`) and sets `isDayOrganized` sparkle badge on `ClockHeader`.
- `recentlyCreatedEventIds` StateFlow drives a 3-second highlight on freshly-written events.

## Known Gap (TaskWallScreen wiring)

The `DayOrganizerOverlay` call in `TaskWallScreen` omits `onRetryFailed`, `onSetPendingRemove`, `onConfirmRemoveBlock`, and `taskNameById`. Block-removal and "from: task" labels silently no-op in wall mode. **`CalendarScreen` wires them correctly** — copy that call site as the reference when fixing wall mode.
