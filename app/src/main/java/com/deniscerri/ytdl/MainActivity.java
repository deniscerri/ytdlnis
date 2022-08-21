package com.deniscerri.ytdl;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.deniscerri.ytdl.databinding.ActivityMainBinding;
import io.reactivex.disposables.CompositeDisposable;



public class MainActivity extends AppCompatActivity{

    ActivityMainBinding binding;

    private static final String TAG = "MainActivity";
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private HomeFragment homeFragment;
    private HistoryFragment historyFragment;
    private SettingsFragment settingsFragment;
    private Fragment lastFragment;
    private FragmentManager fm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setContentView(binding.getRoot());

        fm = getSupportFragmentManager();

        homeFragment = new HomeFragment();
        historyFragment = new HistoryFragment();
        settingsFragment = new SettingsFragment();

        fm.beginTransaction()
                .replace(R.id.frame_layout, homeFragment)
                .add(R.id.frame_layout, historyFragment)
                .add(R.id.frame_layout, settingsFragment)
                .hide(historyFragment)
                .hide(settingsFragment)
                .commit();

        lastFragment = homeFragment;

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            switch(item.getItemId()){
                case R.id.home:
                    if(lastFragment == homeFragment){
                        homeFragment.scrollToTop();
                    }else{
                        this.setTitle(R.string.app_name);;
                    }
                    replaceFragment(homeFragment);
                    break;
                case R.id.history:
                    if(lastFragment == historyFragment){
                        historyFragment.scrollToTop();
                    }else {
                        this.setTitle(getString(R.string.history));
                    }
                    replaceFragment(historyFragment);
                    break;
                case R.id.settings:
                    this.setTitle(getString(R.string.settings));;
                    replaceFragment(settingsFragment);
                    break;

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
            settingsFragment = new SettingsFragment();

            homeFragment.handleIntent(intent);
            fm.beginTransaction()
                    .replace(R.id.frame_layout, homeFragment)
                    .add(R.id.frame_layout, historyFragment)
                    .add(R.id.frame_layout, settingsFragment)
                    .hide(historyFragment)
                    .hide(settingsFragment)
                    .commit();

            lastFragment = homeFragment;
        }
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    private void replaceFragment(Fragment f){
        fm.beginTransaction().hide(lastFragment).show(f).commit();
        lastFragment = f;
    }

}
