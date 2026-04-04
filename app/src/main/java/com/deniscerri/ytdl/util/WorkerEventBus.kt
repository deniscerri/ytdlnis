package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.work.DownloadWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WorkerEventBus {
    private val _events = MutableSharedFlow<DownloadWorker.WorkerProgress>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun post(event: DownloadWorker.WorkerProgress) {
        _events.tryEmit(event)
    }
}