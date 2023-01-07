package com.deniscerri.ytdlnis.page.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;


import com.deniscerri.ytdlnis.R;
import com.google.android.material.appbar.MaterialToolbar;


public class SettingsActivity extends AppCompatActivity{

        private FragmentManager fm;
        private MaterialToolbar topAppBar;
        Context context;

        @Override
        public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_settings);
                context = getBaseContext();
                topAppBar = findViewById(R.id.settings_toolbar);
                topAppBar.setNavigationOnClickListener(view -> onBackPressed());
                fm = getSupportFragmentManager();
                fm.beginTransaction()
                        .replace(R.id.settings_frame_layout, new SettingsFragment())
                        .commit();

        }

        @Override
        public void onBackPressed() {
                super.onBackPressed();
        }
}
