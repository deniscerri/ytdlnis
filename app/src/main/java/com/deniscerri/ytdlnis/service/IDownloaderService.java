package com.deniscerri.ytdlnis.service;

import android.app.Activity;
import com.deniscerri.ytdlnis.database.Video;
import java.util.ArrayList;

public interface IDownloaderService {
    DownloadInfo getInfo();
    void addActivity(Activity activity, ArrayList<IDownloaderListener> callback);
    void removeActivity(Activity activity);
    void updateQueue(ArrayList<Video> queue);
    void cancelDownload(boolean cancelAll);
    void removeItemFromDownloadQueue(Video video, String type);
}
