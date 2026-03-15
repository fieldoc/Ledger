package com.example.todowallapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.todowallapp.data.model.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ModePreferenceRepository(
    private val context: Context
) {
    private val modePreferenceKey = stringPreferencesKey("mode_preference")

    val modePreferenceFlow: Flow<AppMode?> = context.dataStore.data.map { preferences ->
        preferences[modePreferenceKey]
            ?.let { raw -> AppMode.entries.firstOrNull { it.name == raw } }
    }

    suspend fun getModePreference(): AppMode? {
        return modePreferenceFlow.first()
    }

    suspend fun setModePreference(mode: AppMode?) {
        context.dataStore.edit { preferences ->
            if (mode == null) {
                preferences.remove(modePreferenceKey)
            } else {
                preferences[modePreferenceKey] = mode.name
            }
        }
    }
}
