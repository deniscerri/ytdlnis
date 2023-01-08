package com.deniscerri.ytdlnis.ui;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import com.deniscerri.ytdlnis.R;

public class MoreFragment extends PreferenceFragmentCompat {
    public static final String TAG = "MoreFragment";
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey);
    }
}