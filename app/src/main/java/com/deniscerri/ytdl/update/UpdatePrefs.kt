package com.deniscerri.ytdl.update

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a found update announces itself, backed by the existing `update_app` switch so there is
 * one setting for one decision rather than two that can disagree.
 *
 * This governs the prompt and nothing else. The check still runs, the manifest is still saved and a
 * staged APK still resumes — turning it off only means the app waits to be asked rather than
 * interrupting on launch, which is what makes the manual check in Settings worth having.
 */
object UpdatePrefs {

    const val KEY_AUTO_PROMPT = "update_app"

    private val _autoPrompt = MutableStateFlow(true)

    /** True while a found update is allowed to raise its dialog on its own. Default: it is. */
    val autoPrompt: StateFlow<Boolean> = _autoPrompt.asStateFlow()

    /** Seeds the flow from storage. Called once per process, before anything can read it. */
    fun init(context: Context) {
        _autoPrompt.value = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_AUTO_PROMPT, true)
    }

    fun setAutoPrompt(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit { putBoolean(KEY_AUTO_PROMPT, enabled) }
        _autoPrompt.value = enabled
    }
}
