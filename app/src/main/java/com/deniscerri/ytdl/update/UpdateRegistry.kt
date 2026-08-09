package com.deniscerri.ytdl.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for the app-update flow. Holds the [available] update (found by
 * [UpdateChecker]) and the live [state] of its download (written by [UpdateService], observed by
 * [UpdateGate]). Living outside any Activity means a config change — or dismissing the dialog —
 * never loses an in-flight APK download: re-opening simply re-reads the same flow.
 */
object UpdateRegistry {

    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _promptRequest = MutableStateFlow(false)

    /**
     * Set when something has asked for the update dialog outright — the settings screen's manual
     * check. It overrides [UpdatePrefs.autoPrompt] being off, and re-opens a prompt already
     * dismissed this session, so asking always gets an answer. Cleared the moment it is honoured.
     */
    val promptRequest: StateFlow<Boolean> = _promptRequest.asStateFlow()

    /** Records that a newer version exists; the UI shows the prompt off this. */
    fun setAvailable(update: AppUpdate) { _available.value = update }

    fun requestPrompt() { _promptRequest.value = true }

    fun clearPromptRequest() { _promptRequest.value = false }

    fun update(state: UpdateState) { _state.value = state }

    fun stateOf(): UpdateState = _state.value

    /** True while the APK is connecting or transferring. */
    val isActive: Boolean
        get() = when (_state.value) {
            UpdateState.Connecting, is UpdateState.Downloading -> true
            else -> false
        }
}
