package com.deniscerri.ytdlnis.service

import com.deniscerri.ytdlnis.database.models.ResultItem
import java.util.*

class DownloadInfo {
    var video: ResultItem? = null
    var progress = 0
    var downloadQueue: LinkedList<ResultItem>? = null
        private set
    var outputLine: String? = null
    var downloadStatus: String? = null
    var downloadPath: String? = null
    var downloadType: String? = null
    fun setDownloadQueue(downloadQueue: LinkedList<ResultItem>) {
        this.downloadQueue = downloadQueue
        video = downloadQueue.peek()
    }
}