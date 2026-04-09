# Context Enrichment for Day Planner Prompt — Design Spec

**Date:** 2026-04-02
**Branch:** context-enrichment
**Status:** Implemented (energy profile, weather, sleep schedule, task metadata all passed to Gemini)
**Implemented:** 2026-04-09
**Scope:** Backend-only changes to enrich the Gemini day planner prompt with richer task/event/environment context.

## Problem

`buildDayPlanGeminiPrompt()` currently receives minimal task info (title + listId only), no weather context, hardcoded wake time and energy curve, bare event strings, no weekend awareness, and no focused-list bias. This limits Gemini's ability to make intelligent scheduling decisions.

## Changes

### 1. Add `preferredTime` to Task model

`Task.kt` already has `recurrenceRule` and `priority` populated from `TaskMetadata.decode()`, but `preferredTime` (parsed from `||PREFERRED:morning||` tags) is discarded. Add it.

**File:** `data/model/Task.kt`
- Add `val preferredTime: String? = null` field

**File:** `data/repository/GoogleTasksRepository.kt`
- In `toAppTask()`: set `preferredTime = decoded.preferredTime`

### 2. Enrich `ExistingTaskRef` with metadata

**File:** `capture/repository/GeminiCaptureRepository.kt`
- Expand `ExistingTaskRef` with: `dueDate: LocalDate?`, `priority: TaskPriority`, `preferredTime: String?`, `recurrenceInfo: String?`

### 3. Enrich task formatting in prompt

**File:** `capture/repository/GeminiCaptureRepository.kt` — `buildDayPlanGeminiPrompt()`
- Change task line from `"- ${it.title} (list: ${it.listId})"` to include due date, priority, preferred time, and recurrence. Example: `"- Buy groceries (list: abc123, due: 2026-04-02, priority: HIGH, preferred: morning, recurs: Every week)"`

### 4. Add weather context to prompt

**Approach:** Add `weatherForecast: String?` parameter to `buildDayPlanGeminiPrompt()`. The ViewModel formats the weather string before passing it.

**File:** `capture/repository/GeminiCaptureRepository.kt`
- New param: `weatherForecast: String? = null`
- Add weather section to userContent if non-null
- Add scheduling rule to systemInstruction: "If weather is bad (RAIN/SNOW/STORM), prefer indoor tasks; schedule outdoor errands on clear days if possible."

**File:** `DayOrganizerCoordinator.kt`
- Accept `weatherProvider: (suspend () -> String?)?` in `startListening()`
- Call it in `handleTranscription()` and pass result to prompt builder

**File:** `viewmodel/TaskWallViewModel.kt`
- In `startDayOrganizer()`, provide a weatherProvider that reads today's forecast from `_weatherForecast`

### 5. Replace hardcoded wake time

**File:** `capture/repository/GeminiCaptureRepository.kt`
- New param: `wakeHour: Int = 7`
- Replace `"Start scheduling from 07:00 (morning)."` with `"Start scheduling from ${String.format("%02d:00", wakeHour)} (morning)."`

**File:** `DayOrganizerCoordinator.kt` / `TaskWallViewModel.kt`
- Pass `sleepEndHour` value through the chain

### 6. Replace hardcoded energy curve

**File:** `capture/repository/GeminiCaptureRepository.kt`
- New param: `availableHoursDescription: String? = null`
- Replace rule 3 with dynamic version using wake/sleep hours. E.g.: "Energy curve: ACTIVE hours are {wakeHour}:00–12:00, PASSIVE/LEISURE after 17:00. Available hours: {wakeHour}:00–{sleepStartHour}:00."

**File:** `viewmodel/TaskWallViewModel.kt`
- Build the description string from `sleepStartHour` and `sleepEndHour`

### 7. Enrich event context strings

**File:** `viewmodel/TaskWallViewModel.kt` — `startDayOrganizer()` eventsProvider
- Current: `"$timeRange ${event.title}"`
- New: `"$timeRange ${event.title} (${durationMin}min${if (event.isAllDay) ", all-day" else ""})"`
- For all-day events, show "All day" and "(all-day)" rather than computing duration

### 8. Check connectivity before voice capture

**File:** `viewmodel/TaskWallViewModel.kt`
- In `startVoiceInput()`: if `!isOnline.value`, set error state and return
- In `startDayOrganizer()`: same guard

### 9. Weekend/weekday awareness

**File:** `capture/repository/GeminiCaptureRepository.kt`
- Detect `targetDate.dayOfWeek` and add to userContent: "Today is a WEEKEND (Saturday)" or "Today is a WEEKDAY (Tuesday)"
- Add prompt rule: "On weekends, be more relaxed about scheduling — larger blocks of leisure are normal. On weekdays, assume work obligations unless the user says otherwise."

### 10. Focused task list bias

**File:** `capture/repository/GeminiCaptureRepository.kt`
- New param: `focusedListId: String? = null`
- Add to userContent: "USER'S FOCUSED LIST: {listTitle} — prioritize tasks from this list when scheduling."

**File:** `DayOrganizerCoordinator.kt` / `TaskWallViewModel.kt`
- Pass `selectedTaskListId` and its title through

## Assumptions

1. **No new network calls** — weather data is already fetched and cached by WeatherRepository; we just read the cached forecast.
2. **`preferredTime` on Task** — adding this field is safe because Task is a data class with default `null`. No migration needed.
3. **ExistingTaskRef changes** — all callers (ViewModel taskProvider lambdas) will be updated. There are 3 call sites: `startDayOrganizer()`, `init{}` voiceParsingCoordinator, and `restoreVoicePipelineCallback()`. Only `startDayOrganizer()` uses the day plan prompt; the other two use voice parsing which only needs id/title/listId, so we keep them as-is or provide defaults.
4. **Connectivity check** — voice input needs network for both SpeechRecognizer (optional) and Gemini API (required). Checking upfront provides a better UX than failing mid-flow.
5. **Weekend rule** — kept soft ("be more relaxed") rather than hard to avoid limiting users who work weekends.
