# Weather-Tinted Calendar Days — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add subtle weather-based background tints to calendar day cells (month view) and day rows (week view), giving users ambient awareness of which days are good for outdoor errands without checking a separate weather app.

**Architecture:** New `WeatherRepository` fetches an 8-day forecast from OpenWeatherMap's API, caches it in DataStore, and exposes it as a `Map<LocalDate, WeatherCondition>`. The ViewModel holds a `weatherForecast` StateFlow that calendar views consume. Each calendar cell/row gets a barely-visible background tint based on the weather condition. Week view also gets a tiny weather icon next to the date label.

**Tech Stack:** OpenWeatherMap One Call 3.0 API, kotlinx.serialization for cache, DataStore for persistence, Jetpack Compose for tints.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `data/model/WeatherCondition.kt` | Create | Enum: CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW, STORM + tint mapping |
| `data/repository/WeatherRepository.kt` | Create | API client + DataStore caching + location |
| `security/WeatherKeyStore.kt` | Create | Encrypted API key storage (follows GeminiKeyStore pattern) |
| `viewmodel/TaskWallViewModel.kt` | Modify | Add weatherForecast StateFlow, trigger daily refresh |
| `ui/components/CalendarMonthView.kt` | Modify | Apply tint to day cells |
| `ui/components/CalendarWeekView.kt` | Modify | Apply tint to day rows + weather icon |
| `ui/components/SettingsPanel.kt` | Modify | Add weather location + API key settings |
| `MainActivity.kt` | Modify | Wire WeatherKeyStore + location to ViewModel |

---

## Task 1: WeatherCondition model + tint colors

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/data/model/WeatherCondition.kt`

- [ ] **Step 1: Create the enum with tint mapping**

```kotlin
package com.example.todowallapp.data.model

import androidx.compose.ui.graphics.Color

enum class WeatherCondition(val tintColor: Color, val icon: String) {
    CLEAR(Color(0x14FFD54F), "☀"),           // Warm gold wash
    PARTLY_CLOUDY(Color.Transparent, "⛅"),    // No tint (neutral default)
    CLOUDY(Color(0x0A90A4AE), "☁"),           // Cool grey
    RAIN(Color(0x12607D8B), "🌧"),             // Slate blue
    SNOW(Color(0x0ECFD8DC), "❄"),             // Soft white/lavender
    STORM(Color(0x12FFAB91), "⚡");           // Muted amber

    companion object {
        /**
         * Map OpenWeatherMap condition codes to our simplified enum.
         * See: https://openweathermap.org/weather-conditions
         */
        fun fromOwmCode(code: Int): WeatherCondition = when (code) {
            in 200..299 -> STORM        // Thunderstorm
            in 300..399 -> RAIN         // Drizzle
            in 500..599 -> RAIN         // Rain
            in 600..699 -> SNOW         // Snow
            in 700..762 -> CLOUDY       // Atmosphere (mist, fog, haze)
            771, 781 -> STORM           // Squall, tornado
            800 -> CLEAR                // Clear sky
            801 -> PARTLY_CLOUDY        // Few clouds
            in 802..804 -> CLOUDY       // Scattered/broken/overcast clouds
            else -> PARTLY_CLOUDY
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 2: WeatherKeyStore (encrypted API key storage)

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/security/WeatherKeyStore.kt`

- [ ] **Step 1: Create key store following GeminiKeyStore pattern**

```kotlin
package com.example.todowallapp.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class WeatherKeyStore(context: Context) {
    private val preferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        preferences = EncryptedSharedPreferences.create(
            context.applicationContext,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String? = preferences.getString(KEY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
    fun hasApiKey(): Boolean = getApiKey() != null

    fun setApiKey(apiKey: String) {
        require(apiKey.trim().isNotEmpty()) { "API key cannot be blank" }
        preferences.edit().putString(KEY_NAME, apiKey.trim()).apply()
    }

    fun clearApiKey() {
        preferences.edit().remove(KEY_NAME).apply()
    }

    fun getLocation(): String? = preferences.getString(LOCATION_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setLocation(location: String) {
        preferences.edit().putString(LOCATION_KEY, location.trim()).apply()
    }

    companion object {
        private const val PREF_FILE = "weather_secure_prefs"
        private const val KEY_NAME = "owm_api_key"
        private const val LOCATION_KEY = "weather_location"
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 3: WeatherRepository (API client + caching)

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/data/repository/WeatherRepository.kt`

- [ ] **Step 1: Create repository with OWM API integration**

```kotlin
package com.example.todowallapp.data.repository

import android.content.Context
import android.util.Log
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.security.WeatherKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeatherRepository(
    private val context: Context,
    private val keyStore: WeatherKeyStore
) {
    private val prefs by lazy {
        context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    }

    /**
     * Returns an 8-day forecast map. Uses cache if fresh (< 3 hours old).
     * Returns empty map if no API key or location configured, or on error.
     */
    suspend fun getForecast(): Map<LocalDate, WeatherCondition> {
        val apiKey = keyStore.getApiKey() ?: return emptyMap()
        val location = keyStore.getLocation() ?: return emptyMap()

        // Check cache
        val cached = loadCache()
        if (cached != null) return cached

        // Fetch from API
        return withContext(Dispatchers.IO) {
            try {
                val coords = geocodeCity(apiKey, location) ?: return@withContext emptyMap()
                val forecast = fetchForecast(apiKey, coords.first, coords.second)
                saveCache(forecast)
                forecast
            } catch (e: Exception) {
                Log.w("WeatherRepository", "Weather fetch failed", e)
                emptyMap()
            }
        }
    }

    private fun geocodeCity(apiKey: String, city: String): Pair<Double, Double>? {
        val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
        val url = URL("https://api.openweathermap.org/geo/1.0/direct?q=$encodedCity&limit=1&appid=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val response = connection.inputStream.bufferedReader().readText()
            val array = org.json.JSONArray(response)
            if (array.length() == 0) return null
            val first = array.getJSONObject(0)
            first.getDouble("lat") to first.getDouble("lon")
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchForecast(apiKey: String, lat: Double, lon: Double): Map<LocalDate, WeatherCondition> {
        val url = URL(
            "https://api.openweathermap.org/data/3.0/onecall?lat=$lat&lon=$lon&exclude=minutely,hourly,alerts&units=metric&appid=$apiKey"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val daily = json.getJSONArray("daily")
            val zone = ZoneId.systemDefault()
            buildMap {
                for (i in 0 until daily.length()) {
                    val day = daily.getJSONObject(i)
                    val dt = day.getLong("dt")
                    val date = Instant.ofEpochSecond(dt).atZone(zone).toLocalDate()
                    val weather = day.getJSONArray("weather").getJSONObject(0)
                    val conditionCode = weather.getInt("id")
                    put(date, WeatherCondition.fromOwmCode(conditionCode))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCache(): Map<LocalDate, WeatherCondition>? {
        val cacheTime = prefs.getLong("cache_time", 0L)
        val threeHoursAgo = System.currentTimeMillis() - 3 * 60 * 60 * 1000
        if (cacheTime < threeHoursAgo) return null

        val raw = prefs.getString("cache_data", null) ?: return null
        return try {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val date = LocalDate.parse(key)
                    val condition = WeatherCondition.valueOf(json.getString(key))
                    put(date, condition)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveCache(forecast: Map<LocalDate, WeatherCondition>) {
        val json = JSONObject()
        forecast.forEach { (date, condition) ->
            json.put(date.toString(), condition.name)
        }
        prefs.edit()
            .putString("cache_data", json.toString())
            .putLong("cache_time", System.currentTimeMillis())
            .apply()
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 4: ViewModel integration

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

- [ ] **Step 1: Add weatherForecast StateFlow**

Add to TaskWallViewModel constructor params:
```kotlin
private val weatherRepository: WeatherRepository? = null
```

Add state:
```kotlin
private val _weatherForecast = MutableStateFlow<Map<LocalDate, WeatherCondition>>(emptyMap())
val weatherForecast: StateFlow<Map<LocalDate, WeatherCondition>> = _weatherForecast.asStateFlow()
```

Add refresh method:
```kotlin
fun refreshWeather() {
    val repo = weatherRepository ?: return
    viewModelScope.launch {
        _weatherForecast.value = repo.getForecast()
    }
}
```

- [ ] **Step 2: Trigger weather refresh on sign-in and daily**

In `onSignedIn()` or `init`, after the task sync:
```kotlin
refreshWeather()
```

In the periodic sync timer (wherever `scheduleSyncLoop` or similar is), add:
```kotlin
// Refresh weather once per 3 hours (aligned with cache TTL)
if (syncCount % 36 == 0) { // every 36 * 5min = 3 hours
    refreshWeather()
}
```

Or simpler: call `refreshWeather()` inside `performRefresh()` at the end — the cache handles throttling.

- [ ] **Step 3: Build to verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 5: Wire WeatherRepository through MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/MainActivity.kt`

- [ ] **Step 1: Create WeatherKeyStore and WeatherRepository**

In `TaskWallApp`, alongside existing repositories:
```kotlin
val weatherKeyStore = remember { WeatherKeyStore(appContext) }
val weatherRepository = remember { WeatherRepository(appContext, weatherKeyStore) }
```

Pass `weatherRepository` to the ViewModel factory.

- [ ] **Step 2: Thread weatherForecast to calendar views**

In `WallModeContent`, collect the forecast:
```kotlin
val weatherForecast by viewModel.weatherForecast.collectAsState()
```

Pass it to `CalendarScreen`:
```kotlin
CalendarScreen(
    ...,
    weatherForecast = weatherForecast,
    ...
)
```

Thread through CalendarScreen → CalendarMonthView / CalendarWeekView.

- [ ] **Step 3: Build to verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 6: Apply weather tints to CalendarMonthView

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarMonthView.kt`

- [ ] **Step 1: Add weatherForecast parameter**

Add to `CalendarMonthView` signature:
```kotlin
weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
```

Pass to `CalendarDayCell`:
```kotlin
CalendarDayCell(
    ...,
    weatherTint = weatherForecast[date]?.tintColor ?: Color.Transparent,
    ...
)
```

- [ ] **Step 2: Apply tint in CalendarDayCell**

Add `weatherTint: Color = Color.Transparent` parameter.

Modify the `backgroundColor` computation to layer the weather tint:
```kotlin
val backgroundColor by animateColorAsState(
    targetValue = when {
        isToday -> colors.accentPrimary.copy(alpha = 0.04f)
        weatherTint != Color.Transparent -> weatherTint
        else -> colors.surfaceCard.copy(alpha = 0f)
    },
    animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
    label = "calendarDayBackground"
)
```

The tint colors are already defined with very low opacity (8-12%) in the `WeatherCondition` enum, so they layer subtly.

- [ ] **Step 3: Build to verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 7: Apply weather tints to CalendarWeekView

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarWeekView.kt`

- [ ] **Step 1: Add weatherForecast parameter**

Add to `CalendarWeekView` signature:
```kotlin
weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
```

- [ ] **Step 2: Apply tint to day row backgrounds**

In the `Row` modifier for each day, modify the background:
```kotlin
val weatherCondition = weatherForecast[date]
val weatherTint = weatherCondition?.tintColor ?: Color.Transparent

Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(DayRowShape)
        .background(
            when {
                isToday -> colors.accentPrimary.copy(alpha = 0.07f)
                weatherTint != Color.Transparent -> weatherTint
                else -> Color.Transparent
            },
            DayRowShape
        )
        ...
)
```

- [ ] **Step 3: Add weather icon to DayLabel**

In the `DayLabel` composable (or next to it in the Row), add a tiny weather icon when forecast available:

```kotlin
if (weatherCondition != null && weatherCondition != WeatherCondition.PARTLY_CLOUDY) {
    Text(
        text = weatherCondition.icon,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = colors.textMuted,
        modifier = Modifier.padding(start = 4.dp)
    )
}
```

12dp icon, muted color, only shown for non-neutral conditions.

- [ ] **Step 4: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 8: Settings panel — weather configuration

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/SettingsPanel.kt`

- [ ] **Step 1: Add weather settings section**

Add a "Weather" section after the existing settings with:
- Location text field (city name, e.g., "San Francisco, CA")
- OWM API key field (masked, with save/clear)
- Small help text: "Get a free key at openweathermap.org"

Follow the existing settings section pattern (title + rows).

This requires threading `weatherKeyStore` or callbacks through from MainActivity → SettingsPanel. Use callback pattern:
```kotlin
onWeatherLocationChange: (String) -> Unit = {},
onWeatherApiKeyChange: (String) -> Unit = {},
weatherLocation: String = "",
weatherApiKeyPresent: Boolean = false,
```

- [ ] **Step 2: Wire settings callbacks through MainActivity**

Connect the SettingsPanel callbacks to `WeatherKeyStore.setLocation()` / `setApiKey()` and trigger `viewModel.refreshWeather()` after save.

- [ ] **Step 3: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`

---

## Task 9: Add INTERNET permission (if not present)

**Files:**
- Check: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Verify INTERNET permission exists**

The app already makes network calls (Google Tasks API), so this should already be present. Verify:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

If missing, add it.

---

## Execution Order

Tasks 1-3 are independent (model, key store, repository) — can parallel.
Task 4 depends on 1+3 (ViewModel needs WeatherCondition + WeatherRepository).
Task 5 depends on 4 (MainActivity wiring).
Tasks 6+7 depend on 5 (calendar views need forecast data).
Task 8 depends on 2+5 (settings need key store + callbacks).
Task 9 is independent.

**Suggested flow:** 1+2+3 parallel → 4 → 5 → 6+7+8 parallel → 9

---

## Verification

After all tasks, run full build:
```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug
```

Manual test: Install on device, enter OWM API key + city in settings, navigate to calendar month/week view, verify subtle tints appear.
