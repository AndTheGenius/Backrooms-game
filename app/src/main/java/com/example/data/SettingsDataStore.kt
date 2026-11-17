package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.CamcorderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "camcorder_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val AUDIO_VOLUME = floatPreferencesKey("audio_volume")
        val LOOK_SENSITIVITY = floatPreferencesKey("look_sensitivity")
        val GLITCH_SAFETY_MODE = booleanPreferencesKey("glitch_safety_mode")
        val INVERT_LOOK = booleanPreferencesKey("invert_look")
        val SHOW_SIGNAL_COMPASS = booleanPreferencesKey("show_signal_compass")
    }

    val settingsFlow: Flow<CamcorderSettings> = context.settingsDataStore.data.map { prefs ->
        CamcorderSettings(
            audioEnabled = prefs[Keys.AUDIO_ENABLED] ?: true,
            audioVolume = prefs[Keys.AUDIO_VOLUME] ?: 0.75f,
            lookSensitivity = prefs[Keys.LOOK_SENSITIVITY] ?: 1.0f,
            glitchSafetyMode = prefs[Keys.GLITCH_SAFETY_MODE] ?: false,
            invertLook = prefs[Keys.INVERT_LOOK] ?: false,
            showSignalCompass = prefs[Keys.SHOW_SIGNAL_COMPASS] ?: true
        )
    }

    suspend fun updateSettings(transform: (CamcorderSettings) -> CamcorderSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = CamcorderSettings(
                audioEnabled = prefs[Keys.AUDIO_ENABLED] ?: true,
                audioVolume = prefs[Keys.AUDIO_VOLUME] ?: 0.75f,
                lookSensitivity = prefs[Keys.LOOK_SENSITIVITY] ?: 1.0f,
                glitchSafetyMode = prefs[Keys.GLITCH_SAFETY_MODE] ?: false,
                invertLook = prefs[Keys.INVERT_LOOK] ?: false,
                showSignalCompass = prefs[Keys.SHOW_SIGNAL_COMPASS] ?: true
            )
            val next = transform(current)
            prefs[Keys.AUDIO_ENABLED] = next.audioEnabled
            prefs[Keys.AUDIO_VOLUME] = next.audioVolume.coerceIn(0f, 1f)
            prefs[Keys.LOOK_SENSITIVITY] = next.lookSensitivity.coerceIn(0.4f, 2.2f)
            prefs[Keys.GLITCH_SAFETY_MODE] = next.glitchSafetyMode
            prefs[Keys.INVERT_LOOK] = next.invertLook
            prefs[Keys.SHOW_SIGNAL_COMPASS] = next.showSignalCompass
        }
    }
}
