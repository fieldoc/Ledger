package com.example.todowallapp.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class WeatherKeyStore internal constructor(
    private val keyValueStore: KeyValueStore
) {
    constructor(context: Context) : this(
        SharedPreferencesKeyValueStore(
            createEncryptedPreferences(context.applicationContext)
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
        private const val PREF_FILE = "weather_secure_prefs"
        private const val KEY_NAME = "owm_api_key"
        private const val LOCATION_KEY = "weather_location"

        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
