package com.deniscerri.ytdl.util;


import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.deniscerri.ytdl.App;
import com.deniscerri.ytdl.R;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateUtil {
    Context context;
    private final String TAG  = "UpdateUtil";
    private DownloadManager downloadManager;

    public UpdateUtil(Context context){
        this.context = context;
        downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
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

    public void updateApp(JSONObject updateInfo){
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


}
