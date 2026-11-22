package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.work.DownloadWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WorkerEventBus {
    private val _events = MutableSharedFlow<DownloadWorker.WorkerProgress>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // last progress per download id, so cards can restore it when a screen is rebuilt
    private val _latest = MutableStateFlow<Map<Long, DownloadWorker.WorkerProgress>>(emptyMap())
    val latest = _latest.asStateFlow()

    fun post(event: DownloadWorker.WorkerProgress) {
        _latest.update { it + (event.downloadItemID to event) }
        _events.tryEmit(event)
    }

    fun clear(downloadItemID: Long) {
        _latest.update { it - downloadItemID }
    }
}
