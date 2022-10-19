package com.deniscerri.ytdlnis;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.deniscerri.ytdlnis.database.DBManager;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.databinding.ActivityMainBinding;
import com.deniscerri.ytdlnis.page.DownloadsFragment;
import com.deniscerri.ytdlnis.page.HomeFragment;
import com.deniscerri.ytdlnis.page.MoreFragment;
import com.deniscerri.ytdlnis.page.settings.SettingsActivity;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.deniscerri.ytdlnis.service.IDownloaderService;
import com.deniscerri.ytdlnis.util.NotificationUtil;
import com.deniscerri.ytdlnis.util.UpdateUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity{

    ActivityMainBinding binding;
    Context context;

    private static final String TAG = "MainActivity";

    private HomeFragment homeFragment;
    private DownloadsFragment downloadsFragment;
    private MoreFragment moreFragment;

    private Fragment lastFragment;
    private FragmentManager fm;

    private boolean isDownloadServiceRunning = false;
    public DownloaderService downloaderService;
    private ArrayList<IDownloaderListener> listeners = null;
    private IDownloaderService iDownloaderService;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            downloaderService = ((DownloaderService.LocalBinder) service).getService();
            iDownloaderService = (IDownloaderService) service;
            isDownloadServiceRunning = true;
            try{
                iDownloaderService.addActivity(MainActivity.this, listeners);
                for (int i = 0; i < listeners.size(); i++){
                    IDownloaderListener listener = listeners.get(i);
                    listener.onDownloadStart(iDownloaderService.getDownloadInfo());
                }
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setContentView(binding.getRoot());
        context = getBaseContext();
        reconnectDownloadService();
        checkUpdate();

        fm = getSupportFragmentManager();

        homeFragment = new HomeFragment();
        downloadsFragment = new DownloadsFragment();
        moreFragment = new MoreFragment();

        initFragments();

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if(id == R.id.home){
                if(lastFragment == homeFragment){
                    homeFragment.scrollToTop();
                }else{
                    this.setTitle(R.string.app_name);;
                }
                replaceFragment(homeFragment);
            }else if(id == R.id.downloads){
                if(lastFragment == downloadsFragment){
                    downloadsFragment.scrollToTop();
                }else {
                    this.setTitle(getString(R.string.downloads));
                }
                replaceFragment(downloadsFragment);
            }else if(id == R.id.more){
                if(lastFragment == moreFragment){
                    Intent intent = new Intent(context, SettingsActivity.class);
                    startActivity(intent);
                }else{
                    this.setTitle(getString(R.string.more));
                }
                replaceFragment(moreFragment);
            }
            return true;
        });
        Intent intent = getIntent();
        handleIntents(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            iDownloaderService.removeActivity(this);
            context.getApplicationContext().unbindService(serviceConnection);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntents(intent);
    }

    public void handleIntents(Intent intent){
        String action = intent.getAction();
        String type = intent.getType();
        if(Intent.ACTION_SEND.equals(action) && type != null){
            Log.e(TAG, action);

            homeFragment = new HomeFragment();
            downloadsFragment = new DownloadsFragment();
            moreFragment = new MoreFragment();
            if (type.equalsIgnoreCase("application/txt")){
                try{
                    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    InputStream is = getContentResolver().openInputStream(uri);
                    StringBuilder textBuilder = new StringBuilder();
                    Reader reader = new BufferedReader(new InputStreamReader
                            (is, Charset.forName(StandardCharsets.UTF_8.name())));
                    int c = 0;
                    while ((c = reader.read()) != -1) {
                        textBuilder.append((char) c);
                    }
                    String[] lines = textBuilder.toString().split("\n");
                    homeFragment.handleFileIntent(lines);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }else{
                homeFragment.handleIntent(intent);
            }
            initFragments();
        }
    }

    private void initFragments(){
        fm.beginTransaction()
                .replace(R.id.frame_layout, homeFragment)
                .add(R.id.frame_layout, downloadsFragment)
                .add(R.id.frame_layout, moreFragment)
                .hide(downloadsFragment)
                .hide(moreFragment)
                .commit();

        lastFragment = homeFragment;

        listeners = new ArrayList<>();
        listeners.add(homeFragment.listener);
        listeners.add(downloadsFragment.listener);
    }

    private void replaceFragment(Fragment f){
        fm.beginTransaction().hide(lastFragment).show(f).commit();
        lastFragment = f;
    }

    public void startDownloadService(ArrayList<Video> downloadQueue, IDownloaderListener awaitingListener){
        addQueueToDownloads(downloadQueue);
        if(isDownloadServiceRunning){
            iDownloaderService.updateQueue(downloadQueue);
            return;
        }
        if(!listeners.contains(awaitingListener)) listeners.add(awaitingListener);
        Intent serviceIntent = new Intent(context, DownloaderService.class);
        serviceIntent.putParcelableArrayListExtra("queue", downloadQueue);
        context.getApplicationContext().startService(serviceIntent);
        context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void addQueueToDownloads(ArrayList<Video> downloadQueue) {
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
            if (!sharedPreferences.getBoolean("incognito", false)) {
                DBManager dbManager = new DBManager(context);
                for (int i = downloadQueue.size() - 1; i >= 0; i--){
                    Video v = downloadQueue.get(i);
                    v.setQueuedDownload(true);
                    dbManager.addToHistory(v);
                }
                dbManager.close();
                downloadsFragment.setDownloading(true);
                downloadsFragment.initCards();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopDownloadService(){
        if(!isDownloadServiceRunning) return;
        try {
            iDownloaderService.removeActivity(this);
            context.getApplicationContext().unbindService(serviceConnection);
            context.getApplicationContext().stopService(new Intent(context.getApplicationContext(), DownloaderService.class));
        }catch (Exception ignored){}
        isDownloadServiceRunning = false;
    }

    public void cancelDownloadService(){
        if(!isDownloadServiceRunning) return;
        iDownloaderService.cancelDownload(true);
        stopDownloadService();
    }

    public void removeItemFromDownloadQueue(Video video, String type){
        iDownloaderService.removeItemFromDownloadQueue(video, type);
    }

    public boolean isDownloadServiceRunning() {
        ActivityManager.RunningServiceInfo service = getService(DownloaderService.class);
        if(service != null){
            if (service.foreground) {
                isDownloadServiceRunning = true;
                return true;
            }
        }
        return false;
    }

    private void reconnectDownloadService(){
        ActivityManager.RunningServiceInfo service = getService(DownloaderService.class);
        if(service != null){
            Intent serviceIntent = new Intent(context.getApplicationContext(), DownloaderService.class);
            serviceIntent.putExtra("rebind", true);
            context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            isDownloadServiceRunning = true;
        }
    }

    private ActivityManager.RunningServiceInfo getService(Class className){
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (className.getName().equals(service.service.getClassName())) {
                return service;
            }
        }
        return null;
    }

    private void checkUpdate(){
        SharedPreferences preferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        if(preferences.getBoolean("update_app", false)){
            UpdateUtil updateUtil = new UpdateUtil(this);
            updateUtil.updateApp();
        }
    }

}
