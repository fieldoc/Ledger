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
        val apiKey = keyStore.getApiKey()
        if (apiKey == null) {
            Log.d(TAG, "No API key configured — skipping weather fetch")
            return emptyMap()
        }
        val location = keyStore.getLocation()
        if (location == null) {
            Log.d(TAG, "No location configured — skipping weather fetch")
            return emptyMap()
        }

        // Check cache
        val cached = loadCache()
        if (cached != null) {
            Log.d(TAG, "Using cached forecast (${cached.size} days)")
            return cached
        }

        // Fetch from API
        return withContext(Dispatchers.IO) {
            try {
                val coords = geocodeCity(apiKey, location)
                if (coords == null) {
                    Log.w(TAG, "Geocode failed for '$location' — no coordinates returned")
                    return@withContext emptyMap()
                }
                Log.d(TAG, "Geocoded '$location' → (${coords.first}, ${coords.second})")

                val forecast = fetchForecast(apiKey, coords.first, coords.second)
                if (forecast.isNotEmpty()) {
                    saveCache(forecast)
                    Log.d(TAG, "Fetched forecast: ${forecast.size} days")
                } else {
                    Log.w(TAG, "Forecast fetch returned empty map")
                }
                forecast
            } catch (e: Exception) {
                Log.w(TAG, "Weather fetch failed: ${e.message}", e)
                emptyMap()
            }
        }
    }

    data class CitySuggestion(val name: String, val state: String?, val country: String)

    /**
     * Search for city names via OWM Geocoding API. Returns up to 5 suggestions.
     * Returns empty list if no API key or on error.
     */
    suspend fun searchCities(query: String): List<CitySuggestion> {
        if (query.length < 2) return emptyList()
        val apiKey = keyStore.getApiKey() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL("https://api.openweathermap.org/geo/1.0/direct?q=$encoded&limit=5&appid=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                try {
                    val response = connection.inputStream.bufferedReader().readText()
                    val array = org.json.JSONArray(response)
                    buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            add(CitySuggestion(
                                name = obj.getString("name"),
                                state = obj.optString("state", "").takeIf { it.isNotEmpty() },
                                country = obj.getString("country")
                            ))
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "City search failed", e)
                emptyList()
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

    /**
     * Tries OneCall 3.0 first (paid), then falls back to free 2.5 forecast API.
     */
    private fun fetchForecast(apiKey: String, lat: Double, lon: Double): Map<LocalDate, WeatherCondition> {
        // Try OneCall 3.0 first
        val oneCallResult = tryOneCall3(apiKey, lat, lon)
        if (oneCallResult != null) return oneCallResult

        // Fall back to free 2.5 forecast API
        return tryForecast25(apiKey, lat, lon)
    }

    /**
     * OneCall 3.0 — requires paid subscription. Returns null on auth/HTTP failure.
     */
    private fun tryOneCall3(apiKey: String, lat: Double, lon: Double): Map<LocalDate, WeatherCondition>? {
        val url = URL(
            "https://api.openweathermap.org/data/3.0/onecall?lat=$lat&lon=$lon&exclude=minutely,hourly,alerts&units=metric&appid=$apiKey"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val code = connection.responseCode
            if (code != 200) {
                Log.d(TAG, "OneCall 3.0 returned HTTP $code — falling back to 2.5")
                return null
            }
            val response = connection.inputStream.bufferedReader().readText()
            parseOneCallResponse(response)
        } catch (e: Exception) {
            Log.d(TAG, "OneCall 3.0 failed: ${e.message} — falling back to 2.5")
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Free 2.5 forecast API — 5-day / 3-hour forecast, grouped to daily.
     */
    private fun tryForecast25(apiKey: String, lat: Double, lon: Double): Map<LocalDate, WeatherCondition> {
        val url = URL(
            "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&units=metric&appid=$apiKey"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val code = connection.responseCode
            if (code != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "no body"
                Log.w(TAG, "Forecast 2.5 returned HTTP $code: $errorBody")
                return emptyMap()
            }
            val response = connection.inputStream.bufferedReader().readText()
            parseForecast25Response(response)
        } catch (e: Exception) {
            Log.w(TAG, "Forecast 2.5 failed: ${e.message}", e)
            emptyMap()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseOneCallResponse(response: String): Map<LocalDate, WeatherCondition> {
        val json = JSONObject(response)
        val daily = json.getJSONArray("daily")
        val zone = ZoneId.systemDefault()
        return buildMap {
            for (i in 0 until daily.length()) {
                val day = daily.getJSONObject(i)
                val dt = day.getLong("dt")
                val date = Instant.ofEpochSecond(dt).atZone(zone).toLocalDate()
                val weather = day.getJSONArray("weather").getJSONObject(0)
                val conditionCode = weather.getInt("id")
                put(date, WeatherCondition.fromOwmCode(conditionCode))
            }
        }
    }

    /**
     * The 2.5 forecast returns 3-hour intervals over 5 days.
     * We pick the most frequent condition per day (mode) to get a daily summary.
     */
    private fun parseForecast25Response(response: String): Map<LocalDate, WeatherCondition> {
        val json = JSONObject(response)
        val list = json.getJSONArray("list")
        val zone = ZoneId.systemDefault()

        // Group condition codes by date
        val codesByDate = mutableMapOf<LocalDate, MutableList<Int>>()
        for (i in 0 until list.length()) {
            val entry = list.getJSONObject(i)
            val dt = entry.getLong("dt")
            val date = Instant.ofEpochSecond(dt).atZone(zone).toLocalDate()
            val weather = entry.getJSONArray("weather").getJSONObject(0)
            val code = weather.getInt("id")
            codesByDate.getOrPut(date) { mutableListOf() }.add(code)
        }

        // Pick the most frequent condition code for each day
        return buildMap {
            codesByDate.forEach { (date, codes) ->
                val dominantCode = codes.groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key ?: codes.first()
                put(date, WeatherCondition.fromOwmCode(dominantCode))
            }
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

    companion object {
        private const val TAG = "WeatherRepository"
    }
}
