package com.deniscerri.ytdl;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.deniscerri.ytdl.databinding.ActivityMainBinding;
import com.deniscerri.ytdl.page.HistoryFragment;
import com.deniscerri.ytdl.page.HomeFragment;
import com.deniscerri.ytdl.page.MoreFragment;
import com.deniscerri.ytdl.page.MusicEditorFragment;
import com.deniscerri.ytdl.page.settings.SettingsActivity;


public class MainActivity extends AppCompatActivity{

    ActivityMainBinding binding;
    Context context;

    private static final String TAG = "MainActivity";

    private HomeFragment homeFragment;
    private HistoryFragment historyFragment;
    private MusicEditorFragment musicEditorFragment;
    private MoreFragment moreFragment;

    private Fragment lastFragment;
    private FragmentManager fm;

    private boolean isDownloadServiceRunning = false;
    public DownloaderService downloaderService;

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
    protected void onCreate(Bundle savedInstanceState) {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setContentView(binding.getRoot());

        context = getBaseContext();

        fm = getSupportFragmentManager();

        homeFragment = new HomeFragment();
        historyFragment = new HistoryFragment();
        moreFragment = new MoreFragment();
        musicEditorFragment = new MusicEditorFragment();

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
            }else if(id == R.id.history){
                if(lastFragment == historyFragment){
                    historyFragment.scrollToTop();
                }else {
                    this.setTitle(getString(R.string.history));
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
            }else if(id == R.id.music_editor){
                replaceFragment(musicEditorFragment);
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

            homeFragment.handleIntent(intent);
            initFragments();
        }
    }

    private void initFragments(){
        fm.beginTransaction()
                .replace(R.id.frame_layout, homeFragment)
                .add(R.id.frame_layout, historyFragment)
                .add(R.id.frame_layout, moreFragment)
                .add(R.id.frame_layout, musicEditorFragment)
                .hide(historyFragment)
                .hide(moreFragment)
                .hide(musicEditorFragment)
                .commit();

        lastFragment = homeFragment;
    }

    private void replaceFragment(Fragment f){
        fm.beginTransaction().hide(lastFragment).show(f).commit();
        lastFragment = f;
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
