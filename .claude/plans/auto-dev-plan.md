# Auto-Dev Plan: Branch 2 — Context Enrichment

**Created:** 2026-04-02
**Spec:** `docs/superpowers/specs/2026-04-02-context-enrichment-design.md`

## Task Dependency Map

```
T1: Add preferredTime to Task + GoogleTasksRepository
T2: Enrich ExistingTaskRef data class (depends: T1)
T3: Enrich buildDayPlanGeminiPrompt signature + body (depends: T2)
T4: Update DayOrganizerCoordinator to pass new params (depends: T3)
T5: Update TaskWallViewModel to provide all new data (depends: T4)
T6: Add connectivity guard to voice/day-organizer entry points (independent)
```

## Parallel Groups

- **Group A (T1 → T2 → T3 → T4 → T5):** Sequential chain — each task modifies a different file but depends on the prior's type changes.
- **Group B (T6):** Independent — only touches ViewModel, no type dependencies.

Since Group A is a chain and T6 is small, execute everything sequentially.

## Execution Order

### T1: Add `preferredTime` to Task model [Task.kt, GoogleTasksRepository.kt]
- [ ] Add `val preferredTime: String? = null` to `Task` data class (after `cleanNotes`)
- [ ] In `GoogleTasksRepository.toAppTask()`: add `preferredTime = decoded.preferredTime`
- **Status:** pending

### T2: Enrich `ExistingTaskRef` [GeminiCaptureRepository.kt]
- [ ] Add fields: `dueDate: LocalDate? = null`, `priority: TaskPriority = TaskPriority.NORMAL`, `preferredTime: String? = null`, `recurrenceInfo: String? = null`
- [ ] All existing callers use named args or defaults, so backward-compatible
- **Status:** pending

### T3: Enrich `buildDayPlanGeminiPrompt()` [GeminiCaptureRepository.kt]
- [ ] Add params: `weatherForecast: String? = null`, `wakeHour: Int = 7`, `sleepHour: Int = 23`, `focusedListTitle: String? = null`
- [ ] In systemInstruction:
  - Replace rule 3 energy curve with dynamic wake/sleep hours
  - Add rule 11: weather-aware scheduling
  - Add rule 12: weekend/weekday awareness
- [ ] In userContent:
  - Add WEATHER section (if non-null)
  - Add day-of-week line: "TODAY IS: WEEKDAY (Tuesday)" or "TODAY IS: WEEKEND (Saturday)"
  - Add focused list line (if non-null)
  - Enrich task lines: include dueDate, priority, preferredTime, recurrenceInfo
- [ ] Replace hardcoded "07:00" with `wakeHour`
- **Status:** pending

### T4: Update `DayOrganizerCoordinator` [DayOrganizerCoordinator.kt]
- [ ] Add to `startListening()` params: `weatherProvider: (suspend () -> String?)? = null`, `wakeHour: Int = 7`, `sleepHour: Int = 23`, `focusedListTitle: String? = null`
- [ ] Store as private fields
- [ ] In `handleTranscription()`: call weatherProvider, pass new params to `buildDayPlanGeminiPrompt()`
- **Status:** pending

### T5: Update `TaskWallViewModel` [TaskWallViewModel.kt]
- [ ] In `startDayOrganizer()`:
  - Enrich taskProvider to build `ExistingTaskRef` with dueDate, priority, preferredTime, recurrenceInfo
  - Enrich eventsProvider to include duration and all-day flag in string
  - Add weatherProvider lambda: format today's weather from `_weatherForecast`
  - Pass `sleepEndHour.value` as wakeHour, `sleepStartHour.value` as sleepHour
  - Pass focused list title from `_uiState.value.selectedTaskListTitle`
- **Status:** pending

### T6: Connectivity guard [TaskWallViewModel.kt]
- [ ] In `startVoiceInput()`: early return with error if `!isOnline.value`
- [ ] In `startDayOrganizer()`: early return with transient message if `!isOnline.value`
- **Status:** pending

## Build Verification
- [ ] `gradlew assembleDebug` after T5 (all type changes done)
- [ ] `gradlew assembleDebug` after T6

## Review Checkpoint
- [ ] Code review after all tasks complete
- [ ] Verify prompt output reads correctly (mentally trace a sample scenario)
