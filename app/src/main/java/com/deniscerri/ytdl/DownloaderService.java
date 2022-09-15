package com.deniscerri.ytdl;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class DownloaderService extends Service {

    private LocalBinder binder = new LocalBinder();
    private NotificationCompat.Builder builder;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Intent theIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, theIntent, PendingIntent.FLAG_IMMUTABLE);

        String title = intent.getStringExtra("title");
        int id = intent.getIntExtra("id", 1);

        Notification notification = App.notificationUtil.createDownloadServiceNotification(pendingIntent,title);
        startForeground(id, notification);
        return binder;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        stopForeground(true);
        stopSelf();
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {

        public DownloaderService getService() {
            return DownloaderService.this;
        }
    }
}
