package com.deniscerri.ytdl.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.deniscerri.ytdl.R;

public class NotificationUtil {
    Context context;
    public static final String DOWNLOAD_SERVICE_CHANNEL_ID = "1";
    public static final int DOWNLOAD_NOTIFICATION_ID = 1;

    public static final String COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID = "2";
    public static final int COMMAND_DOWNLOAD_NOTIFICATION_ID = 2;

    public static NotificationCompat.Builder notificationBuilder;
    private static int PROGRESS_MAX = 100;
    private static int PROGRESS_CURR = 0;
    private NotificationManager notificationManager;

    public NotificationUtil(Context context){
        this.context = context;
        notificationBuilder = new NotificationCompat.Builder(context, DOWNLOAD_SERVICE_CHANNEL_ID);
        notificationManager = context.getSystemService(NotificationManager.class);
    }

    public void createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            //gui downloads
            CharSequence name = context.getString(R.string.download_notification_channel_name);
            String description = context.getString(R.string.download_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(DOWNLOAD_SERVICE_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);

            //command downloads
            name = "Command Downloads";
            description = "Notification that shows the download progress of a yt-dlp command";
            channel = new NotificationChannel(COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public Notification createDownloadServiceNotification(PendingIntent pendingIntent, String title){
        Notification notification = notificationBuilder
                .setContentTitle(title)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.stat_sys_download))
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(PROGRESS_MAX, PROGRESS_CURR, false)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        return notification;
    }

    public void updateDownloadNotification(int id, String desc, int progress, int queue, String title){
        String contentText = "";
        if (queue > 1) contentText += queue + " items left\n";
        contentText += desc.replaceAll("\\[.*?\\]", "");

        notificationBuilder.setProgress(100, (int) progress, false)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText));
        notificationManager.notify(id, notificationBuilder.build());
    }

    public void cancelNotification(int id){
        notificationManager.cancel(id);
    }
}
