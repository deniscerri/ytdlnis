package com.deniscerri.ytdl.page;

import static com.deniscerri.ytdl.App.notificationUtil;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.deniscerri.ytdl.BuildConfig;
import com.deniscerri.ytdl.DownloaderService;
import com.deniscerri.ytdl.R;
import com.deniscerri.ytdl.page.settings.SettingsFragment;
import com.deniscerri.ytdl.util.NotificationUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.yausername.youtubedl_android.DownloadProgressCallback;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class CustomCommandActivity extends AppCompatActivity {
    private static final String TAG = "CustomCommandActivity";
    private MaterialToolbar topAppBar;
    private boolean running = false;
    private boolean isDownloadServiceRunning = false;
    public DownloaderService downloaderService;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private TextView output;
    private EditText input;
    private ExtendedFloatingActionButton fab;
    Context context;

    private final DownloadProgressCallback callback = (progress, etaInSeconds, line) -> CustomCommandActivity.this.runOnUiThread(() -> {
        output.append(line);
        notificationUtil.updateDownloadNotification(NotificationUtil.COMMAND_DOWNLOAD_NOTIFICATION_ID,
                line, (int) progress, 0, getString(R.string.running_ytdlp_command));
    });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            downloaderService = ((DownloaderService.LocalBinder) service).getService();
            isDownloadServiceRunning = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloaderService = null;
            isDownloadServiceRunning = false;
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
            runCommand(input.getText().toString());
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private void runCommand(String text){
        if (running) {
            Toast.makeText(this, "Cannot start command! A command is already in progress", Toast.LENGTH_LONG).show();
            return;
        }
        if(!text.startsWith("yt-dlp ")){
            Toast.makeText(context, "Wrong input! Try Again!", Toast.LENGTH_SHORT).show();
            return;
        }

        fab.hide();
        input.setEnabled(false);

        output.setText("");
        startDownloadService(getString(R.string.running_ytdlp_command), NotificationUtil.COMMAND_DOWNLOAD_NOTIFICATION_ID);
        text = text.substring(6).trim();

        YoutubeDLRequest request = new YoutubeDLRequest(Collections.emptyList());
        String commandRegex = "\"([^\"]*)\"|(\\S+)";
        Matcher m = Pattern.compile(commandRegex).matcher(text);
        while (m.find()) {
            if (m.group(1) != null) {
                request.addOption(m.group(1));
            } else {
                request.addOption(m.group(2));
            }
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        String downloadsDir = sharedPreferences.getString("command_path", getString(R.string.command_path));
        File youtubeDLDir = new File(downloadsDir);
        if (!youtubeDLDir.exists()) {
            boolean isDirCreated = youtubeDLDir.mkdir();
            if (!isDirCreated) {
                Toast.makeText(context, R.string.failed_making_directory, Toast.LENGTH_LONG).show();
            }
        }
        request.addOption("-o", youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s");

        running = true;

        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().execute(request, callback))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(youtubeDLResponse -> {
                    output.append(youtubeDLResponse.getOut());
                    running = false;
                    stopDownloadService();
                    // MEDIA SCAN
                    MediaScannerConnection.scanFile(context, new String[]{"/storage"}, null, null);
                    fab.show();
                    input.setEnabled(true);
                }, e -> {
                    if (BuildConfig.DEBUG) Log.e(TAG, getString(R.string.failed_download), e);
                    output.append(e.getMessage());
                    running = false;
                    stopDownloadService();
                    fab.show();
                    input.setEnabled(true);
                });
        compositeDisposable.add(disposable);
    }

    public void startDownloadService(String title, int id){
        if(isDownloadServiceRunning) return;
        Intent serviceIntent = new Intent(context, DownloaderService.class);
        serviceIntent.putExtra("title", title);
        serviceIntent.putExtra("id", id);
        context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void stopDownloadService(){
        if(!isDownloadServiceRunning) return;
        context.getApplicationContext().unbindService(serviceConnection);
        isDownloadServiceRunning = false;
    }
}
