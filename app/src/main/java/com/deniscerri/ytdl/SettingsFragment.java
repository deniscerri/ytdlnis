package com.deniscerri.ytdl;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.yausername.youtubedl_android.YoutubeDL;
import java.nio.charset.StandardCharsets;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SettingsFragment extends PreferenceFragmentCompat {

    Preference musicPath;
    Preference videoPath;
    Preference update;
    public static final int MUSIC_PATH_CODE = 33333;
    public static final int VIDEO_PATH_CODE = 55555;
    public static boolean updating;

    private static final String TAG = "SettingsFragment";
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        musicPath = findPreference("music_path");
        videoPath = findPreference("video_path");

        SharedPreferences preferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        String music_path = preferences.getString("music_path", "");
        String video_path = preferences.getString("video_path", "");

        if(music_path.isEmpty()){
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("music_path", "/storage/emulated/0/Music/");
            editor.apply();
            music_path = preferences.getString("music_path", "");
        }
        musicPath.setSummary(music_path);
        musicPath.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            musicPathResultLauncher.launch(intent);
            return true;
        });


        if(video_path.isEmpty()){
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("video_path", "/storage/emulated/0/Movies/");
            editor.apply();
            video_path = preferences.getString("video_path", "");
        }
        videoPath.setSummary(video_path);
        videoPath.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            videoPathResultLauncher.launch(intent);
            return true;
        });

        update = findPreference("update");
        if(update != null){
            update.setOnPreferenceClickListener(preference -> {
                updateYoutubeDL();
                return true;
            });
        }


    }

    ActivityResultLauncher<Intent> musicPathResultLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    changePath(musicPath, result.getData(), MUSIC_PATH_CODE);
                }
            }
    });

    ActivityResultLauncher<Intent> videoPathResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        changePath(videoPath, result.getData(), VIDEO_PATH_CODE);
                    }
                }
            });

    public void changePath(Preference p, Intent data, int requestCode){
        Log.e("TEST", data.toUri(0));
        String dataValue = data.getData().toString();
        dataValue = dataValue.replace("content://com.android.externalstorage.documents/tree/", "");
        dataValue = dataValue.replaceAll("%3A", "/");

        try{
            dataValue = java.net.URLDecoder.decode(dataValue, StandardCharsets.UTF_8.name());
        }catch(Exception ignored){}

        String[] pieces = dataValue.split("/");

        int index = 1;
        StringBuilder path = new StringBuilder("/storage/");
        if(pieces[0].equals("primary")){
            path.append("emulated/0/");
        }else{
            path.append(pieces[0]).append("/");
        }

        for(int i = index; i < pieces.length; i++){
            path.append(pieces[i]).append("/");
        }

        Log.e("TEST", path.toString());
        p.setSummary(path.toString());
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        switch (requestCode){
            case MUSIC_PATH_CODE:
                editor.putString("music_path", path.toString());
                break;
            case VIDEO_PATH_CODE:
                editor.putString("video_path", path.toString());
                break;
        }
        editor.apply();
    }

    private void updateYoutubeDL() {
        if (updating) {
            Toast.makeText(getContext(), getString(R.string.ytdl_already_updating), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), getString(R.string.ytdl_updating_started), Toast.LENGTH_SHORT).show();

        updating = true;
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
                    updating = false;
                }, e -> {
                    if(BuildConfig.DEBUG) Log.e(TAG, getString(R.string.ytdl_update_failed), e);
                    Toast.makeText(getContext(), getString(R.string.ytdl_update_failed), Toast.LENGTH_LONG).show();
                    updating = false;
                });
        compositeDisposable.add(disposable);
    }


}