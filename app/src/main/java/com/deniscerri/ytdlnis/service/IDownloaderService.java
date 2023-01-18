package com.deniscerri.ytdlnis.service;

import android.app.Activity;

import com.deniscerri.ytdlnis.database.models.ResultItem;

import java.util.ArrayList;

public interface IDownloaderService {
    DownloadInfo getInfo();
    void addActivity(Activity activity, ArrayList<IDownloaderListener> callback);
    void removeActivity(Activity activity);
    void updateQueue(ArrayList<ResultItem> queue);
    void cancelDownload(boolean cancelAll);
    void removeItemFromDownloadQueue(ResultItem video, String type);
}
