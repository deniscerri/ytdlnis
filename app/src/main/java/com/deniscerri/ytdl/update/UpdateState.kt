package com.deniscerri.ytdl.update

import java.io.File

/**
 * Lifecycle of the in-app APK update. There is only ever one update in flight, so this is a single
 * global state rather than a keyed map.
 *
 * [Downloading.progress] — 0f–1f once Content-Length is known, null before that.
 * [Downloading.log]      — human-readable speed/size line shown in the dialog and the notification.
 */
sealed class UpdateState {
    data object Idle       : UpdateState()
    data object Connecting : UpdateState()
    data class  Downloading(val progress: Float?, val log: String = "") : UpdateState()
    data class  Downloaded(val apk: File) : UpdateState()
    data class  Error(val message: String) : UpdateState()
}
