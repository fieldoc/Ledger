# Gemini Search Grounding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in Gemini googleSearch grounding to the day planning pipeline so Gemini can autonomously look up weather, local events, and business hours before building the day plan.

**Architecture:** Two-call approach — a grounding call (plain text, no schema) fetches real-time context via Gemini's googleSearch tool, then that context is injected into the day plan prompt (structured JSON call, unchanged). The grounding call is optional, gated by a user toggle persisted in DataStore. WeatherRepository is NOT deprecated (it provides structured 7-day forecast for calendar views; grounding provides unstructured text for day planning only).

**Tech Stack:** Kotlin, Gemini REST API (`generativelanguage.googleapis.com/v1beta`), Jetpack DataStore, Coroutines, Gson (JsonObject manual construction), Jetpack Compose (SettingsPanel extension)

---

## File Map

| File | Change |
|------|--------|
| `capture/repository/GeminiCaptureRepository.kt` | Add `latencyCallback` field, latency logging in day plan calls, `buildGroundingRequestBody()`, `fetchGroundingContext()`, `groundingContext` param to `buildDayPlanGeminiPrompt()` |
| `capture/DayOrganizerCoordinator.kt` | Add `groundingContextProvider` param to `startListening()`, invoke in `handleTranscription()` |
| `viewmodel/TaskWallViewModel.kt` | Add DataStore key + StateFlow + setter for grounding toggle, `_lastGroundingLatencyMs` StateFlow, wire grounding provider in `startDayOrganizer()` |
| `ui/components/SettingsPanel.kt` | Add `SEARCH_GROUNDING` enum value + row rendering + params |
| `ui/screens/TaskWallScreen.kt` | Thread new SettingsPanel params through |

---

## Task 1: Add latency tracking to GeminiCaptureRepository

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt`

Context: `GeminiCaptureRepository` has `callGeminiForDayPlan()` and `callGeminiForDayPlanMultiTurn()` that call `apiClient.generateContent()`. We want to measure latency for those calls and expose it via a callback field.

- [ ] **Step 1: Add `latencyCallback` field to `GeminiCaptureRepository`**

In `GeminiCaptureRepository.kt`, find the class body (after the constructor parameters). Add this field right after the companion object or at the top of the class body:

```kotlin
/**
 * Optional latency callback. Called after each Gemini HTTP call completes.
 * First arg: call tag (e.g. "dayplan", "grounding"). Second arg: elapsed milliseconds.
 */
var latencyCallback: ((String, Long) -> Unit)? = null
```

- [ ] **Step 2: Add latency timing to `callGeminiForDayPlan()`**

Find `callGeminiForDayPlan()` (currently at ~line 612). Replace it with:

```kotlin
suspend fun callGeminiForDayPlan(apiKey: String, prompt: GeminiPrompt): String = withContext(Dispatchers.IO) {
    val requestBody = buildRequestBody(prompt)
    val config = GeminiRequestConfig(
        model = DAY_PLAN_MODEL,
        connectTimeoutMs = 30_000,
        readTimeoutMs = 90_000
    )
    val start = System.currentTimeMillis()
    val response = apiClient.generateContent(apiKey = apiKey, requestBody = requestBody, config = config)
    val elapsed = System.currentTimeMillis() - start
    android.util.Log.d(TAG, "callGeminiForDayPlan latency: ${elapsed}ms")
    latencyCallback?.invoke("dayplan", elapsed)
    extractTextFromGeminiResponse(response)
}
```

- [ ] **Step 3: Add latency timing to `callGeminiForDayPlanMultiTurn()`**

Find `callGeminiForDayPlanMultiTurn()` (currently at ~line 627). Replace it with:

```kotlin
suspend fun callGeminiForDayPlanMultiTurn(
    apiKey: String,
    systemInstruction: String,
    turns: List<Pair<String, String>>,
    generationConfig: JsonObject? = null
): String = withContext(Dispatchers.IO) {
    val requestBody = buildMultiTurnRequestBody(
        systemInstruction = systemInstruction,
        turns = turns,
        generationConfig = generationConfig ?: buildGenerationConfig(
            temperature = 0.3,
            responseSchema = DAY_PLAN_SCHEMA
        )
    )
    val config = GeminiRequestConfig(
        model = DAY_PLAN_MODEL,
        connectTimeoutMs = 30_000,
        readTimeoutMs = 90_000
    )
    val start = System.currentTimeMillis()
    val response = apiClient.generateContent(apiKey = apiKey, requestBody = requestBody, config = config)
    val elapsed = System.currentTimeMillis() - start
    android.util.Log.d(TAG, "callGeminiForDayPlanMultiTurn latency: ${elapsed}ms")
    latencyCallback?.invoke("dayplan_multiturn", elapsed)
    extractTextFromGeminiResponse(response)
}
```

- [ ] **Step 4: Build and verify**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat: add latency tracking callback to GeminiCaptureRepository"
```

---

## Task 2: Add `buildGroundingRequestBody()` and `fetchGroundingContext()`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt`

Context: `buildRequestBody()` creates a Gson `JsonObject` with `systemInstruction`, `contents`, and optionally `generationConfig`. The grounding variant is identical but adds `"tools": [{ "googleSearch": {} }]` and must NOT include `responseSchema` (API constraint). `extractTextFromGeminiResponse()` already handles the grounding response format (it reads `candidates[0].content.parts[0].text`).

- [ ] **Step 1: Add `buildGroundingRequestBody()` private method**

In `GeminiCaptureRepository`, add this private function after `buildRequestBody()`:

```kotlin
/**
 * Like buildRequestBody() but adds googleSearch grounding tool.
 * IMPORTANT: Must NOT include responseSchema — mutually exclusive with grounding.
 */
private fun buildGroundingRequestBody(
    systemInstruction: String,
    userContent: String
): JsonObject = JsonObject().apply {
    add("systemInstruction", JsonObject().apply {
        add("parts", JsonArray().apply {
            add(JsonObject().apply { addProperty("text", systemInstruction) })
        })
    })
    add("contents", JsonArray().apply {
        add(JsonObject().apply {
            addProperty("role", "user")
            add("parts", JsonArray().apply {
                add(JsonObject().apply { addProperty("text", userContent) })
            })
        })
    })
    // googleSearch grounding tool — incompatible with responseSchema/responseMimeType
    add("tools", JsonArray().apply {
        add(JsonObject().apply {
            add("googleSearch", JsonObject())
        })
    })
    // Only temperature — no responseSchema or responseMimeType
    add("generationConfig", JsonObject().apply {
        addProperty("temperature", 0.1)
    })
}
```

- [ ] **Step 2: Add companion constant `TAG_GROUNDING`**

In the companion object, add:

```kotlin
private const val TAG_GROUNDING = "GeminiGrounding"
```

- [ ] **Step 3: Add `fetchGroundingContext()` suspend function**

Add this public function after `callGeminiForDayPlanMultiTurn()`. Add the necessary imports if not present: `java.time.format.TextStyle`, `java.util.Locale`.

```kotlin
/**
 * Fetches real-time context (weather, local events, holidays) via Gemini search grounding.
 * Returns plain text on success, null on any failure (grounding failures are silent).
 *
 * IMPORTANT: Uses googleSearch grounding tool, which is incompatible with responseSchema.
 * This is intentionally a plain-text call — do NOT add responseSchema here.
 */
suspend fun fetchGroundingContext(
    apiKey: String,
    location: String?,
    date: java.time.LocalDate,
    latencyCallback: ((Long) -> Unit)? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))

        val locationLine = if (!location.isNullOrBlank()) {
            "My location is: $location."
        } else {
            "Location not configured."
        }

        val userContent = buildString {
            appendLine("Today is $dayOfWeek, $dateStr. $locationLine")
            appendLine()
            appendLine("Please search the web and provide a concise plain-text summary (under 150 words) of:")
            if (!location.isNullOrBlank()) {
                appendLine("1. Today's weather conditions and forecast for $location")
                appendLine("2. Any noteworthy local events or activities happening today near $location")
            }
            appendLine("${if (!location.isNullOrBlank()) "3" else "1"}. Any national holidays, observances, or culturally significant events today")
            appendLine()
            appendLine("Plain text only. No markdown, no bullet points, no headers.")
        }

        val systemInstruction =
            "You are a helpful assistant. Use your Google Search tool to look up real-time information. " +
            "Summarize your findings concisely in plain prose."

        val requestBody = buildGroundingRequestBody(systemInstruction, userContent)
        val config = GeminiRequestConfig(
            model = DAY_PLAN_MODEL,
            connectTimeoutMs = 15_000,
            readTimeoutMs = 30_000
        )

        val start = System.currentTimeMillis()
        val responseJson = apiClient.generateContent(apiKey = apiKey, requestBody = requestBody, config = config)
        val elapsed = System.currentTimeMillis() - start

        android.util.Log.d(TAG_GROUNDING, "Grounding context fetched in ${elapsed}ms")
        latencyCallback?.invoke(elapsed)
        this@GeminiCaptureRepository.latencyCallback?.invoke("grounding", elapsed)

        val text = extractTextFromGeminiResponse(responseJson)
        if (text.isBlank()) null else text
    } catch (e: Exception) {
        android.util.Log.w(TAG_GROUNDING, "Grounding context fetch failed: ${e.message}")
        null
    }
}
```

Note: `TextStyle` requires `import java.time.format.TextStyle` — add to imports at the top of the file.

- [ ] **Step 4: Add missing import**

At the top of `GeminiCaptureRepository.kt`, verify or add:
```kotlin
import java.time.format.TextStyle
import java.util.Locale
```

- [ ] **Step 5: Build and verify**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat: add fetchGroundingContext() with googleSearch grounding to GeminiCaptureRepository"
```

---

## Task 3: Add `groundingContext` param to `buildDayPlanGeminiPrompt()`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt`

Context: `buildDayPlanGeminiPrompt()` already has `weatherForecast: String? = null` which injects a "WEATHER FORECAST:" section into `userContent`. We add a parallel `groundingContext: String? = null` param that injects a "REAL-TIME WEB CONTEXT:" section. These are separate: `weatherForecast` comes from `WeatherRepository` (structured icon data rendered as text), while `groundingContext` is the free-text Gemini grounding result.

- [ ] **Step 1: Add `groundingContext` parameter to `buildDayPlanGeminiPrompt()`**

Find `fun buildDayPlanGeminiPrompt(` (currently in the companion object). Add `groundingContext: String? = null` as the last parameter before the closing `)`:

```kotlin
fun buildDayPlanGeminiPrompt(
    rawTranscription: String,
    existingEvents: List<String>,
    existingTasks: List<ExistingTaskRef>,
    targetDate: LocalDate,
    currentTime: LocalTime,
    weatherForecast: String? = null,
    wakeHour: Int = 7,
    sleepHour: Int = 23,
    focusedListTitle: String? = null,
    groundingContext: String? = null   // <-- new
): GeminiPrompt {
```

- [ ] **Step 2: Inject grounding context section into userContent**

In the same function, find where `userContent` is built. It currently ends with:
```kotlin
        val userContent = """
...
$weatherSection$focusedListSection
USER'S REQUEST:
"$rawTranscription"
""".trimIndent()
```

Add a `groundingSection` variable alongside the existing `weatherSection`:

```kotlin
val groundingSection = if (!groundingContext.isNullOrBlank()) """

REAL-TIME WEB CONTEXT (fetched via search, use this for weather/events/holidays):
$groundingContext
""" else ""
```

Then include it in `userContent` after `focusedListSection`:

```kotlin
val userContent = """
TARGET DATE: $targetDate ($dayTypeLabel — ${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }})
CURRENT TIME: ${currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
$timeContext

EXISTING CALENDAR EVENTS (DO NOT move or modify these — schedule around them):
$eventsBlock

EXISTING GOOGLE TASKS (for reference — match by name if the user mentions one):
$tasksBlock$weatherSection$focusedListSection$groundingSection
USER'S REQUEST:
"$rawTranscription"
""".trimIndent()
```

- [ ] **Step 3: Build and verify**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (no callers pass `groundingContext` yet — that's fine, it has a default of null)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat: add groundingContext param to buildDayPlanGeminiPrompt"
```

---

## Task 4: Thread grounding context provider through DayOrganizerCoordinator

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt`

Context: `startListening()` already accepts `weatherProvider: (suspend () -> String?)? = null` and `DayOrganizerCoordinator` calls `weatherProvider?.invoke()` inside `handleTranscription()` before building the prompt. We add `groundingContextProvider` using the same pattern.

- [ ] **Step 1: Add `groundingContextProvider` field to class**

Find the private fields section (where `weatherProvider`, `wakeHour`, etc. are declared). Add:

```kotlin
private var groundingContextProvider: (suspend () -> String?)? = null
```

- [ ] **Step 2: Add `groundingContextProvider` parameter to `startListening()`**

In `startListening()`, add the parameter after `focusedListTitle: String? = null`:

```kotlin
fun startListening(
    scope: CoroutineScope,
    listProvider: () -> List<ExistingListRef>,
    taskProvider: () -> List<ExistingTaskRef>,
    eventsProvider: suspend () -> List<String>,
    selectedCalendarId: String = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
    weatherProvider: (suspend () -> String?)? = null,
    wakeHour: Int = 7,
    sleepHour: Int = 23,
    focusedListTitle: String? = null,
    groundingContextProvider: (suspend () -> String?)? = null   // <-- new
) {
    this.scope = scope
    this.listProvider = listProvider
    this.taskProvider = taskProvider
    this.eventsProvider = eventsProvider
    this.selectedCalendarId = selectedCalendarId
    this.weatherProvider = weatherProvider
    this.wakeHour = wakeHour
    this.sleepHour = sleepHour
    this.focusedListTitle = focusedListTitle
    this.groundingContextProvider = groundingContextProvider   // <-- new
    // ... rest unchanged
```

- [ ] **Step 3: Reset `groundingContextProvider` in `cancel()` / reset block**

Find where the coordinator resets state (the comment `// Reset all provider/config state set by startListening()`). Add:

```kotlin
groundingContextProvider = null
```

- [ ] **Step 4: Invoke grounding context in `handleTranscription()`**

In `handleTranscription()`, find where `weatherProvider?.invoke()` is called:

```kotlin
val weather = weatherProvider?.invoke()
```

Add a line after it:

```kotlin
val grounding = groundingContextProvider?.invoke()
```

Then pass it to `buildDayPlanGeminiPrompt()`:

```kotlin
val prompt: GeminiPrompt = geminiCaptureRepository.buildDayPlanGeminiPrompt(
    rawTranscription = trimmed,
    existingEvents = events,
    existingTasks = tasks,
    targetDate = targetDate,
    currentTime = LocalTime.now(),
    weatherForecast = weather,
    wakeHour = wakeHour,
    sleepHour = sleepHour,
    focusedListTitle = focusedListTitle,
    groundingContext = grounding   // <-- new
)
```

- [ ] **Step 5: Build and verify**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt
git commit -m "feat: thread groundingContextProvider through DayOrganizerCoordinator"
```

---

## Task 5: Add grounding setting and wiring in TaskWallViewModel

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

Context: The ViewModel uses DataStore for persistent settings. Existing pattern: `private val sleepStartHourKey = intPreferencesKey("sleep_start_hour")` with a corresponding `_sleepStartHour: MutableStateFlow<Int>` and a setter `setSleepSchedule(...)` that calls `context.dataStore.edit { prefs -> prefs[key] = value }`.

The grounding setting follows the exact same pattern. Additionally:
- `_lastGroundingLatencyMs: MutableStateFlow<Long?>(null)` tracks the last observed grounding latency.
- In `startDayOrganizer()`, if grounding is enabled + online, a `groundingContextProvider` lambda is passed to `dayOrganizerCoordinator.startListening()`.
- `geminiCaptureRepository.latencyCallback` is wired to update `_lastGroundingLatencyMs`.

- [ ] **Step 1: Add DataStore key and StateFlow**

Find the `private val` preference keys block (around line 183–191). Add:

```kotlin
private val geminiGroundingEnabledKey = booleanPreferencesKey("gemini_grounding_enabled")
```

Find the `MutableStateFlow` declarations block. Add:

```kotlin
private val _geminiGroundingEnabled = MutableStateFlow(false)
val geminiGroundingEnabled: StateFlow<Boolean> = _geminiGroundingEnabled.asStateFlow()

private val _lastGroundingLatencyMs = MutableStateFlow<Long?>(null)
val lastGroundingLatencyMs: StateFlow<Long?> = _lastGroundingLatencyMs.asStateFlow()
```

- [ ] **Step 2: Load from DataStore in `loadSettings()`**

Find `loadSettings()` (or the init block where DataStore is read). Find the section that reads preference values like:

```kotlin
prefs[sleepStartHourKey]?.let { _sleepStartHour.value = it }
```

Add:

```kotlin
prefs[geminiGroundingEnabledKey]?.let { _geminiGroundingEnabled.value = it }
```

- [ ] **Step 3: Add setter**

Find where `setSleepSchedule()` or other setters are defined. Add a new function following the same pattern:

```kotlin
fun setGeminiGroundingEnabled(enabled: Boolean) {
    _geminiGroundingEnabled.value = enabled
    viewModelScope.launch {
        context.dataStore.edit { prefs ->
            prefs[geminiGroundingEnabledKey] = enabled
        }
    }
}
```

- [ ] **Step 4: Wire latencyCallback on geminiCaptureRepository**

In the ViewModel's `init` block (or at the point where `geminiCaptureRepository` is used for the first time), add:

```kotlin
geminiCaptureRepository.latencyCallback = { tag, ms ->
    if (tag == "grounding") {
        _lastGroundingLatencyMs.value = ms
    }
    android.util.Log.d("GeminiLatency", "[$tag] ${ms}ms")
}
```

This should go in `init { }` or right after the ViewModel's primary initialization.

- [ ] **Step 5: Pass `groundingContextProvider` in `startDayOrganizer()`**

Find `startDayOrganizer()` in `TaskWallViewModel`. Find the call to `dayOrganizerCoordinator.startListening(...)`. Add the `groundingContextProvider` argument:

```kotlin
dayOrganizerCoordinator.startListening(
    scope = viewModelScope,
    listProvider = { /* existing */ },
    taskProvider = { /* existing */ },
    eventsProvider = { /* existing */ },
    selectedCalendarId = /* existing */,
    weatherProvider = /* existing */,
    wakeHour = /* existing */,
    sleepHour = /* existing */,
    focusedListTitle = /* existing */,
    groundingContextProvider = if (_geminiGroundingEnabled.value && isOnline.value) {
        {
            val apiKey = geminiKeyStore.getApiKey()
            val location = try {
                com.example.todowallapp.security.WeatherKeyStore(context).getLocation()
            } catch (e: Exception) {
                null
            }
            if (apiKey != null) {
                geminiCaptureRepository.fetchGroundingContext(
                    apiKey = apiKey,
                    location = location,
                    date = java.time.LocalDate.now()
                )
            } else null
        }
    } else null
)
```

- [ ] **Step 6: Build and verify**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt
git commit -m "feat: add geminiGroundingEnabled setting and wire grounding provider in TaskWallViewModel"
```

---

## Task 6: Add Search Grounding toggle to SettingsPanel

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/SettingsPanel.kt`
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

Context: `SettingsPanel` has a private `enum class SettingsItemType` that lists all rows. Items are conditionally added to a `buildList { }` and rendered in a `Column`. The encoder-navigable focus index cycles through the items list. For boolean toggles, left/right encoder input calls the toggle callback. The `SettingsItem` composable takes `label`, `description`, `isSelected`, `onClick`, and a trailing content lambda (usually `SettingsValuePill`).

- [ ] **Step 1: Add `SEARCH_GROUNDING` to `SettingsItemType` enum**

Find `private enum class SettingsItemType`. Add `SEARCH_GROUNDING` before `SWITCH_MODE`:

```kotlin
private enum class SettingsItemType {
    PLAN_DAY,
    THEME_MODE,
    LIGHT_HOURS,
    SLEEP_SCHEDULE,
    SYNC_INTERVAL,
    GEMINI_KEY,
    WEATHER,
    SEARCH_GROUNDING,   // <-- new
    SWITCH_MODE,
    SIGN_OUT,
    CLOSE
}
```

- [ ] **Step 2: Add new parameters to `SettingsPanel` composable**

In the `SettingsPanel` function signature, add two parameters after `onSearchCities`:

```kotlin
geminiGroundingEnabled: Boolean = false,
onGeminiGroundingToggle: (Boolean) -> Unit = {},
```

- [ ] **Step 3: Add `SEARCH_GROUNDING` to the items list**

In the `buildList { }` block (inside `remember`), add after `add(SettingsItemType.WEATHER)`:

```kotlin
if (geminiKeyPresent) {
    add(SettingsItemType.SEARCH_GROUNDING)
}
```

This shows the toggle only when a Gemini key is configured (grounding requires Gemini).

- [ ] **Step 4: Add accessibility description**

Find the `focusedItemDescription` when block. Add a case:

```kotlin
SettingsItemType.SEARCH_GROUNDING -> "Search Grounding selected"
```

- [ ] **Step 5: Handle encoder input for SEARCH_GROUNDING**

Find the encoder key handler (the `onKeyEvent` block that handles `Key.DirectionRight` / `Key.DirectionLeft` with `SettingsItemType.SYNC_INTERVAL -> adjustSyncInterval(...)`). Add:

```kotlin
SettingsItemType.SEARCH_GROUNDING -> onGeminiGroundingToggle(!geminiGroundingEnabled)
```

Both left and right encoder input should toggle the boolean (rotate either direction = flip). Add this for both the right-arrow and left-arrow branches.

Also in the enter/click handler (where `SettingsItemType.CLOSE -> onDismiss()`), add:

```kotlin
SettingsItemType.SEARCH_GROUNDING -> onGeminiGroundingToggle(!geminiGroundingEnabled)
```

- [ ] **Step 6: Render the SEARCH_GROUNDING row in the UI Column**

Find where `SettingsItemType.WEATHER` row is rendered. After its closing `}`, add the new row. Also add a `SettingsSectionHeader("SEARCH")` before it if the "INTELLIGENCE" section doesn't already feel appropriate. Follow the exact same structure as the SYNC_INTERVAL row:

```kotlin
SettingsDivider()

SettingsSectionHeader("INTELLIGENCE")

if (geminiKeyPresent) {
    SettingsItem(
        label = "Search Grounding",
        description = "Gemini queries weather & events (experimental)",
        isSelected = focusedItem == SettingsItemType.SEARCH_GROUNDING,
        onClick = { onGeminiGroundingToggle(!geminiGroundingEnabled) }
    ) {
        SettingsValuePill(text = if (geminiGroundingEnabled) "On" else "Off")
    }
}
```

Note: If there's already a `SettingsSectionHeader("INTELLIGENCE")` near the Gemini key area, don't add a duplicate — just add the new `SettingsItem` after the Weather item under an appropriate section.

- [ ] **Step 7: Update `TaskWallScreen.kt` — add params and pass through**

Find the `TaskWallScreen` composable function signature. Add the two new parameters:

```kotlin
geminiGroundingEnabled: Boolean = false,
onGeminiGroundingToggle: (Boolean) -> Unit = {},
```

Find the `SettingsPanel(...)` call site (at ~line 1225). Add the new arguments:

```kotlin
geminiGroundingEnabled = geminiGroundingEnabled,
onGeminiGroundingToggle = onGeminiGroundingToggle,
```

- [ ] **Step 8: Update `MainActivity.kt` — wire ViewModel state to TaskWallScreen**

Find where `TaskWallScreen(...)` is called in `MainActivity.kt`. Collect the new ViewModel states and pass them:

```kotlin
val geminiGroundingEnabled by viewModel.geminiGroundingEnabled.collectAsState()
```

Then pass to `TaskWallScreen`:
```kotlin
geminiGroundingEnabled = geminiGroundingEnabled,
onGeminiGroundingToggle = { viewModel.setGeminiGroundingEnabled(it) },
```

- [ ] **Step 9: Build and verify**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/SettingsPanel.kt
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git add app/src/main/java/com/example/todowallapp/MainActivity.kt
git commit -m "feat: add Search Grounding toggle to SettingsPanel (experimental)"
```

---

## Final Verification

- [ ] **Run full build one more time**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Manual smoke test checklist**

1. Open Settings panel → verify "Search Grounding (experimental)" row appears only when Gemini key is configured
2. Toggle it on and off via encoder or tap — verify value pill flips between "On" / "Off"
3. Kill app and reopen — verify the setting persists
4. With grounding ON + valid Gemini key: start a day planning session — check LogCat for `GeminiGrounding` tag with latency ms
5. With grounding OFF: start a day planning session — verify no `GeminiGrounding` log entries
6. With grounding ON + offline: verify day planning still proceeds (grounding lambda returns null silently)

---

## Self-Review Notes

**Spec coverage check:**
- ✅ `tools: [{ googleSearch: {} }]` — implemented via `buildGroundingRequestBody()` in Task 2
- ✅ Gemini autonomously queries weather, business hours, local events — grounding prompt asks for all three in Task 2
- ✅ WeatherRepository deprecation evaluation — documented in spec (NOT deprecated; serves structured calendar data)
- ✅ Latency monitoring — `latencyCallback` field, timing in Task 1 and Task 2, `lastGroundingLatencyMs` StateFlow in Task 5
- ✅ User setting to toggle grounding on/off — DataStore key + StateFlow + SettingsPanel toggle in Tasks 5–6

**API constraint:**
The grounding request (`buildGroundingRequestBody`) deliberately excludes `responseSchema`. The day plan request (`buildRequestBody` + `DAY_PLAN_SCHEMA`) is unchanged. These two calls use different request builders — the constraint is correctly enforced by design.

**Default state:**
`gemini_grounding_enabled` defaults to `false`. Users must explicitly opt in. This is intentional for an experimental feature.
