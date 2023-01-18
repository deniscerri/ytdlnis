package com.deniscerri.ytdlnis.service;

public interface IDownloaderListener {
    void onDownloadStart(DownloadInfo downloadInfo);
    void onDownloadProgress(DownloadInfo downloadInfo);
    void onDownloadError(DownloadInfo downloadInfo);
    void onDownloadEnd(DownloadInfo downloadInfo);
    void onDownloadCancel(DownloadInfo downloadInfo);
    void onDownloadCancelAll(DownloadInfo downloadInfo);
    void onDownloadServiceEnd();
}
