# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow & Tool Delegation

Claude handles all tasks — architecture, implementation, research, and review. Codex and Gemini CLI are available as optional accelerators for specific scenarios.

### Codex - Optional: Code Implementation
Use when delegating large, self-contained coding tasks non-interactively (e.g. writing a new file, adding a single well-scoped function). See "Using Codex CLI" section for syntax.

### Gemini - Optional: Large-Context Research
Use when analyzing 5+ files simultaneously or the full codebase in one shot. See "Using Gemini CLI" section for syntax.

## Project Overview

ToDoWallApp is an ambient life management surface — a wall-mounted Android kiosk that serves as an always-on cognitive partner. It integrates Google Tasks, Google Calendar, and AI-powered voice input into a calm, premium display designed to hold the complexity of daily life so the user's mind doesn't have to.

The app runs in fullscreen/immersive mode with screen-always-on functionality, operated primarily via a rotary encoder (3 inputs: rotate CW, rotate CCW, click). It is designed for homes or offices where a dedicated wall display replaces the need to check phones and apps for task/schedule awareness.

### Core Purpose: Externalizing Mental Load
The fundamental reason this app exists is to **reduce thought clutter and cognitive overhead**. The user gets wrapped up in project ideas, nested subtasks, scheduling decisions, and "what should I do next?" spirals — the kind of mental overhead that pulls you out of the present moment. The wall holds complexity so the mind doesn't have to.

This shapes every design decision:
- **Capture must be frictionless** — if it takes effort to offload a thought, the thought stays in your head. Voice input exists to serve this.
- **The display must feel trustworthy** — if you don't trust the system to hold your tasks, you won't stop mentally rehearsing them. Reliable sync, clear visibility, and always-on presence build that trust.
- **The UI must be calm, not anxiety-inducing** — a wall of screaming red overdue items defeats the purpose. The display should acknowledge urgency without creating stress.
- **Nested/complex projects belong here** — the app should gracefully handle deep task structures, not just flat lists. The wall can hold the project map so you can focus on one step at a time.
- **Scheduling decisions belong here** — calendar integration and AI-assisted day planning mean the wall can handle the "what fits where?" puzzle that burns executive function.
- **Ambient awareness reduces phone-checking** — weather, events, delivery tracking, and notifications can live on the wall so you don't have to pick up your phone to check.
- **The wall is a window, not a dashboard** — new information surfaces (weather, local events, habits) should whisper, not shout. They're there when you glance; invisible when you don't.

### Scope Evolution
The app is expanding beyond task display into an **ambient intelligence hub**. Current and planned capabilities:

| Layer | Status | Description |
|-------|--------|-------------|
| Task Display | Built | Google Tasks sync, accordion folders, urgency, completion animations |
| Calendar Integration | Built | Google Calendar sync, month/week/day views, task-to-event promotion |
| Voice Capture | Built | SpeechRecognizer + Gemini AI parsing. Waveform overlay, draft card, confirm/cancel all implemented. |
| Ambient Modes | Built | Two-tier quiet/sleep system with light sensing and schedule |
| Day Organizer | Built | Gemini-powered day planning: DayOrganizerCoordinator, DayOrganizerOverlay, per-block UX, undo, sparkle badge |
| Weather Awareness | Built (partial) | WeatherRepository + WeatherContextStrip exist; calendar forecast tints pending |
| Community Events | Planned | Local event discovery with preference learning |
| Habit Tracking | Planned | Gentle streak dots for recurring task patterns |
| Energy-Aware Ordering | Planned | Circadian task sorting based on user profile |

When adding new features, evaluate them against the core question: **"Does this reduce a specific type of mental overhead without adding visual noise?"** If it requires active attention or creates anxiety, it doesn't belong on the wall.

## Design Philosophy & UX Principles

The UI must be designed thoughtfully and artistically, with a premium feel that communicates quality at every level. This is a top-priority concern — not an afterthought.

### Visual Identity

**The UI communicates:**
- **Calm productivity** — A quiet confidence. The interface should feel like a well-organized desk, not a busy dashboard.
- **Controlled depth** — Subtle elevation, soft shadows, and layering that implies physicality without shouting about it.
- **Slight tactility without being playful** — Surfaces should feel real and considered. Think matte finishes, not glossy bouncing buttons.
- **Enterprise-ready but human** — Professional enough for an office wall, warm enough for a kitchen.

**The UI is NOT:**
- Experimental or trendy
- Highly animated or attention-seeking
- Visually loud or high-contrast for its own sake
- Gamified (no badges, streaks, rewards, or dopamine hooks)

### Motion & Transitions
- Transitions should be **fluid and elegant**, not flashy. Every animation must have purpose — guiding attention or confirming an action.
- Prefer subtle easing curves (ease-in-out, decelerate) over springy/bouncy physics.
- Duration sweet spot: 200-350ms for most transitions. Nothing should feel sluggish or snappy.
- State changes (task completion, list switching, sync) should feel like smooth, inevitable progressions — not events.

### Typography & Spacing
- Generous whitespace. Let content breathe.
- Typographic hierarchy should do the heavy lifting — not color or iconography.
- Favor weight and size contrast over color contrast for establishing hierarchy.

### Color & Surface
- Muted, sophisticated palette. No saturated primaries competing for attention.
- Urgency indicators should be understated — a quiet warmth for overdue, not an alarm.
- Dark and light themes should both feel intentional and complete, not one derived from the other.

### Interaction Design
- Since this is primarily a display device, any interactive moments must feel deliberate and premium.
- Touch targets should be generous (wall-mounted = less precision).
- Feedback should be immediate but restrained — a gentle state shift, not a celebration.

### Rotary Encoder Input (Physical Hardware)
The device has a **rotary encoder with click button**, connected via Arduino as a Bluetooth HID keyboard. This is the primary physical interaction method — it makes the wall device tactile and satisfying to use without a touchscreen.

**How it maps:**
- **Rotate** = scroll/navigate through tasks and lists
- **Click** = select, confirm, or toggle task completion

**Design implications:**
- The UI must have a clear, visible **focus/selection state** — the user needs to always know which item the encoder is pointing at.
- Focus movement should feel smooth and connected to the physical rotation. One detent = one item. No drift, no ambiguity.
- All core interactions (scroll tasks, switch lists, complete a task) must be fully operable via sequential focus navigation + click. Touch is secondary.

### Task Completion Feel
Marking a task as done should feel like a **mini reward** — a small, satisfying moment that makes you want to do it again. Think Apple's haptic feedback: solid, premium, precise.

**What this means:**
- **Haptic feedback**: Trigger the tablet's vibrator with a short, crisp pulse on completion — not a buzz, a *thunk*. Use Android's `HapticFeedbackConstants` or a tuned `VibrationEffect` for that tight, mechanical feel.
- **Visual animation**: A subtle, confident state change — the task could gently contract, its text could soften and shift, the surface could settle into a "done" state with slight opacity and position change. It should feel like something clicking into place, not disappearing.
- **What to avoid**: Green checkmark animations, confetti, particle effects, celebratory sounds, or anything that feels like a mobile game reward. The satisfaction comes from the physical click of the encoder + the crisp haptic + the quiet visual acknowledgment. Understated and real.
- **Timing**: The haptic, visual, and encoder click should all land together — simultaneity is what makes it feel premium. Any delay between the physical click and the response breaks the illusion.

## Voice Input (Implemented)

Voice capture is fully implemented: SpeechRecognizer on-device STT → Gemini AI parses natural language (due dates, target list, parent task) → draft card preview → encoder click to confirm. Trigger: header voice button (encoder: navigate to it, click). Flow: full-screen dim + waveform visualizer → draft card → confirm/cancel. See Voice & AI Capture Pipeline in Architecture for technical details.

## Design Decisions (Binding Specs)

These are finalized design decisions. All implementation must follow these specs exactly.

### 1. Task Completion Behavior
When a task is marked as done, it **drifts to the bottom** of the list. The completed task gently animates downward into a "Completed" section, and the remaining pending tasks close the gap smoothly. The wall reorganizes itself — it's alive and maintaining structure for you. Use ease-in-out curves, ~300ms for the reorder animation.

### 2. Encoder Focus State: Gentle Glow
The selected/focused task card has a **soft, warm edge glow** — as if light is coming from behind the card. Everything else stays normal. The glow must be visible and unambiguous from 6+ feet away. This is not a border highlight — it's a subtle luminous halo effect. Implemented via a soft outer shadow/glow in the accent color with low opacity and moderate blur radius.

### 3. Task Hierarchy: Indented Children with Connecting Line
Parent tasks display normally. Their subtasks are **indented beneath with a subtle vertical connecting line** on the left. The full hierarchy is always visible — never collapsed or hidden. This is critical to the core purpose: if subtasks are hidden behind a tap, they stay in the user's head. The wall must hold the whole map.

### 4. Multiple Lists: Accordion Folders
All task lists are visible in a single scrollable view, displayed as **collapsible folder sections**:
- **Collapsed state**: The list title is shown, with the **first task visible as faded, truncated text** (ellipsis trailing off) — a peek at what's inside.
- **Expanded state**: When the encoder focus lands on a list folder, it **blooms open** to reveal all child tasks at full opacity. Moving focus away causes it to settle back to collapsed.
- **Ordering**: Lists are sorted by urgency of their children — the list containing the nearest due date floats to the top. For lists with no due dates (the common case), fall back to most-recently-updated or the order from Google Tasks API.
- **Transition**: Expand/collapse should animate smoothly (~300ms ease-in-out). Tasks inside should fade in/out rather than just appearing.

### 5. Voice Input: Full-Screen Dim + Waveform Visualizer
When voice input is active:
- The **entire task list dims** (similar to ambient mode dimming).
- A **waveform audio visualizer** appears centered on screen, responding to the microphone input in real-time.
- The waveform style must fit the app's aesthetic — thin lines, muted accent color, smooth motion. Not a flashy equalizer.
- **No live transcription text** appears during listening. The wall is just listening quietly.
- When speech ends, the waveform fades and the wall returns to normal, then presents the confirmation card (see #6).

### 6. Voice Confirmation: Preview Draft Card
After voice input processes:
- A **draft task card** appears at the top of the current list, visually distinct from committed tasks (e.g., slightly different surface, subtle "draft" indicator).
- The transcribed text is displayed in the card.
- **Encoder click** = confirm and commit the task (syncs to Google Tasks, card settles into place as a real task).
- **Encoder twist** = navigate to edit or cancel options.
- The card waits for explicit user action — no auto-commit, no timeout.

### 7. Clock Header: Minimal Corner Utility
The clock/date display should be **small and tucked into a corner** — not a large header consuming vertical space. Tasks are the star of the wall. The time is a utility glance, not a centerpiece. Think: small `bodyMedium` or `labelLarge` text in a corner with the date beneath it, total height under ~48dp.

### 8. Urgency: Warm Temperature Shift
Overdue tasks use a **muted warm amber/terracotta tone** — not red, not alarming. It's a gentle temperature shift that the eye catches without triggering stress. Due-today tasks can use a slightly warmer version of the neutral palette. Due-soon and normal tasks remain in the standard cool/neutral palette. This requires adding a warm accent color to the theme (e.g., `Color(0xFFB8866B)` or similar muted amber) alongside the existing slate blue.

### 9. Ambient Mode: Two-Tier System
**Tier 1 — Quiet Mode** (triggered after 30 seconds of no interaction):
- The task list simplifies to show only the **next 2-3 upcoming/urgent tasks** as faint text.
- Everything else fades away. The wall is whispering just the most important things.
- Low brightness, but still readable if you walk up.

**Tier 2 — Sleep Mode** (triggered by schedule or ambient light):
- Screen goes **fully dark** (true black, OLED-friendly).
- Triggered by either:
  - A configurable **sleep schedule** (e.g., 11pm–7am), or
  - **Ambient light sensing** via the device camera — periodically check brightness and enter sleep when the room is dark.
- The encoder instantly wakes the device from either tier.
- This is essential for bedroom installations where light pollution matters.

## Build and Development Commands

### Building the App
```bash
# Build debug APK
gradlew assembleDebug

# Build release APK
gradlew assembleRelease

# Install debug build on connected device
gradlew installDebug
```

### Running Tests
```bash
# Run unit tests
gradlew test

# Run instrumented tests on connected device
gradlew connectedAndroidTest

# Run specific unit test
gradlew test --tests com.example.todowallapp.ExampleUnitTest

# Run specific instrumented test
gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.todowallapp.ExampleInstrumentedTest
```

### Code Quality
```bash
# Check for lint issues
gradlew lint

# Clean build artifacts
gradlew clean
```

## Architecture

### MVVM Pattern
The app follows MVVM (Model-View-ViewModel) architecture with unidirectional data flow:
- **Model**: Data classes in `data/model/` (Task, TaskList, DayPlan) and repository pattern in `data/repository/`
- **ViewModel**: `TaskWallViewModel` (wall mode) and `PhoneCaptureViewModel` (phone mode) manage UI state
- **View**: Jetpack Compose screens in `ui/screens/` and components in `ui/components/`

### App Modes (WALL vs PHONE)
The app supports two distinct modes selected at first launch via `ModeSelectorScreen`:
- **WALL mode**: Immersive kiosk display with encoder navigation (`TaskWallScreen`)
- **PHONE mode**: Touch-first UI with capture bar and bottom sheets (`PhoneHomeScreen`)

Mode is persisted via `ModePreferenceRepository`. `AppMode.kt` defines the enum. `MainActivity` routes to the correct root screen.

### Key Architectural Components

#### Authentication Flow
1. `GoogleAuthManager` (auth/GoogleAuthManager.kt) handles Google Sign-In with Tasks API scope
2. Auth state is managed through sealed class `AuthState` (Loading, NotAuthenticated, Authenticated, Error)
3. Silent sign-in attempted on app start with 3-second timeout
4. Activity result launcher pattern used for interactive sign-in

#### Data Flow
1. **Repository Layer**: `GoogleTasksRepository` wraps Google Tasks API client
   - Initialized with `GoogleSignInAccount` after authentication
   - All API calls executed on `Dispatchers.IO`
   - Returns `Result<T>` for error handling
2. **ViewModel Layer**: `TaskWallViewModel` exposes `StateFlow<TaskWallUiState>`
   - Optimistic updates for task completion/uncompletion with revert on failure
   - Auto-sync every 5 minutes (configurable via `syncIntervalMs`)
   - Selected task list persisted via DataStore preferences
3. **UI Layer**: Composables collect state via `collectAsState()`

#### State Management
- Single source of truth: `TaskWallUiState` in ViewModel
- Persisted state: Selected task list ID stored in DataStore (preferences)
- Task sorting: Incomplete tasks first, then by position (Google Tasks ordering)

### Kiosk Mode Implementation
The app runs in immersive fullscreen mode configured in `MainActivity`:
- System UI hidden via `WindowInsetsControllerCompat`
- Deprecated flags used for broader device compatibility (SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
- Screen kept on via `FLAG_KEEP_SCREEN_ON`
- System UI re-hidden on `onResume()` and `onWindowFocusChanged()`
- Manifest configured with HOME category for potential launcher replacement

### Google Tasks Integration
- OAuth2 authentication with `TasksScopes.TASKS` scope
- Parses RFC 3339 timestamps for `updated` and `completed` fields
- Due dates are date-only (no time component)
- Task urgency calculated based on due date: OVERDUE, DUE_TODAY, DUE_SOON, NORMAL, COMPLETED
- Deleted tasks filtered out in `getTasks()`
- Hard cap of 100 tasks per list (no pagination — known limitation)

### Google Calendar Integration
- `GoogleCalendarRepository` wraps Google Calendar API
- Fetches events for date ranges, groups by date client-side
- Supports event creation (task-to-event promotion) and deletion
- Calendar scope requires separate consent (403 handling with re-consent prompt)
- Three view modes: MONTH (default), WEEK (7 days), DAY

### Voice & AI Capture Pipeline
- `VoiceCaptureManager` wraps Android `SpeechRecognizer` — always-continuous, no `continuous` parameter on `startListening()`
- Use `cancel()` for full teardown — `resetToIdle()` only flips state flag, does NOT stop recognizer or remove handler callbacks
- `VoiceParsingCoordinator` orchestrates voice → Gemini parse → route → commit. Call `configureDayPlanningContext()` after `loadSettings()` (use `refreshDayPlanningContext()` helper to avoid stale sleep hours)
- `GeminiCaptureRepository` sends transcriptions to Gemini. `VoiceIntent` enum: ADD/COMPLETE/RESCHEDULE/DELETE/QUERY/AMEND/DAY_PLAN
- `VoiceIntentRouter.kt` deleted (2026-04-12) — Gemini classifies all intents including DAY_PLAN
- `ListRouting` assigns parsed tasks to the correct Google Tasks list
- `CaptureCommitOrchestrator` handles the Tasks API write after user confirmation
- Falls back to raw transcription if Gemini parsing fails
- Voice input triggered via header voice button (encoder: navigate to button, click)
- Double-click on a focused task opens context menu (configurable window: DOUBLE_CLICK_WINDOW_MS)
- Voice overlay (waveform + dim), draft card preview, and confirm/cancel flow are fully implemented

### Day Organizer
- `DayOrganizerCoordinator` manages a 6-state planning state machine: Idle → Processing → PlanReady → Confirming → PartialSuccess / Error
- Entry points: `generatePlan(transcription, scope, ...)` and `adjustPlan(adjustmentText)` — coordinator receives text, does NOT own voice capture
- Constructor: `DayOrganizerCoordinator(geminiCaptureRepository, geminiKeyStore, calendarRepository, tasksRepository)` — no VoiceCaptureManager
- `DayPlan` model: list of `PlanBlock`s with 12 categories, per-block confidence + flexibility, `EnergyProfile` setting
- `DayOrganizerOverlay` renders the conversation UI and per-block encoder-navigable plan preview
- Accepted plans write events to Google Calendar via `GoogleCalendarRepository`
- Plan acceptance has 8-second undo (`PlanUndoState`), and sets `isDayOrganized` badge on ClockHeader
- Ghost blocks render in `CalendarDayView` during plan preview before acceptance

## Project Structure

```
app/src/main/java/com/example/todowallapp/
├── MainActivity.kt              # Entry point, kiosk mode, page navigation (Tasks/Calendar)
├── auth/
│   └── GoogleAuthManager.kt     # Google Sign-In and auth state
├── capture/
│   ├── DayOrganizerCoordinator.kt  # Gemini day planning state machine (8 states)
│   ├── VoiceParsingCoordinator.kt  # Orchestrates voice → parse → route pipeline
│   ├── model/
│   │   └── ParsedCapture.kt        # Structured result from Gemini voice parsing
│   ├── repository/
│   │   ├── GeminiCaptureRepository.kt  # Gemini AI voice parsing (NL → structured task)
│   │   ├── GeminiJsonParser.kt         # JSON extraction from Gemini responses
│   │   ├── CaptureCommitOrchestrator.kt # Commits parsed captures to Tasks API
│   │   ├── PendingCaptureStore.kt      # Holds draft captures awaiting confirmation
│   │   ├── ListRouting.kt              # Assigns captured tasks to correct list
│   │   └── ScannerRepository.kt        # QR/barcode scanning support
├── data/
│   ├── model/
│   │   ├── Task.kt             # Data models (Task, TaskList, TaskUrgency)
│   │   ├── CalendarEvent.kt    # Calendar event model
│   │   ├── CalendarViewMode.kt # MONTH / WEEK / DAY enum
│   │   ├── AppMode.kt          # WALL / PHONE mode enum
│   │   ├── DayPlan.kt          # Day Organizer plan model (PlanBlock, 12 categories)
│   │   ├── TaskListWithTasks.kt # Joined model used in Day Organizer context
│   │   ├── TaskMetadata.kt     # Extended task metadata (energy, recurrence)
│   │   └── WeatherCondition.kt # Weather data model
│   └── repository/
│       ├── GoogleTasksRepository.kt    # Google Tasks API wrapper
│       ├── GoogleCalendarRepository.kt # Google Calendar API wrapper
│       ├── WeatherRepository.kt        # Open-Meteo weather data fetching
│       ├── AppPreferences.kt           # DataStore keys and defaults
│       ├── ModePreferenceRepository.kt # Persists WALL/PHONE mode selection
│       └── GoogleApiTransportFactory.kt # HTTP transport for Google API clients
├── security/
│   ├── GeminiKeyStore.kt       # Encrypted storage for Gemini API key
│   └── WeatherKeyStore.kt      # Encrypted storage for weather API key
├── viewmodel/
│   ├── TaskWallViewModel.kt    # UI state, sync, voice, calendar, Day Organizer settings (~2461 lines)
│   └── PhoneCaptureViewModel.kt # Phone-mode capture/task state
├── voice/
│   └── VoiceCaptureManager.kt  # Android SpeechRecognizer wrapper
├── util/
│   ├── ConnectivityMonitor.kt  # Network state monitoring
│   └── ...
└── ui/
    ├── components/
    │   ├── TaskItem.kt             # Task card with completion animation, urgency
    │   ├── ClockHeader.kt          # Time/date/sync status bar
    │   ├── SettingsPanel.kt        # Settings overlay (encoder-navigable)
    │   ├── CalendarMonthView.kt    # Month grid with event dots
    │   ├── CalendarWeekView.kt     # 7-day row view with event chips
    │   ├── CalendarDayView.kt      # Hour-slot day view with ghost plan blocks
    │   ├── Calendar3DayView.kt     # 3-day column view
    │   ├── DayOrganizerOverlay.kt  # Day Organizer conversation/plan preview UI
    │   ├── WeatherContextStrip.kt  # Compact weather row for calendar day view
    │   ├── WeekStrip.kt            # Horizontal week date selector
    │   ├── SharedTaskPrimitives.kt # Checkbox, due date badge, animations
    │   ├── WaveformVisualizer.kt   # Voice input pulse animation
    │   ├── UndoToast.kt            # Undo completion toast
    │   ├── ViewSwitcherPill.kt     # Wall/Phone mode switcher pill
    │   ├── TaskContextMenu.kt      # Double-click context menu (complete/edit/promote)
    │   ├── TaskDetailOverlay.kt    # Full task detail/edit overlay
    │   ├── SearchFilterOverlay.kt  # Task search and filter UI
    │   ├── PromotionSheet.kt       # Task-to-calendar-event promotion flow
    │   ├── TaskPickerOverlay.kt    # Task selection picker (for Day Organizer)
    │   ├── RecurrencePickerOverlay.kt # Recurrence rule editor
    │   ├── EventActionMenu.kt      # Calendar event action menu
    │   ├── NextActionSpotlight.kt  # Highlights the next suggested action
    │   ├── PageIndicator.kt        # Page position dots
    │   ├── PhoneTaskItem.kt        # Phone-mode task row
    │   ├── PhoneCaptureBar.kt      # Phone-mode voice/text capture bar
    │   ├── PhoneVoiceBottomSheet.kt # Phone-mode voice input sheet
    │   ├── PhoneSettingsSheet.kt   # Phone-mode settings sheet
    │   └── PhoneAccordionSection.kt # Phone-mode accordion task list section
    ├── screens/
    │   ├── TaskWallScreen.kt   # Main wall display (~2319 lines), focus/nav, ambient
    │   ├── CalendarScreen.kt   # Calendar view with month/week/day modes
    │   ├── PhoneHomeScreen.kt  # Phone-mode home (tasks + capture)
    │   ├── ModeSelectorScreen.kt # WALL/PHONE mode selection on first launch
    │   ├── ParsedCapturePreviewScreen.kt # Preview captured task before commit
    │   └── SignInScreen.kt     # Google Sign-In screen
    ├── theme/                  # Material3 theme, WallColors, typography
    └── utils/
        ├── Haptics.kt          # Haptic feedback abstraction
        └── LayoutDimensions.kt # Orientation-adaptive spacing
```

## Key Configuration

- **minSdk**: 23 (Android 6.0)
- **targetSdk**: 35 (Android 15)
- **compileSdk**: 35
- **JVM Target**: 11
- **Compose**: Enabled with Kotlin Compose Compiler plugin
- **Package exclusions**: META-INF/DEPENDENCIES (for Google API client compatibility)

## Dependencies

### Core Google Integration
- `play-services-auth:21.0.0` - Google Sign-In
- `google-api-client-android:2.2.0` - Google API client
- `google-api-services-tasks:v1-rev20240312-2.0.0` - Tasks API
- `google-api-services-calendar:v3-*` - Calendar API

### AI Integration
- Gemini API (via `generativeai` SDK) - Voice input parsing, future day planning

### State Management
- `lifecycle-viewmodel-compose:2.7.0` - ViewModel integration
- `datastore-preferences:1.0.0` - Persistent preferences
- `kotlinx-coroutines-android:1.7.3` - Async operations
- `kotlinx-coroutines-play-services:1.7.3` - Coroutine support for Play Services

## Using Codex CLI for Coding Tasks

For implementation tasks, you can delegate to Codex (OpenAI's code generation model) to handle coding work non-interactively.

### Command Syntax

```bash
# Read-only mode (analysis/research)
codex exec --skip-git-repo-check "Analyze the structure of TaskWallViewModel.kt"

# Write access for implementation - NOTE: -s flag MUST come before --skip-git-repo-check
codex exec -s danger-full-access --skip-git-repo-check "Add a new function to TaskWallViewModel.kt that clears all completed tasks"
```

### Sandbox Modes

- `read-only` (default) - Can only read files, run safe commands
- `workspace-write` - Can write to project directory only
- `danger-full-access` - Full system access (use for implementation tasks that modify files)

### When to Use Codex

- Implementing new features or functions based on architectural directives
- Adding new Compose UI components
- Writing unit tests or instrumented tests
- Refactoring existing Kotlin code
- Making repetitive code changes across multiple files
- Implementing data classes or repository methods

### Android-Specific Examples

```bash
# Add a new Composable function
codex exec -s danger-full-access --skip-git-repo-check "Add a new Composable function to TaskItem.kt called TaskPriorityBadge that displays a colored badge based on TaskUrgency"

# Implement a repository method
codex exec -s danger-full-access --skip-git-repo-check "Add a deleteTask(taskListId: String, taskId: String) method to GoogleTasksRepository.kt following the same pattern as completeTask"

# Add unit tests
codex exec -s danger-full-access --skip-git-repo-check "Create unit tests for the Task.getUrgencyLevel() function in TaskTest.kt covering all urgency levels"
```

### Shell Quoting — Use Stdin for Complex Prompts

When the prompt contains Kotlin string literals, `${}`, or backticks, `"$(cat file)"` fails with shell interpretation errors. Use stdin redirect instead:

```bash
codex exec -s danger-full-access --skip-git-repo-check < /tmp/prompt.txt
```

Codex works best for **self-contained new file creation** or **well-scoped single functions**. Avoid delegating complex multi-section edits to a single existing file — implement those directly instead.

### Important Notes

- Uses `gpt-5.1-codex-mini` model
- **Argument order matters**: `-s` must come before `--skip-git-repo-check`
- Always review Codex output before approving changes
- Include specific file paths and detailed requirements in directives
- For Kotlin code, specify coding conventions (e.g., "use const arrow function style" becomes "use immutable val and lambda syntax")

## Using Gemini CLI for Large Code Comprehension

When analyzing large portions of the codebase or multiple files that might exceed Claude's context, use the Gemini CLI with its large context window for code research and analysis tasks.

### Recommended Model Configuration

**For code analysis and research (RECOMMENDED):**
```bash
gemini -m gemini-2.5-flash -p "Your query here"
```

**Available models:**
- `gemini-2.5-flash` - **RECOMMENDED** - Best balance of speed and reasoning quality
- `gemini-2.5-flash-lite` - Faster, higher rate limits (500 RPD vs 20), use for simple lookups

### Syntax

Use `@` syntax to include files/directories (paths relative to project root):

```bash
# Analyze a single file
gemini -m gemini-2.5-flash -p "@app/src/main/java/com/example/todowallapp/MainActivity.kt Explain the kiosk mode implementation"

# Analyze multiple files
gemini -m gemini-2.5-flash -p "@app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt @app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt How does the ViewModel interact with the Repository?"

# Analyze entire directory
gemini -m gemini-2.5-flash -p "@app/src/main/java/com/example/todowallapp/ui/ Summarize all UI components and their purposes"

# Analyze whole project
gemini -m gemini-2.5-flash --all_files -p "What are the key architectural patterns used in this Android app?"
```

### When to Use Gemini

- Analyzing entire feature modules or large files
- Understanding multi-file interactions (e.g., data flow across ViewModel → Repository → API)
- Reviewing Compose UI component hierarchy
- Verifying if specific Android patterns are implemented correctly
- Code review tasks requiring broad codebase context
- Understanding Google Tasks API integration across multiple layers

### Best Pattern for UI Feature Analysis

Bundle all related files in a single Gemini call rather than reading iteratively. Example for calendar work:

```bash
gemini -m gemini-2.5-flash -p "@app/.../CalendarScreen.kt @app/.../CalendarDayView.kt @app/.../CalendarEvent.kt @app/.../GoogleCalendarRepository.kt @app/.../TaskWallViewModel.kt Analyze..."
```

Loading 5 files in one shot gives a complete cross-layer picture faster than sequential LSP symbol reads.

### Important Notes

- **Always use `-m gemini-2.5-flash`** for optimal speed/quality balance
- Paths in `@` syntax are relative to current working directory
- Particularly useful for understanding Kotlin coroutine flows and state management patterns
- Can analyze manifest files, Gradle configs, and multiple Kotlin files simultaneously

## Development Notes

### Testing with Google Tasks API
- Requires valid Google account with Google Tasks data
- OAuth consent screen must be configured in Google Cloud Console
- SHA-1 certificate fingerprint must be registered for debug/release keystores

### Manifest Special Configuration
The app includes HOME category intent filter, allowing it to act as a launcher replacement for dedicated kiosk devices. This is intentional for wall-mounted installations but may trigger launcher selection dialogs during development.

### Time Handling
All timestamp parsing uses system timezone via `ZoneId.systemDefault()`. Due dates from Google Tasks API are stored as `LocalDate` (date-only), while completion and update times use `LocalDateTime`.

### WallColors Token Reference
The warm accent for promoted tasks/urgency is `colors.accentWarm` — there is no `urgencyWarm` property. Full urgency tokens: `urgencyOverdue`, `urgencyDueToday`, `urgencyDueSoon`, `urgencyOverdueSubtle`.

### CalendarScreen Call Site
`CalendarScreen(...)` is called from `MainActivity.kt`, not from `TaskWallScreen.kt`.

### Code Navigation (Kotlin LSP)
`kotlin-lsp@claude-plugins-official` is active — transparent LSP, no explicit tool calls needed.
Use `Grep` with `glob: "**/*.kt"` scoped to `app/src` for symbol searches; `Glob` for file discovery.
See `.claude/rules/kotlin-lsp.md` for the full decision guide.

### Hook Configuration Gotcha
PreToolUse hooks fire synchronously and spawn a shell process. Use the narrowest matcher
possible — `"Bash(git commit*)"` not `"Bash"`. A broad matcher on Bash adds overhead to
every shell call in the session. Hooks belong in `settings.local.json` (gitignored), not
`settings.json` (tracked).
