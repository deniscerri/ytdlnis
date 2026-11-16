package com.aistudio.kineticmedia.feature.browser

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.buttonDataStore by preferencesDataStore(name = "draggable_button_prefs")

class ButtonDataStore(private val context: Context) {
    companion object {
        val KEY_X = floatPreferencesKey("button_x")
        val KEY_Y = floatPreferencesKey("button_y")
    }

    val buttonX: Flow<Float> = context.buttonDataStore.data
        .map { prefs -> prefs[KEY_X] ?: -1f }

    val buttonY: Flow<Float> = context.buttonDataStore.data
        .map { prefs -> prefs[KEY_Y] ?: -1f }

    suspend fun savePosition(x: Float, y: Float) {
        context.buttonDataStore.edit { prefs ->
            prefs[KEY_X] = x
            prefs[KEY_Y] = y
        }
    }
}
