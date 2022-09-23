package com.deniscerri.ytdlnis.page;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.util.UpdateUtil;

public class MoreFragment extends PreferenceFragmentCompat {
    Preference updateYTDL;
    Preference updateApp;
    public static final String TAG = "MoreFragment";
    private UpdateUtil updateUtil;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey);

        updateYTDL = findPreference("update_ytdl");
        updateApp = findPreference("update_app");

        updateUtil = new UpdateUtil(getContext());

        updateYTDL.setOnPreferenceClickListener(preference -> {
            updateUtil.updateYoutubeDL();
            return true;
        });

        updateApp.setOnPreferenceClickListener(preference -> {
            if(!updateUtil.updateApp()){
                Toast.makeText(getContext(), R.string.you_are_in_latest_version, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }
}