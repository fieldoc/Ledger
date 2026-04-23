# Project Structure

Load when you need a map of the codebase — exploring a new feature area, deciding where a new file belongs, or wiring up a cross-package dependency. For pinpoint navigation, use Grep/Glob (see `kotlin-lsp.md`); this file is the high-level layout.

```
app/src/main/java/com/example/todowallapp/
├── MainActivity.kt              # Entry point, kiosk mode, page navigation (Tasks/Calendar)
├── auth/
│   └── GoogleAuthManager.kt     # Google Sign-In and auth state
├── capture/
│   ├── DayOrganizerCoordinator.kt    # Gemini day planning state machine
│   ├── VoiceParsingCoordinator.kt    # Orchestrates voice → parse → route pipeline
│   ├── model/
│   │   └── ParsedCapture.kt          # Structured result from Gemini voice parsing
│   └── repository/
│       ├── GeminiCaptureRepository.kt    # Gemini AI voice parsing (NL → structured)
│       ├── GeminiJsonParser.kt           # JSON extraction from Gemini responses
│       ├── CaptureCommitOrchestrator.kt  # Commits parsed captures to Tasks API
│       ├── PendingCaptureStore.kt        # Draft captures awaiting confirmation
│       ├── ListRouting.kt                # Assigns captured tasks to correct list
│       └── ScannerRepository.kt          # QR/barcode scanning support
├── data/
│   ├── model/
│   │   ├── Task.kt                  # Task, TaskList, TaskUrgency
│   │   ├── CalendarEvent.kt
│   │   ├── CalendarViewMode.kt      # MONTH / WEEK / DAY enum
│   │   ├── AppMode.kt               # WALL / PHONE enum
│   │   ├── DayPlan.kt               # PlanBlock, 12 categories
│   │   ├── TaskListWithTasks.kt     # Joined model used in Day Organizer context
│   │   ├── TaskMetadata.kt          # Extended metadata (energy, recurrence)
│   │   └── WeatherCondition.kt
│   └── repository/
│       ├── GoogleTasksRepository.kt        # Google Tasks API wrapper
│       ├── GoogleCalendarRepository.kt     # Google Calendar API wrapper
│       ├── WeatherRepository.kt            # Open-Meteo data fetching
│       ├── AppPreferences.kt               # DataStore keys and defaults
│       ├── ModePreferenceRepository.kt     # Persists WALL/PHONE mode
│       └── GoogleApiTransportFactory.kt    # HTTP transport for Google API clients
├── security/
│   ├── GeminiKeyStore.kt        # Encrypted Gemini API key storage
│   └── WeatherKeyStore.kt       # Encrypted weather API key storage
├── viewmodel/
│   ├── TaskWallViewModel.kt     # ~2461 lines — UI state, sync, voice, calendar, settings
│   └── PhoneCaptureViewModel.kt # Phone-mode capture/task state
├── voice/
│   └── VoiceCaptureManager.kt   # Android SpeechRecognizer wrapper
├── util/
│   ├── ConnectivityMonitor.kt   # Network state monitoring
│   └── ...
└── ui/
    ├── components/              # Per-feature Composables (Task/Calendar/Voice/Day Organizer/Phone)
    ├── screens/
    │   ├── TaskWallScreen.kt    # ~2319 lines — focus/nav, ambient, overlays
    │   ├── CalendarScreen.kt    # MONTH/WEEK/DAY views (called from MainActivity)
    │   ├── PhoneHomeScreen.kt
    │   ├── ModeSelectorScreen.kt
    │   ├── ParsedCapturePreviewScreen.kt
    │   └── SignInScreen.kt
    ├── theme/                   # Material3 theme, WallColors, typography
    └── utils/
        ├── Haptics.kt
        └── LayoutDimensions.kt
```

## Key file sizes (orientation aid)

- `viewmodel/TaskWallViewModel.kt` ~2461 lines — single ViewModel, all wall-mode state
- `ui/screens/TaskWallScreen.kt` ~2319 lines — focus/navigation, ambient mode, voice overlay
- `ui/components/SettingsPanel.kt` ~1120 lines — encoder-navigable settings overlay
- `MainActivity.kt` ~906 lines — entry point, kiosk/immersive, auth launcher (`CalendarScreen` called ~line 597)
- `ui/components/TaskItem.kt` ~685 lines — task row with completion animation, urgency
- `data/repository/GoogleTasksRepository.kt` ~325 lines

## Notable component locations

- **TaskWall components**: `TaskItem`, `ClockHeader`, `WaveformVisualizer`, `UndoToast`, `ViewSwitcherPill`, `TaskContextMenu`, `TaskDetailOverlay`, `SearchFilterOverlay`, `PromotionSheet`, `TaskPickerOverlay`, `RecurrencePickerOverlay`, `NextActionSpotlight`, `PageIndicator`.
- **Calendar components**: `CalendarMonthView`, `CalendarWeekView`, `CalendarDayView`, `Calendar3DayView`, `WeekStrip`, `EventActionMenu`, `WeatherContextStrip`.
- **Day Organizer**: `DayOrganizerOverlay` (component), `DayOrganizerCoordinator` (`capture/`).
- **Phone-mode**: `PhoneTaskItem`, `PhoneCaptureBar`, `PhoneVoiceBottomSheet`, `PhoneSettingsSheet`, `PhoneAccordionSection`.
