package com.deniscerri.ytdl.util;


import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.deniscerri.ytdl.App;
import com.deniscerri.ytdl.BuildConfig;
import com.deniscerri.ytdl.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yausername.youtubedl_android.YoutubeDL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class UpdateUtil {
    Context context;
    private final String TAG  = "UpdateUtil";
    private DownloadManager downloadManager;
    public static boolean updatingYTDL;
    public static boolean updatingApp;
    MaterialCardView materialCardView;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public UpdateUtil(Context context){
        this.context = context;
        downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public void updateApp(){
        if (updatingApp) {
            Toast.makeText(context, context.getString(R.string.ytdl_already_updating), Toast.LENGTH_LONG).show();
            return;
        }
        AtomicReference<JSONObject> res = new AtomicReference<>(new JSONObject());
        try{
            Thread thread = new Thread(() -> res.set(checkForAppUpdate()));
            thread.start();
            thread.join();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

        if(res.get() == null){
            Toast.makeText(context, R.string.error_checking_latest_version, Toast.LENGTH_SHORT).show();
        }

        String version = "";
        String body = "";
        try {
            version = res.get().getString("tag_name");
            body = res.get().getString("body");
        } catch (JSONException ignored) {}

        if(version.equals("v"+BuildConfig.VERSION_NAME)){
            Toast.makeText(context, R.string.you_are_in_latest_version, Toast.LENGTH_SHORT).show();
            return;
        }

        MaterialAlertDialogBuilder updateDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(version)
                .setMessage(body)
                .setIcon(R.drawable.ic_update_app)
                .setNegativeButton("Cancel", (dialogInterface, i) -> {})
                .setPositiveButton("Update", (dialogInterface, i) -> startAppUpdate(res.get()));
        updateDialog.show();
    }


    public JSONObject checkForAppUpdate(){
        String url = "https://api.github.com/repos/deniscerri/ytdlnis/releases/latest";

        BufferedReader reader;
        String line;
        StringBuilder responseContent = new StringBuilder();
        HttpURLConnection conn;
        JSONObject json = new JSONObject();

        try{
            URL req = new URL(url);
            conn = (HttpURLConnection) req.openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(5000);

            if(conn.getResponseCode() < 300){
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while((line = reader.readLine()) != null){
                    responseContent.append(line);
                }
                reader.close();

                json = new JSONObject(responseContent.toString());
                if(json.has("error")){
                    throw new Exception();
                }
            }
            conn.disconnect();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }




        return json;
    }

    private void startAppUpdate(JSONObject updateInfo){
        try{
            JSONArray versions = updateInfo.getJSONArray("assets");
            String url = "";
            String app_name = "";
            for (int i = 0; i < versions.length(); i++){
                JSONObject tmp = versions.getJSONObject(i);
                if(tmp.getString("name").contains(Build.SUPPORTED_ABIS[0])){
                    url = tmp.getString("browser_download_url");
                    app_name = tmp.getString("name");
                    break;
                }
            }

            if(url.isEmpty()){
                Toast.makeText(context, R.string.couldnt_find_apk, Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = Uri.parse(url);
            Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .mkdirs();

            downloadManager.enqueue(new DownloadManager.Request(uri)
                    .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI |
                            DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setTitle(context.getString(R.string.downloading_update))
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, app_name));

        }catch(Exception ignored){}
    }

    public void updateYoutubeDL() {
        if (updatingYTDL) {
            Toast.makeText(context, context.getString(R.string.ytdl_already_updating), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(context, context.getString(R.string.ytdl_updating_started), Toast.LENGTH_SHORT).show();

        updatingYTDL = true;
        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().updateYoutubeDL(context))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    switch (status) {
                        case DONE:
                            Toast.makeText(context, context.getString(R.string.ytld_update_success), Toast.LENGTH_LONG).show();
                            break;
                        case ALREADY_UP_TO_DATE:
                            Toast.makeText(context, context.getString(R.string.you_are_in_latest_version), Toast.LENGTH_LONG).show();
                            break;
                        default:
                            Toast.makeText(context, status.toString(), Toast.LENGTH_LONG).show();
                            break;
                    }
                    updatingYTDL = false;
                }, e -> {
                    if(BuildConfig.DEBUG) Log.e(TAG, context.getString(R.string.ytdl_update_failed), e);
                    Toast.makeText(context, context.getString(R.string.ytdl_update_failed), Toast.LENGTH_LONG).show();
                    updatingYTDL = false;
                });
        compositeDisposable.add(disposable);
    }


}
