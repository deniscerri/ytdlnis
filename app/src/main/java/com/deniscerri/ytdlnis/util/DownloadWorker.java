package com.deniscerri.ytdlnis.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.deniscerri.ytdlnis.MainActivity;
import com.deniscerri.ytdlnis.database.Video;

import java.util.ArrayList;

import kotlin.random.Random;

public class DownloadWorker extends Worker {
    private Context context;
    private WorkerParameters workerParams;
    public DownloadWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        this.context = appContext;
        this.workerParams = workerParams;
    }

    @NonNull
    @Override
    public Result doWork() {
        //TODO
        return Result.success();
    }
}
