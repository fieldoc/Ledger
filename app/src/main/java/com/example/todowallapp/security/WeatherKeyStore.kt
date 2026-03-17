package com.example.todowallapp.security

import android.content.Context
import android.content.SharedPreferences

// DISTRIBUTION NOTE: This store uses plain SharedPreferences (not encrypted) so that
// API keys survive app reinstalls via Android Auto Backup. This is acceptable for a
// single-user kiosk device where disk is physically controlled. If this app is ever
// distributed to untrusted devices or multiple users, switch back to
// EncryptedSharedPreferences (and accept that keys won't persist across reinstalls)
// or use a server-side key vault.

class WeatherKeyStore internal constructor(
    private val keyValueStore: KeyValueStore
) {
    constructor(context: Context) : this(
        SharedPreferencesKeyValueStore(
            context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        )
    )

    fun getApiKey(): String? {
        return keyValueStore.getString(KEY_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    fun setApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be blank" }
        keyValueStore.putString(KEY_NAME, trimmed)
    }

    fun clearApiKey() {
        keyValueStore.remove(KEY_NAME)
    }

    fun getLocation(): String? {
        return keyValueStore.getString(LOCATION_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun setLocation(location: String) {
        keyValueStore.putString(LOCATION_KEY, location.trim())
    }

    internal interface KeyValueStore {
        fun getString(key: String): String?
        fun putString(key: String, value: String)
        fun remove(key: String)
    }

    internal class SharedPreferencesKeyValueStore(
        private val preferences: SharedPreferences
    ) : KeyValueStore {
        override fun getString(key: String): String? {
            return preferences.getString(key, null)
        }

        override fun putString(key: String, value: String) {
            preferences.edit().putString(key, value).apply()
        }

        override fun remove(key: String) {
            preferences.edit().remove(key).apply()
        }
    }

    companion object {
        private const val PREF_FILE = "weather_api_prefs"
        private const val KEY_NAME = "owm_api_key"
        private const val LOCATION_KEY = "weather_location"
    }
}
