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
    MaterialCardView materialCardView;

    public static boolean updatingYTDL;
    public static boolean updatingApp;
    public static final String TAG = "MoreFragment";
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey);

        updateYTDL = findPreference("update_ytdl");
        updateApp = findPreference("update_app");

        updateYTDL.setOnPreferenceClickListener(preference -> {
            updateYoutubeDL();
            return true;
        });

        updateApp.setOnPreferenceClickListener(preference -> {
            updateApp();
            return true;
        });
    }



    private void updateYoutubeDL() {
        if (updatingYTDL) {
            Toast.makeText(getContext(), getString(R.string.ytdl_already_updating), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), getString(R.string.ytdl_updating_started), Toast.LENGTH_SHORT).show();

        updatingYTDL = true;
        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().updateYoutubeDL(getContext()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    switch (status) {
                        case DONE:
                            Toast.makeText(getContext(), getString(R.string.ytld_update_success), Toast.LENGTH_LONG).show();
                            break;
                        case ALREADY_UP_TO_DATE:
                            Toast.makeText(getContext(), getString(R.string.you_are_in_latest_version), Toast.LENGTH_LONG).show();
                            break;
                        default:
                            Toast.makeText(getContext(), status.toString(), Toast.LENGTH_LONG).show();
                            break;
                    }
                    updatingYTDL = false;
                }, e -> {
                    if(BuildConfig.DEBUG) Log.e(TAG, getString(R.string.ytdl_update_failed), e);
                    Toast.makeText(getContext(), getString(R.string.ytdl_update_failed), Toast.LENGTH_LONG).show();
                    updatingYTDL = false;
                });
        compositeDisposable.add(disposable);
    }


    private void updateApp(){
        if (updatingApp) {
            Toast.makeText(getContext(), getString(R.string.ytdl_already_updating), Toast.LENGTH_LONG).show();
            return;
        }

        UpdateUtil updateUtil = new UpdateUtil(getContext());
        AtomicReference<JSONObject> res = new AtomicReference<>(new JSONObject());

        try{
            Thread thread = new Thread(() -> res.set(updateUtil.checkForAppUpdate()));
            thread.start();
            thread.join();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

        if(res.get() == null){
            Toast.makeText(getContext(), R.string.error_checking_latest_version, Toast.LENGTH_SHORT).show();
        }

        String version = "";
        String body = "";
        try {
            version = res.get().getString("tag_name");
            body = res.get().getString("body");
        } catch (JSONException ignored) {}

        if(version.equals("v"+BuildConfig.VERSION_NAME)){
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), R.string.you_are_running_latest_version, Toast.LENGTH_SHORT).show());
            return;
        }

        MaterialAlertDialogBuilder updateDialog = new MaterialAlertDialogBuilder(getContext())
            .setTitle(version)
            .setMessage(body)
            .setIcon(R.drawable.ic_update_app)
            .setNegativeButton("Cancel", (dialogInterface, i) -> {})
            .setPositiveButton("Update", (dialogInterface, i) -> updateUtil.updateApp(res.get()));
        updateDialog.show();
    }


}