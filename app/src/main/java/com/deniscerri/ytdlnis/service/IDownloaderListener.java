package com.deniscerri.ytdlnis.service;

import com.deniscerri.ytdlnis.database.Video;

public interface IDownloaderListener {
    void onDownloadStart(DownloadInfo downloadInfo);
    void onDownloadProgress(DownloadInfo downloadInfo);
    void onDownloadError(DownloadInfo downloadInfo);
    void onDownloadEnd(DownloadInfo downloadInfo);
    void onDownloadCancel(DownloadInfo downloadInfo);
    void onDownloadCancelAll(DownloadInfo downloadInfo);
    void onDownloadServiceEnd();
}
