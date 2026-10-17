package com.example.measurear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.measurear.util.MeasureUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "measure_ar_settings")

data class UserSettings(
    val unit: MeasureUnit = MeasureUnit.CM,
    val showSurfaceGrid: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val MEASURE_UNIT = stringPreferencesKey("measure_unit")
        val SHOW_GRID = booleanPreferencesKey("show_surface_grid")
        val HAPTICS = booleanPreferencesKey("haptic_feedback")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val unitName = prefs[Keys.MEASURE_UNIT] ?: MeasureUnit.CM.name
        val unit = runCatching { MeasureUnit.valueOf(unitName) }.getOrDefault(MeasureUnit.CM)
        UserSettings(
            unit = unit,
            showSurfaceGrid = prefs[Keys.SHOW_GRID] ?: true,
            hapticFeedbackEnabled = prefs[Keys.HAPTICS] ?: true
        )
    }

    suspend fun setMeasureUnit(unit: MeasureUnit) {
        context.dataStore.edit { it[Keys.MEASURE_UNIT] = unit.name }
    }

    suspend fun setShowSurfaceGrid(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_GRID] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }
}
