package com.aure.androidtuner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aure.androidtuner.model.AppSettings
import com.aure.androidtuner.model.TileInteractionBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "android_tuner_settings")

class SettingsStorage(private val context: Context) {

    private val tileTapBehaviorKey = stringPreferencesKey("tile_tap_behavior")
    private val tileLongPressBehaviorKey = stringPreferencesKey("tile_long_press_behavior")
    private val applyLastPresetOnBootKey = booleanPreferencesKey("apply_last_preset_on_boot")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            tileTapBehavior = preferences[tileTapBehaviorKey]
                ?.let(::parseBehavior)
                ?: TileInteractionBehavior.SHOW_DIALOG,
            tileLongPressBehavior = preferences[tileLongPressBehaviorKey]
                ?.let(::parseBehavior)
                ?: TileInteractionBehavior.SHOW_DIALOG,
            applyLastPresetOnBoot = preferences[applyLastPresetOnBootKey] ?: false,
        )
    }

    suspend fun persistTileTapBehavior(behavior: TileInteractionBehavior) {
        context.settingsDataStore.edit { preferences ->
            preferences[tileTapBehaviorKey] = behavior.name
        }
    }

    suspend fun persistTileLongPressBehavior(behavior: TileInteractionBehavior) {
        context.settingsDataStore.edit { preferences ->
            preferences[tileLongPressBehaviorKey] = behavior.name
        }
    }

    suspend fun persistApplyLastPresetOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[applyLastPresetOnBootKey] = enabled
        }
    }

    private fun parseBehavior(raw: String): TileInteractionBehavior {
        return runCatching { TileInteractionBehavior.valueOf(raw) }
            .getOrDefault(TileInteractionBehavior.SHOW_DIALOG)
    }
}
