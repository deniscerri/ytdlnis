package com.deniscerri.ytdlnis.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.deniscerri.ytdlnis.database.DBManager;
import com.deniscerri.ytdlnis.database.Video;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class InfoUtil {
    private static final String TAG = "API MANAGER";
    private static String countryCODE = "US";
    private ArrayList<Video> videos;
    private String key;
    private DBManager dbManager;

    public InfoUtil(Context context) {
        @Nullable ApplicationInfo applicationInfo;
        try{
            SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
            key = sharedPreferences.getString("api_key", "");
            applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            if (key.isEmpty()) key = applicationInfo.metaData.getString("ytAPIkey");
            Log.e(TAG, key);
            dbManager = new DBManager(context);

            Thread thread = new Thread(() -> {
                //get Locale
                JSONObject country = genericRequest("https://ipwho.is/");
                try{
                    countryCODE = country.getString("country_code");
                }catch(Exception ignored){}
            });
            thread.start();
            thread.join();

        }catch(Exception ignored){
            Toast.makeText(context, "Couldn't find API Key for the request", Toast.LENGTH_SHORT).show();
        }

        videos = new ArrayList<>();
    }

    public ArrayList<Video> search(String query) throws JSONException{
        if (key.isEmpty()) return getFromYTDL(query);

        videos = new ArrayList<>();
        //short data
        JSONObject res = genericRequest("https://youtube.googleapis.com/youtube/v3/search?part=snippet&q="+query+"&maxResults=25&regionCode="+countryCODE+"&key="+key);
        if (!res.has("items")) return getFromYTDL(query);
        JSONArray dataArray = res.getJSONArray("items");

        //extra data
        String url2 = "https://www.googleapis.com/youtube/v3/videos?id=";
        //getting all ids, for the extra data request
        for(int i = 0; i < dataArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");

            if(element.getJSONObject("id").getString("kind").equals("youtube#video")){
                String videoID = element.getJSONObject("id").getString("videoId");
                url2 = url2 + videoID + ",";
                snippet.put("videoID", videoID);
            }
        }
        url2 = url2.substring(0, url2.length()-1) + "&part=contentDetails&regionCode="+countryCODE+"&key="+key;
        JSONObject extra = genericRequest(url2);
        int j = 0;
        for(int i = 0; i < dataArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");
            if(element.getJSONObject("id").getString("kind").equals("youtube#video")){
                String duration = extra.getJSONArray("items").getJSONObject(j++).getJSONObject("contentDetails").getString("duration");
                duration = formatDuration(duration);
                if(duration.equals("0:00")){
                    continue;
                }

                snippet.put("duration", duration);
                snippet = fixThumbnail(snippet);
                Video v = createVideofromJSON(snippet);

                if(v.getThumb().isEmpty()){
                    continue;
                }
                videos.add(createVideofromJSON(snippet));
            }
        }


        return videos;
    }

    public PlaylistTuple getPlaylist(String id, String nextPageToken) throws JSONException{
        if (key.isEmpty()) return new PlaylistTuple("", getFromYTDL("https://www.youtube.com/playlist?list="+id));
        videos = new ArrayList<>();

        String url = "https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet&pageToken="+nextPageToken+"&maxResults=50&regionCode="+countryCODE+"&playlistId="+id+"&key="+key;
        //short data
        JSONObject res = genericRequest(url);
        if (!res.has("items")) return new PlaylistTuple("", getFromYTDL("https://www.youtube.com/playlist?list="+id));
        JSONArray dataArray = res.getJSONArray("items");

        //extra data
        String url2 = "https://www.googleapis.com/youtube/v3/videos?id=";
        //getting all ids, for the extra data request
        for(int i = 0; i < dataArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");
            String videoID = snippet.getJSONObject("resourceId").getString("videoId");
            url2 = url2 + videoID + ",";
            snippet.put("videoID", videoID);
        }
        url2 = url2.substring(0, url2.length()-1) + "&part=contentDetails&regionCode="+countryCODE+"&key="+key;
        JSONObject extra = genericRequest(url2);
        JSONArray extraArray = extra.getJSONArray("items");
        int j = 0;
        int i;
        for(i = 0; i < extraArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");
            String duration = extra.getJSONArray("items").getJSONObject(j).getJSONObject("contentDetails").getString("duration");
            duration = formatDuration(duration);
            snippet.put("duration", duration);
            snippet = fixThumbnail(snippet);
            Video v = createVideofromJSON(snippet);

            if(v.getThumb().isEmpty()){
                continue;
            }else{
                j++;
            }

            v.setIsPlaylistItem(1);
            videos.add(v);
        }
        String next = res.optString("nextPageToken");
        return new PlaylistTuple(next, videos);
    }


    public Video getVideo(String id) throws JSONException {
        if (key.isEmpty()) return getFromYTDL("https://www.youtube.com/watch?v="+id).get(0);

        //short data
        JSONObject res = genericRequest("https://youtube.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id="+id+"&key="+key);
        if (!res.has("items")) return getFromYTDL("https://www.youtube.com/watch?v="+id).get(0);
        String duration = res.getJSONArray("items").getJSONObject(0).getJSONObject("contentDetails").getString("duration");
        duration = formatDuration(duration);

        res = res.getJSONArray("items").getJSONObject(0).getJSONObject("snippet");

        res.put("videoID", id);
        res.put("duration", duration);
        res = fixThumbnail(res);

        Video v = createVideofromJSON(res);
        return v;
    }

    private Video createVideofromJSON(JSONObject obj){
        Video video = null;
        try{
            String id = obj.getString("videoID");

            String title = obj.getString("title");
            title = Html.fromHtml(title).toString();

            String author = obj.getString("channelTitle");
            author = Html.fromHtml(author).toString();

            String duration = obj.getString("duration");
            String thumb = obj.getString("thumb");
            String url = "https://www.youtube.com/watch?v=" + id;
            int downloadedAudio = dbManager.checkDownloaded(url, "audio");
            int downloadedVideo = dbManager.checkDownloaded(url, "video");
            int isPLaylist = 0;

            video = new Video(id, url, title, author, duration, thumb, downloadedAudio, downloadedVideo, isPLaylist, "youtube");
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }
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

    public ArrayList<Video> getFromYTDL(String query){
        videos = new ArrayList<>();
        try {
            YoutubeDLRequest request = new YoutubeDLRequest(query);
            request.addOption("--flat-playlist");
            request.addOption("-j");
            request.addOption("--skip-download");
            request.addOption("-R", "1");
            request.addOption("--socket-timeout", "5");
            if (!query.contains("http")) request.addOption("--default-search",  "ytsearch25");

            YoutubeDLResponse youtubeDLResponse = YoutubeDL.getInstance().execute(request);
            String[] results;
            try {
                results = youtubeDLResponse.getOut().split(System.getProperty("line.separator"));
            }catch(Exception e){
                results = new String[]{youtubeDLResponse.getOut()};
            }

            int isPlaylist = 0;
            JSONObject pl = new JSONObject(results[0]);
            if (pl.has("playlist")){
                if (! pl.getString("playlist").equals(query)) isPlaylist = 1;
            }

            for (String result : results) {
                JSONObject jsonObject = new JSONObject(result);
                if (jsonObject.getString("title").equals("[Private video]")) continue;

                String url = jsonObject.getString("webpage_url");
                String thumb = "";
                if (jsonObject.has("thumbnail")){
                    thumb = jsonObject.getString("thumbnail");
                }else {
                    JSONArray thumbs = jsonObject.getJSONArray("thumbnails");
                    thumb = thumbs.getJSONObject(thumbs.length()-1).getString("url");
                }

                String website = "";
                if (jsonObject.has("ie_key")) website = jsonObject.getString("ie_key");
                else website = jsonObject.getString("extractor");


                videos.add(new Video(
                        jsonObject.getString("id"),
                        url,
                        jsonObject.getString("title"),
                        jsonObject.getString("uploader"),
                        formatIntegerDuration(jsonObject.getInt("duration")),
                        thumb,
                        dbManager.checkDownloaded(url, "audio"),
                        dbManager.checkDownloaded(url, "video"),
                        isPlaylist,
                        website)
                );
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return videos;
    }

    public ArrayList<Video> getTrending(Context context) throws JSONException{
        if (key.isEmpty()) return new ArrayList<>();

        videos = new ArrayList<>();

        String url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&videoCategoryId=10&regionCode="+countryCODE+"&maxResults=25&key="+key;
        //short data
        JSONObject res = genericRequest(url);
        //extra data from the same videos
        JSONObject contentDetails = genericRequest("https://www.googleapis.com/youtube/v3/videos?part=contentDetails&chart=mostPopular&videoCategoryId=10&regionCode="+countryCODE+"&maxResults=25&key="+key);

        if(!contentDetails.has("items")) return new ArrayList<>();

        JSONArray dataArray = res.getJSONArray("items");
        JSONArray extraDataArray = contentDetails.getJSONArray("items");
        for(int i = 0; i < dataArray.length(); i++){
            JSONObject element = dataArray.getJSONObject(i);
            JSONObject snippet = element.getJSONObject("snippet");

            String duration = extraDataArray.getJSONObject(i).getJSONObject("contentDetails").getString("duration");
            duration = formatDuration(duration);

            snippet.put("videoID", element.getString("id"));
            snippet.put("duration", duration);
            snippet = fixThumbnail(snippet);

            Video v = createVideofromJSON(snippet);
            if(v.getThumb().isEmpty()){
                continue;
            }
            videos.add(v);
        }
        return videos;
    }

    public String formatDuration(String dur){
        if(dur.equals("P0D")){
            return "LIVE";
        }

        boolean hours = false;
        String duration = "";
        dur = dur.substring(2);
        if (dur.contains("H")) {
            hours = true;
            duration += String.format(Locale.getDefault(), "%02d", Integer.parseInt(dur.substring(0, dur.indexOf("H")))) + ":";
            dur = dur.substring(dur.indexOf("H")+1);
        }
        if (dur.contains("M")) {
            duration += String.format(Locale.getDefault(), "%02d", Integer.parseInt(dur.substring(0, dur.indexOf("M")))) + ":";
            dur = dur.substring(dur.indexOf("M")+1);
        }else if(hours) duration += "00:";
        if (dur.contains("S")){
            if(duration.isEmpty()) duration = "00:";
            duration += String.format(Locale.getDefault(), "%02d", Integer.parseInt(dur.substring(0, dur.indexOf("S"))));
        }else{
            duration += "00";
        }

        if(duration.equals("00:00")){
            duration = "";
        }

        return duration;
    }

    public String formatIntegerDuration(int dur){
        String format = String.format(Locale.getDefault(), "%02d:%02d:%02d", (dur/3600), ((dur % 3600)/60), (dur % 60));
        // 00:00:00
        if (dur < 600) format = format.substring(4);
        else if (dur < 3600) format = format.substring(3);
        else if (dur < 36000) format = format.substring(1);

        return format;
    }

//    public ArrayList<String> getSearchHints(String query){
//        String url = "https://google.com/complete/search?client=youtube&q="+query;
//        ArrayList<String> searchHints = new ArrayList<>();
//
//        BufferedReader reader;
//        String line;
//        StringBuilder responseContent = new StringBuilder();
//        HttpURLConnection conn;
//        JSONArray json;
//
//        try{
//            URL req = new URL(url);
//            conn = (HttpURLConnection) req.openConnection();
//
//            conn.setRequestMethod("GET");
//            conn.setConnectTimeout(10000);
//            conn.setReadTimeout(5000);
//
//            if(conn.getResponseCode() < 300){
//                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                while((line = reader.readLine()) != null){
//                    responseContent.append(line);
//                }
//                reader.close();
//                String content  = responseContent.substring(19, responseContent.length()-1);
//
//                json = new JSONArray(content);
//                JSONArray hints = (JSONArray) json.get(1);
//                for (int i = 0; i < hints.length(); i++){
//                    searchHints.add(hints.getJSONArray(i).get(0).toString());
//                }
//            }
//            conn.disconnect();
//        }catch(Exception e){
//            Log.e(TAG, e.toString());
//        }
//        return searchHints;
//    }

    public static class PlaylistTuple {
        String nextPageToken;
        ArrayList<Video> videos;

        PlaylistTuple(String token, ArrayList<Video> videos){
            nextPageToken = token;
            this.videos = videos;
        }

        public String getNextPageToken() {
            return nextPageToken;
        }

        public ArrayList<Video> getVideos() {
            return videos;
        }
    }

}

