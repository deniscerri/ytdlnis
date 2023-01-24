package com.deniscerri.ytdlnis.service

interface IDownloaderListener {
    fun onDownloadStart(downloadInfo: DownloadInfo?)
    fun onDownloadProgress(downloadInfo: DownloadInfo?)
    fun onDownloadError(downloadInfo: DownloadInfo?)
    fun onDownloadEnd(downloadInfo: DownloadInfo?)
    fun onDownloadCancel(downloadInfo: DownloadInfo?)
    fun onDownloadCancelAll(downloadInfo: DownloadInfo?)
    fun onDownloadServiceEnd()
}