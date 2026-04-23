# CLAUDE.md

Guidance for Claude Code working in this repo. Keep this file slim — deep details live in `docs/claude/*.md` and load on-demand (see **Rule Index** below). Files live outside `.claude/` so they are **not** auto-injected into every session.

## Project

ToDoWallApp — Android (Kotlin + Compose) wall-mounted kiosk that holds Google Tasks, Google Calendar, voice capture, and AI-assisted day planning on an always-on display. Goal: **externalize mental load** so the user's mind doesn't have to rehearse tasks or schedules. Operated primarily via a rotary encoder (3 inputs only: rotate CW, rotate CCW, click).

Two app modes selected at first launch (`ModeSelectorScreen`, persisted via `ModePreferenceRepository`):
- **WALL** — immersive kiosk with encoder navigation (`TaskWallScreen`)
- **PHONE** — touch-first UI with capture bar and bottom sheets (`PhoneHomeScreen`)

When evaluating new features, ask: *"Does this reduce a specific kind of mental overhead without adding visual noise?"* If it requires active attention or creates anxiety, it doesn't belong on the wall.

## Build & Test

```bash
gradlew assembleDebug
gradlew test
gradlew test --tests com.example.todowallapp.ExampleUnitTest
gradlew connectedAndroidTest
gradlew lint
gradlew clean
```

**Requirements:** JDK 11 target, compileSdk 35, minSdk 23. Manifest has HOME intent filter (intentional for kiosk launcher use; may show launcher chooser during dev).

## Architecture

MVVM + Compose, single-Activity, single ViewModel per mode:

```
Compose UI (screens + components)
    ↕ StateFlow (collectAsState)
TaskWallViewModel  |  PhoneCaptureViewModel
    ↓
Repositories (GoogleTasksRepository, GoogleCalendarRepository,
              WeatherRepository, ModePreferenceRepository, ...)
    ↓
Google Tasks/Calendar APIs  |  Gemini  |  DataStore  |  Open-Meteo
```

- **Single source of truth**: `TaskWallUiState` (`StateFlow`) in the ViewModel. Separate flows for `VoiceInputState`, `DayOrganizerState` (via coordinator), `UndoState`, `PlanUndoState`, settings (sleep window, sync interval, energy profile), `isOnline`, `recentlyCreatedEventIds`.
- **Auth**: `GoogleAuthManager` with `TasksScopes.TASKS` (+ Calendar scope, separate consent). Sealed `AuthState` (Loading / NotAuthenticated / Authenticated / Error). Silent sign-in on launch with 3s timeout.
- **Repositories** return `Result<T>`, run on `Dispatchers.IO`.
- **Optimistic updates** for task completion/uncompletion with revert on failure. 5s undo on completion, 8s on day-plan acceptance.
- **Sync** auto-runs every 5 min (`syncIntervalMs`); selected list ID persisted in DataStore.
- **Tasks API limits**: hard cap of 100 tasks per list (no pagination — known).
- **Calendar**: three view modes — `MONTH` (default) / `WEEK` (7 days) / `DAY`. Drill: Month → Week → Day. Range fetch groups events client-side.
- **Kiosk mode** (`MainActivity`): immersive via `WindowInsetsControllerCompat`, deprecated `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` for compatibility, `FLAG_KEEP_SCREEN_ON`, re-applied on `onResume`/`onWindowFocusChanged`.

## Inline Gotchas

- **Encoder is 3 inputs only** — rotate CW / CCW / click. There are NO letter keys, no Escape, no hold/long-press. Never propose keyboard shortcuts. See `design-philosophy.md` for the full mapping.
- **WallColors token reference**: warm accent is `WallColors.accentWarm`. There is **no** `urgencyWarm`. Full urgency tokens: `urgencyOverdue`, `urgencyDueToday`, `urgencyDueSoon`, `urgencyOverdueSubtle`.
- **CalendarScreen call site**: `CalendarScreen(...)` is invoked from `MainActivity.kt` (~line 597), NOT from `TaskWallScreen.kt`.
- **Time handling**: timestamps parsed in `ZoneId.systemDefault()`. Due dates → `LocalDate` (date-only). Completed/updated → `LocalDateTime`. Core library desugaring is on for `java.time` on pre-API 26.
- **Hooks belong in `settings.local.json`** (gitignored), NOT `settings.json` (tracked). Use the narrowest matcher possible — `"Bash(git commit*)"` not `"Bash"`. Broad matchers add overhead to every shell call.
- **OAuth setup**: SHA-1 fingerprint must be registered in Google Cloud Console for both debug and release keystores; OAuth consent screen must be configured.

## Code Navigation

`kotlin-lsp@claude-plugins-official` is active — transparent LSP, no explicit tool calls. Use `Grep` with `glob: "**/*.kt"` scoped to `app/src` for symbol searches; `Glob` for file discovery. See `docs/claude/kotlin-lsp.md` for the full decision guide. (Serena has been removed.)

---

## Rule Index — load on-demand via Read

These files are **not** auto-loaded. When a task matches a trigger, Read the file before making changes.

| File | Load when touching… |
|---|---|
| [`docs/claude/design-philosophy.md`](docs/claude/design-philosophy.md) | anything under `ui/`, theming, motion, encoder UX, completion haptic/visual feel |
| [`docs/claude/design-decisions.md`](docs/claude/design-decisions.md) | the 9 binding specs (drift-to-bottom, glow focus, accordion folders, urgency warm, ambient tiers, etc.) |
| [`docs/claude/voice-pipeline.md`](docs/claude/voice-pipeline.md) | `voice/`, `capture/`, `GeminiCaptureRepository`, voice overlay UI |
| [`docs/claude/day-organizer.md`](docs/claude/day-organizer.md) | `DayOrganizerCoordinator`, `DayOrganizerOverlay`, `DayPlan`/`PlanBlock`, ghost blocks, plan undo |
| [`docs/claude/project-structure.md`](docs/claude/project-structure.md) | exploring a new area, deciding where a new file belongs, cross-package wiring |
| [`docs/claude/worktree-git.md`](docs/claude/worktree-git.md) | committing, branching, opening PRs, working in `.claude/worktrees/`, merging/rebasing |
| [`docs/claude/kotlin-lsp.md`](docs/claude/kotlin-lsp.md) | choosing between Grep/Glob for code searches |

**Loading rule:** if unsure whether a task touches a rules file's domain, prefer to Read it. Cost is small; acting on stale or missing invariants (e.g. a binding design spec, the encoder hardware constraint, a Day Organizer wiring gap) regresses behaviour or wastes implementation time.
