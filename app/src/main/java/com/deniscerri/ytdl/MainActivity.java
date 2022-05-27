package com.deniscerri.ytdl;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        homeFragment = new HomeFragment();
        historyFragment = new HistoryFragment();
        settingsFragment = new SettingsFragment();


        replaceFragment(homeFragment);


        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            switch(item.getItemId()){
                case R.id.home:
                    replaceFragment(homeFragment);
                    break;
                case R.id.history:
                    replaceFragment(historyFragment);
                    break;
                case R.id.settings:
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
        Log.e(TAG, action);
        if(Intent.ACTION_SEND.equals(action)){
            homeFragment.handleIntent(intent);
        }
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    private void replaceFragment(Fragment f){
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.frame_layout, f);
        fragmentTransaction.commit();

    }
}
