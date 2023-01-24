package com.deniscerri.ytdlnis.service

import android.app.Activity
import com.deniscerri.ytdlnis.database.models.ResultItem

interface IDownloaderService {
    val info: DownloadInfo?
    fun addActivity(activity: Activity?, callback: ArrayList<IDownloaderListener?>?)
    fun removeActivity(activity: Activity?)
    fun updateQueue(queue: ArrayList<ResultItem?>?)
    fun cancelDownload(cancelAll: Boolean)
    fun removeItemFromDownloadQueue(video: ResultItem?, type: String?)
}