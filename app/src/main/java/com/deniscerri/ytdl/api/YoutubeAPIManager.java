package com.deniscerri.ytdl.api;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.widget.Toast;

import com.deniscerri.ytdl.database.Video;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;


public class YoutubeAPIManager {
    private static final String TAG = "API MANAGER";
    private ArrayList<Video> videos;
    private String key;
    Properties properties = new Properties();

    public YoutubeAPIManager(Context context) {
        AssetManager assetManager = context.getAssets();
        try{
            InputStream inputStream = assetManager.open("config.properties");
            properties.load(inputStream);
            key = properties.getProperty("ytAPI");
        }catch(Exception ignored){
            Toast.makeText(context, "Couldn't find API Key for the request", Toast.LENGTH_SHORT).show();
        }

        videos = new ArrayList<>();
    }

    public ArrayList<Video> search(String query) throws JSONException{
        JSONObject res = genericRequest("https://youtube.googleapis.com/youtube/v3/search?part=snippet&q="+query+"&maxResults=25&regionCode=US&key="+key);
        JSONArray dataArray = res.getJSONArray("items");
        for(int i = 0; i < dataArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");
            if(element.getJSONObject("id").getString("kind").equals("youtube#video")){
                snippet.put("videoID", element.getJSONObject("id").getString("videoId"));
                snippet = fixThumbnail(snippet);
                Video v = createVideofromJSON(snippet);
                v.setTitle(v.getTitle().replace("&amp;", "&").replace("&quot;", "\""));
                v.setAuthor(v.getAuthor().replace("&amp;", "&").replace("&quot;", "\""));
                videos.add(createVideofromJSON(snippet));
            }
        }
        return videos;
    }

    public ArrayList<Video> getPlaylist(String id, String nextPageToken) throws JSONException{
        String url = "https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet&pageToken="+nextPageToken+"&maxResults=50&playlistId="+id+"&key="+key;
        JSONObject res = genericRequest(url);
        JSONArray dataArray = res.getJSONArray("items");

        for(int i = 0; i < dataArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");
            if(snippet.getJSONObject("resourceId").getString("kind").equals("youtube#video")){
                snippet.put("videoID", snippet.getJSONObject("resourceId").getString("videoId"));
                snippet = fixThumbnail(snippet);
                Video v = createVideofromJSON(snippet);

                if(v.getThumb().isEmpty()){
                    continue;
                }

                v.setIsPlaylistItem(1);
                v.setTitle(v.getTitle().replace("&amp;", "&").replace("&quot;", "\""));
                v.setAuthor(v.getAuthor().replace("&amp;", "&").replace("&quot;", "\""));

                videos.add(v);

            }
        }
        String next = res.optString("nextPageToken");
        if(next != ""){
            ArrayList<Video> nextPage = getPlaylist(id,next);
        }

        return videos;
    }


    public Video getVideo(String id) throws JSONException {
        JSONObject res = genericRequest("https://youtube.googleapis.com/youtube/v3/videos?part=snippet&id="+id+"&key="+key);
        res = res.getJSONObject("snippet");
        res.put("videoID", res.getString("id"));
        res = fixThumbnail(res);

        Video v = createVideofromJSON(res);
        v.setTitle(v.getTitle().replace("&amp;", "&").replace("&quot;", "\""));
        v.setAuthor(v.getAuthor().replace("&amp;", "&").replace("&quot;", "\""));


        return v;
    }

    private Video createVideofromJSON(JSONObject obj){
        Video video = null;
        try{
            String id = obj.getString("videoID");
            String title = obj.getString("title");
            String author = obj.getString("channelTitle");
            String thumb = obj.getString("thumb");

            video = new Video(id, title, author, thumb);
        }catch(Exception ignored){}

        return video;
    }

    private JSONObject genericRequest(String url){
        Log.e(TAG, url);

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

                json = new JSONObject(responseContent.toString());
                if(json.has("error")){
                    throw new Exception();
                }
            }
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }




        return json;
    }

    private JSONObject fixThumbnail(JSONObject o){
        String imageURL = "";
        try{
            JSONObject thumbs = o.getJSONObject("thumbnails");
            imageURL = thumbs.getJSONObject("maxres").getString("url");
        }catch(Exception e){
            try {
                JSONObject thumbs = o.getJSONObject("thumbnails");
                imageURL = thumbs.getJSONObject("high").getString("url");
            }catch(Exception u){
                try{
                    JSONObject thumbs = o.getJSONObject("thumbnails");
                    imageURL = thumbs.getJSONObject("default").getString("url");
                }catch(Exception ignored){}
            }

        }

        try{
            o.put("thumb", imageURL);
        }catch(Exception ignored){}


        return o;
    }


}
