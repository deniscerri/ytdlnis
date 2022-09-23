package com.deniscerri.ytdlnis.page.settings;

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
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import com.deniscerri.ytdlnis.BuildConfig;
import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.util.UpdateUtil;

import java.nio.charset.StandardCharsets;

public class SettingsFragment extends PreferenceFragmentCompat {

    Preference musicPath;
    Preference videoPath;
    Preference commandPath;

    SeekBarPreference concurrentFragments;
    EditTextPreference limitRate;
    SwitchPreferenceCompat aria2;

    SwitchPreferenceCompat removeNonMusic;
    SwitchPreferenceCompat embedSubtitles;
    SwitchPreferenceCompat embedThumbnail;
    SwitchPreferenceCompat addChapters;
    SwitchPreferenceCompat writeThumbnail;
    ListPreference audioFormat;
    ListPreference videoFormat;
    SeekBarPreference audioQuality;

    Preference version;

    private UpdateUtil updateUtil;

    public static final int MUSIC_PATH_CODE = 33333;
    public static final int VIDEO_PATH_CODE = 55555;
    public static final int COMMAND_PATH_CODE = 77777;
    private static final String TAG = "SettingsFragment";


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        updateUtil = new UpdateUtil(requireContext());

        initPreferences();
        initListeners();
    }

    private void initPreferences(){
        SharedPreferences preferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        musicPath = findPreference("music_path");
        videoPath = findPreference("video_path");
        commandPath = findPreference("command_path");

        concurrentFragments = findPreference("concurrent_fragments");
        limitRate = findPreference("limit_rate");
        aria2 = findPreference("aria2");

        removeNonMusic = findPreference("remove_non_music");
        embedSubtitles = findPreference("embed_subtitles");
        embedThumbnail = findPreference("embed_thumbnail");
        addChapters = findPreference("add_chapters");
        writeThumbnail = findPreference("write_thumbnail");
        audioFormat = findPreference("audio_format");
        videoFormat = findPreference("video_format");
        audioQuality = findPreference("audio_quality");

        version = findPreference("version");
        version.setSummary(BuildConfig.VERSION_NAME);

        if (preferences.getString("music_path", "").isEmpty()){
            editor.putString("music_path", getString(R.string.music_path));
        }
        if (preferences.getString("video_path", "").isEmpty()){
            editor.putString("video_path", getString(R.string.video_path));
        }
        if (preferences.getString("command_path", "").isEmpty()){
            editor.putString("command_path", getString(R.string.command_path));
        }

        editor.putInt("concurrent_fragments", concurrentFragments.getValue());
        editor.putString("limit_rate", limitRate.getText());
        editor.putBoolean("aria2", aria2.isChecked());
        editor.putBoolean("remove_non_music", removeNonMusic.isChecked());
        editor.putBoolean("embed_subtitles", embedSubtitles.isChecked());
        editor.putBoolean("embed_thumbnail", embedThumbnail.isChecked());
        editor.putBoolean("add_chapters", addChapters.isChecked());
        editor.putBoolean("write_thumbnail", writeThumbnail.isChecked());
        editor.putString("audio_format", audioFormat.getValue());
        editor.putString("video_format", videoFormat.getValue());
        editor.putInt("audio_quality", audioQuality.getValue());

        editor.apply();
    }

    private void initListeners(){
        SharedPreferences preferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        musicPath.setSummary(preferences.getString("music_path", ""));
        musicPath.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            musicPathResultLauncher.launch(intent);
            return true;
        });

        videoPath.setSummary(preferences.getString("video_path", ""));
        videoPath.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            videoPathResultLauncher.launch(intent);
            return true;
        });

        commandPath.setSummary(preferences.getString("command_path", ""));
        commandPath.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            commandPathResultLauncher.launch(intent);
            return true;
        });

        concurrentFragments.setOnPreferenceChangeListener((preference, newValue) -> {
            int value = Integer.parseInt(String.valueOf(newValue));
            editor.putInt("concurrent_fragments", value);
            editor.apply();
            return true;
        });

        limitRate.setOnPreferenceChangeListener((preference, newValue) -> {
            editor.putString("limit_rate", String.valueOf(newValue));
            editor.apply();
            return true;
        });

        aria2.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enable = (Boolean) newValue;
            editor.putBoolean("aria2", enable);
            editor.apply();
            return true;
        });

        removeNonMusic.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean remove = (Boolean) newValue;
            editor.putBoolean("remove_non_music", remove);
            editor.apply();
            return true;
        });

        embedSubtitles.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean embed = (Boolean) newValue;
            editor.putBoolean("embed_subtitles", embed);
            editor.apply();
            return true;
        });

        embedThumbnail.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean embed = (Boolean) newValue;
            editor.putBoolean("embed_thumbnail", embed);
            editor.apply();
            return true;
        });

        addChapters.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean add = (Boolean) newValue;
            editor.putBoolean("add_chapters", add);
            editor.apply();
            return true;
        });

        writeThumbnail.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean write = (Boolean) newValue;
            editor.putBoolean("write_thumbnail", write);
            editor.apply();
            return true;
        });

        audioFormat.setSummary(preferences.getString("audio_format", ""));
        audioFormat.setOnPreferenceChangeListener((preference, newValue) -> {
            editor.putString("audio_format", String.valueOf(newValue));
            audioFormat.setSummary(String.valueOf(newValue));
            editor.apply();
            return true;
        });

        videoFormat.setSummary(preferences.getString("video_format", ""));
        videoFormat.setOnPreferenceChangeListener((preference, newValue) -> {
            editor.putString("video_format", String.valueOf(newValue));
            videoFormat.setSummary(String.valueOf(newValue));
            editor.apply();
            return true;
        });

        audioQuality.setOnPreferenceChangeListener((preference, newValue) -> {
            editor.putInt("audio_format", Integer.parseInt(String.valueOf(newValue)));
            editor.apply();
            return true;
        });

        version.setOnPreferenceClickListener(preference -> {
            if(!updateUtil.updateApp()){
                Toast.makeText(getContext(), R.string.you_are_in_latest_version, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

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

    ActivityResultLauncher<Intent> commandPathResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        changePath(commandPath, result.getData(), COMMAND_PATH_CODE);
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

        Log.e(TAG, path.toString());
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
            case COMMAND_PATH_CODE:
                editor.putString("command_path", path.toString());
                break;
        }
        editor.apply();
    }
}