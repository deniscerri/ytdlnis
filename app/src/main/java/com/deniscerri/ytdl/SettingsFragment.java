package com.deniscerri.ytdl;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.yausername.youtubedl_android.YoutubeDL;

import java.nio.ByteBuffer;
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
    private CompositeDisposable compositeDisposable = new CompositeDisposable();


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        musicPath = (Preference) findPreference("music_path");
        videoPath = (Preference) findPreference("video_path");

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
            startActivityForResult(intent, MUSIC_PATH_CODE);
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
            startActivityForResult(intent, VIDEO_PATH_CODE);
            return true;
        });

        update = (Preference) findPreference("update");
        if(update != null){
            update.setOnPreferenceClickListener(preference -> {
                updateYoutubeDL();
                return true;
            });
        }


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case MUSIC_PATH_CODE:
                if(resultCode == Activity.RESULT_OK){
                    changePath(musicPath, data, requestCode);
                }
                break;
            case VIDEO_PATH_CODE:
                if(resultCode == Activity.RESULT_OK){
                    changePath(videoPath, data, requestCode);
                }
                break;
        }

    }

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
        String path = "/storage/";
        if(pieces[0].equals("primary")){
            path+="emulated/0/";
        }else{
            path+=pieces[0]+"/";
        }

        for(int i = index; i < pieces.length; i++){
            path = path + pieces[i] + "/";
        }

        Log.e("TEST", path);
        p.setSummary(path);
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        switch (requestCode){
            case MUSIC_PATH_CODE:
                editor.putString("music_path", path);
                break;
            case VIDEO_PATH_CODE:
                editor.putString("video_path", path);
                break;
        }
        editor.apply();
    }

    private void updateYoutubeDL() {
        if (updating) {
            Toast.makeText(getContext(), "Perditesimi eshte kerkuar nje here!", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), "Filloi Perditesimi", Toast.LENGTH_SHORT).show();

        updating = true;
        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().updateYoutubeDL(getContext()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    switch (status) {
                        case DONE:
                            Toast.makeText(getContext(), "Perditesimi u krye me sukses!", Toast.LENGTH_LONG).show();
                            break;
                        case ALREADY_UP_TO_DATE:
                            Toast.makeText(getContext(), "Je ne versionin e fundit", Toast.LENGTH_LONG).show();
                            break;
                        default:
                            Toast.makeText(getContext(), status.toString(), Toast.LENGTH_LONG).show();
                            break;
                    }
                    updating = false;
                }, e -> {
                    if(BuildConfig.DEBUG) Log.e(TAG, "Deshtim ne perditesim", e);
                    Toast.makeText(getContext(), "Deshtim ne perditesim", Toast.LENGTH_LONG).show();
                    updating = false;
                });
        compositeDisposable.add(disposable);
    }


}