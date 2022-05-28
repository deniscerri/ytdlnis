package com.deniscerri.ytdl;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.WindowManager;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ComplexColorCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.deniscerri.ytdl.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            this.setTheme(R.style.AppTheme);

            ColorStateList list = new ColorStateList(
              new int[][]{
                new int[]{}
              },
              new int[]{
                   ContextCompat.getColor(getApplicationContext(), R.color.material_dynamic_primary50)
               }
            );
            binding.bottomNavigationView.setItemActiveIndicatorColor(list);
            binding.bottomNavigationView.setItemRippleColor(list);
            binding.bottomNavigationView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.material_dynamic_neutral10));
        }

        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);


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
