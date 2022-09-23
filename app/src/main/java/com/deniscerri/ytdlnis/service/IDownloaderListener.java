package com.deniscerri.ytdlnis.service;

public interface IDownloaderListener {
    void onDownloadStart(DownloadInfo downloadInfo);
    void onDownloadProgress(DownloadInfo downloadInfo);
    void onDownloadError(DownloadInfo downloadInfo);
    void onDownloadEnd(DownloadInfo downloadInfo);
    void onDownloadServiceEnd();
}
