package com.deniscerri.ytdlnis;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.databinding.ActivityMainBinding;
import com.deniscerri.ytdlnis.page.HistoryFragment;
import com.deniscerri.ytdlnis.page.HomeFragment;
import com.deniscerri.ytdlnis.page.MoreFragment;
import com.deniscerri.ytdlnis.page.settings.SettingsActivity;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.deniscerri.ytdlnis.service.IDownloaderService;
import com.deniscerri.ytdlnis.util.UpdateUtil;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity{

    ActivityMainBinding binding;
    Context context;

    private static final String TAG = "MainActivity";

    private HomeFragment homeFragment;
    private HistoryFragment historyFragment;
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
                for (int i = 0; i < listeners.size(); i++){
                    IDownloaderListener listener = listeners.get(i);
                    iDownloaderService.addActivity(MainActivity.this, listener);
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
        historyFragment = new HistoryFragment();
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
                if(lastFragment == historyFragment){
                    historyFragment.scrollToTop();
                }else {
                    this.setTitle(getString(R.string.downloads));
                }
                replaceFragment(historyFragment);
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntents(intent);
    }

    public void handleIntents(Intent intent){
        String action = intent.getAction();
        if(Intent.ACTION_SEND.equals(action)){
            Log.e(TAG, action);

            homeFragment = new HomeFragment();
            historyFragment = new HistoryFragment();
            moreFragment = new MoreFragment();

            homeFragment.handleIntent(intent);
            initFragments();
        }
    }

    private void initFragments(){
        fm.beginTransaction()
                .replace(R.id.frame_layout, homeFragment)
                .add(R.id.frame_layout, historyFragment)
                .add(R.id.frame_layout, moreFragment)
                .hide(historyFragment)
                .hide(moreFragment)
                .commit();

        lastFragment = homeFragment;

        listeners = new ArrayList<>();
        listeners.add(homeFragment.listener);
        listeners.add(historyFragment.listener);
    }

    private void replaceFragment(Fragment f){
        fm.beginTransaction().hide(lastFragment).show(f).commit();
        lastFragment = f;
    }

    public void startDownloadService(ArrayList<Video> downloadQueue, IDownloaderListener awaitingListener){
        if(isDownloadServiceRunning){
            iDownloaderService.updateQueue(downloadQueue);
            return;
        }
        if(!listeners.contains(awaitingListener)) listeners.add(awaitingListener);
        Intent serviceIntent = new Intent(context, DownloaderService.class);
        serviceIntent.putParcelableArrayListExtra("queue", downloadQueue);
        context.getApplicationContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void stopDownloadService(){
        if(!isDownloadServiceRunning) return;
        iDownloaderService.removeActivity(this);
        context.getApplicationContext().unbindService(serviceConnection);
        downloaderService.stopForeground(true);
        downloaderService.stopSelf();
        isDownloadServiceRunning = false;
    }

    public void cancelDownloadService(){
        if(!isDownloadServiceRunning) return;
        iDownloaderService.cancelDownload(true);
        stopDownloadService();
    }

    public void removeItemFromDownloadQueue(Video video){
        iDownloaderService.removeItemFromDownloadQueue(video);
    }

    public boolean isDownloadServiceRunning() {
        ActivityManager.RunningServiceInfo service = getService(DownloaderService.class);
        if(service != null){
            isDownloadServiceRunning = true;
            return true;
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

    public void updateHistoryFragment(){
        historyFragment.initCards();
    }

}
