package com.deniscerri.ytdlnis.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import com.deniscerri.ytdlnis.DownloaderService;
import com.deniscerri.ytdlnis.MainActivity;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.deniscerri.ytdlnis.service.IDownloaderService;
import com.deniscerri.ytdlnis.util.NotificationUtil;

import java.util.ArrayList;

public class NotificationReceiver extends BroadcastReceiver {

    public DownloaderService downloaderService;
    private ArrayList<IDownloaderListener> listeners = null;
    private IDownloaderService iDownloaderService;
    private Context context;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            downloaderService = ((DownloaderService.LocalBinder) service).getService();
            iDownloaderService = (IDownloaderService) service;
            cancelDownload();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloaderService = null;
            iDownloaderService = null;
        }
    };


    @Override
    public void onReceive(Context c, Intent intent) {
        context = c;
        String message = intent.getStringExtra("cancel");
        if (message != null){
            Intent serviceIntent = new Intent(context.getApplicationContext(), DownloaderService.class);
            serviceIntent.putExtra("rebind", true);
            context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void cancelDownload(){
        try {
            iDownloaderService.cancelDownload(true);
            context.getApplicationContext().unbindService(serviceConnection);
            context.getApplicationContext().stopService(new Intent(context.getApplicationContext(), DownloaderService.class));
        }catch (Exception ignored){}
    }

}
