package com.deniscerri.ytdl.page;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.deniscerri.ytdl.BuildConfig;
import com.deniscerri.ytdl.R;
import com.deniscerri.ytdl.util.UpdateUtil;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yausername.youtubedl_android.YoutubeDL;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

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