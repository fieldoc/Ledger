# Design: Gemini Search Grounding (Branch 9)

**Date:** 2026-04-08  
**Scope:** Backend only — no new screens, no encoder UX changes  
**Status:** Experimental / opt-in

---

## Overview

Adds opt-in Gemini [googleSearch grounding](https://ai.google.dev/gemini-api/docs/grounding) to the day planning pipeline. When enabled, Gemini autonomously queries the web for weather, business hours, and local events before building the day plan. This enriches the planning context beyond what the app already knows (task lists, calendar events, stored weather forecast).

---

## Critical API Constraint

Gemini's REST API does **not** allow `responseSchema` (structured JSON output) and `tools: [{ googleSearch: {} }]` in the same request. These features are mutually exclusive.

The day plan call uses `responseSchema = DAY_PLAN_SCHEMA`. Therefore, grounding requires a **two-call architecture**:

```
Call 1 (Grounding)  →  plain-text context summary  →  injected into prompt
Call 2 (Day Plan)   →  structured JSON with DAY_PLAN_SCHEMA
```

This is the only viable approach without abandoning structured output.

---

## WeatherRepository Evaluation

**Decision: WeatherRepository is NOT deprecated.**

Reasons:
- Provides `Map<LocalDate, WeatherCondition>` — structured, date-keyed forecast data used by calendar views (weather icons, tints on CalendarMonthView/WeekView/DayView).
- Covers 7 days; grounding context is text-only and covers only today (appropriate for day planning, not calendar display).
- Grounding and WeatherRepository serve different consumers with different data shapes.

Both coexist. When grounding is enabled, the day planner gets richer real-time text context. Calendar views continue to use the structured forecast from WeatherRepository (unchanged).

---

## Architecture

### New component: `fetchGroundingContext()`

Added to `GeminiCaptureRepository`:

```kotlin
suspend fun fetchGroundingContext(
    apiKey: String,
    location: String?,      // from WeatherKeyStore.getLocation(), nullable
    date: LocalDate,
    latencyCallback: ((Long) -> Unit)? = null
): String?
```

Builds a plain-text grounding prompt asking Gemini to search for:
1. Today's weather (if location provided)
2. Local events or noteworthy happenings
3. National holidays / special days
4. General context about the day

Returns the plain-text response string, or `null` on failure (network error, API error, disabled). Failures are silent — day planning falls back to context-free mode.

### `buildGroundingRequestBody()`

Private helper (parallel to `buildRequestBody()`):

```kotlin
private fun buildGroundingRequestBody(prompt: GeminiPrompt): JsonObject
```

Same structure as `buildRequestBody()` but:
- Adds `"tools": [{ "googleSearch": {} }]`
- Does NOT include `responseSchema` in generationConfig (only temperature)
- Uses the standard `generateContent` endpoint

### Prompt enrichment: `buildDayPlanGeminiPrompt()`

Add `groundingContext: String? = null` parameter. When non-null, inject a section into `userContent`:

```
REAL-TIME WEB CONTEXT (fetched by search grounding):
<groundingContext>
```

Gemini will use this alongside the tasks and calendar events to make weather-aware, event-aware scheduling decisions.

### Latency monitoring

Add to `doRequest()`: record wall-clock time before/after the HTTP call. Log to LogCat with structured tags:
- `GeminiGrounding` tag for context-fetch calls  
- `GeminiDayPlan` tag for plan generation calls

Expose via optional callback field `var latencyCallback: ((String, Long) -> Unit)? = null` on `GeminiCaptureRepository`, where the first arg is the call type (`"grounding"`, `"dayplan"`, etc.) and second is milliseconds.

The ViewModel wires up the callback and stores the last grounding latency in `_lastGroundingLatencyMs: MutableStateFlow<Long?>(null)` (exposed as `StateFlow<Long?>`). This is surfaced in the settings panel display label ("Last: 2.3s") but requires no new UI composable.

### User setting: Gemini Grounding toggle

Added to ViewModel:

```kotlin
private val geminiGroundingEnabledKey = booleanPreferencesKey("gemini_grounding_enabled")
private val _geminiGroundingEnabled = MutableStateFlow(false)
val geminiGroundingEnabled: StateFlow<Boolean> = _geminiGroundingEnabled.asStateFlow()

fun setGeminiGroundingEnabled(enabled: Boolean) { /* DataStore persist */ }
```

Default: `false` (opt-in, experimental).

SettingsPanel adds one new row: **"Search Grounding"** boolean toggle, labeled "Experimental" in the description. Follows exact same pattern as the existing toggle rows (encoder-navigable, click to cycle on/off).

---

## Data Flow

```
startDayOrganizer()
  │
  ├─ if (geminiGroundingEnabled && isOnline)
  │     val context = geminiCaptureRepository.fetchGroundingContext(
  │         apiKey, location, LocalDate.now(), latencyCallback = { ms ->
  │             _lastGroundingLatencyMs.value = ms
  │         }
  │     )
  │
  └─ DayOrganizerCoordinator.startListening(
         groundingContextProvider = { context }
     )
         │
         └─ buildDayPlanGeminiPrompt(..., groundingContext = context)
                 │
                 └─ callGeminiForDayPlan() [Call 2, with schema]
```

---

## Affected Files

| File | Change |
|------|--------|
| `capture/repository/GeminiCaptureRepository.kt` | Add `fetchGroundingContext()`, `buildGroundingRequestBody()`, `groundingContext` param to `buildDayPlanGeminiPrompt()`, latency tracking in `doRequest()`, `latencyCallback` field |
| `capture/DayOrganizerCoordinator.kt` | Add `groundingContextProvider: (suspend () -> String?)? = null` param; invoke before plan call |
| `viewmodel/TaskWallViewModel.kt` | Add DataStore key, StateFlow, setter for grounding toggle; pass latency callback; fetch grounding context in `startDayOrganizer()` |
| `ui/components/SettingsPanel.kt` | Add "Search Grounding" toggle row (minimal — one new SettingsRow entry) |

---

## Assumptions (auto-dev, no user consultation)

1. **Grounding model**: Use `DAY_PLAN_MODEL` (`gemini-2.5-flash`) for the grounding call — same capability tier as the day plan. Justified: grounding queries are open-ended and benefit from intelligence, not speed.

2. **Grounding call timeout**: `connectTimeoutMs = 15_000`, `readTimeoutMs = 30_000`. Shorter than day plan (90s) but longer than voice capture, since web searches add latency.

3. **Grounding failure is silent**: If the grounding call fails (timeout, 429, etc.), day planning continues without it. No error is surfaced to the user for grounding specifically — the day planner proceeds with its existing context.

4. **Location source**: Reuse `WeatherKeyStore.getLocation()`. If location is not configured, the grounding prompt omits the weather/local-events portion and just asks for general date context (holidays, etc.).

5. **SettingsPanel placement**: "Search Grounding" toggle goes at the bottom of the existing settings list, after sync interval. Label: "Search Grounding (Experimental)".

6. **No grounding for voice task capture**: Only day planning gets grounding. Voice task creation must remain fast and structured (JSON schema incompatibility).

7. **Latency StateFlow**: `lastGroundingLatencyMs` is exposed on ViewModel but not shown in settings UI text — SettingsPanel just shows a static label. The StateFlow is wired for future use.

8. **No grounding for multi-turn adjustment calls**: Only the first `callGeminiForDayPlan()` call gets grounding context. Adjustment turns receive it as part of the conversation history (already embedded in the plan summary).

---

## Non-Goals

- Deprecating WeatherRepository (evaluated and rejected — see above)
- Always-on grounding (opt-in only by design)
- UI metrics dashboard for latency
- Grounding for voice task capture
- New calendar view changes
