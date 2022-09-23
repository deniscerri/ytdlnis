package com.deniscerri.ytdlnis.page;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.DownloaderService;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.service.DownloadInfo;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.deniscerri.ytdlnis.service.IDownloaderService;
import com.deniscerri.ytdlnis.util.NotificationUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import java.util.Date;

public class CustomCommandActivity extends AppCompatActivity {
    private static final String TAG = "CustomCommandActivity";
    private MaterialToolbar topAppBar;
    private boolean isDownloadServiceRunning = false;
    public DownloaderService downloaderService;
    private TextView output;
    private EditText input;
    private ExtendedFloatingActionButton fab;
    private ExtendedFloatingActionButton cancelFab;
    private IDownloaderService iDownloaderService;
    Context context;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            downloaderService = ((DownloaderService.LocalBinder) service).getService();
            iDownloaderService = (IDownloaderService) service;
            isDownloadServiceRunning = true;
            try{
                iDownloaderService.addActivity(CustomCommandActivity.this, listener);
                listener.onDownloadStart(iDownloaderService.getDownloadInfo());
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloaderService = null;
            iDownloaderService = null;
            isDownloadServiceRunning = false;
        }
    };

    public IDownloaderListener listener = new IDownloaderListener() {

        public void onDownloadStart(DownloadInfo info) {
            input.setEnabled(false);
            output.setText("");
            swapFabs();
        }

        public void onDownloadProgress(DownloadInfo info) {
            output.append("\n" + info.getOutputLine());
        }

        public void onDownloadError(DownloadInfo info) {
            output.append("\n" + info.getOutputLine());
            input.setEnabled(true);
            swapFabs();
        }

        public void onDownloadEnd(DownloadInfo info) {
            output.append(info.getOutputLine());
            // MEDIA SCAN
            MediaScannerConnection.scanFile(context, new String[]{"/storage"}, null, null);
            input.setEnabled(true);
            swapFabs();
        }


        public void onDownloadServiceEnd() {
            stopDownloadService();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_command);

        context = getBaseContext();
        topAppBar = findViewById(R.id.custom_command_toolbar);
        topAppBar.setNavigationOnClickListener(view -> onBackPressed());
        output = findViewById(R.id.custom_command_output);
        output.setMovementMethod(new ScrollingMovementMethod());

        input = findViewById(R.id.command_edittext);
        input.requestFocus();

        fab = findViewById(R.id.command_fab);
        fab.setOnClickListener(view -> {
            if (isStoragePermissionGranted()){
                startDownloadService(input.getText().toString(), NotificationUtil.COMMAND_DOWNLOAD_NOTIFICATION_ID);
            }
        });

        cancelFab = findViewById(R.id.cancel_command_fab);
        cancelFab.setOnClickListener(view -> {
            cancelDownloadService();
            swapFabs();
            input.setEnabled(true);
        });
    }

    private void swapFabs(){
        int cancel = cancelFab.getVisibility();
        int start = fab.getVisibility();
        cancelFab.setVisibility(start);
        fab.setVisibility(cancel);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    public void startDownloadService(String command, int id){
        if(isDownloadServiceRunning) return;
        Intent serviceIntent = new Intent(context, DownloaderService.class);
        serviceIntent.putExtra("command", command);
        serviceIntent.putExtra("id", id);
        context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void stopDownloadService(){
        Log.e(TAG, String.valueOf(isDownloadServiceRunning));
        if(!isDownloadServiceRunning) return;
        iDownloaderService.removeActivity(this);
        context.getApplicationContext().unbindService(serviceConnection);
        downloaderService.stopForeground(true);
        downloaderService.stopSelf();
        isDownloadServiceRunning = false;
    }

    public void cancelDownloadService(){
        if(!isDownloadServiceRunning) return;
        iDownloaderService.cancelDownload();
        stopDownloadService();
    }

    public boolean isStoragePermissionGranted() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }else{
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }
}
