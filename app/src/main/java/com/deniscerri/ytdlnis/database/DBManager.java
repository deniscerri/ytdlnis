package com.deniscerri.ytdlnis.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.util.Log;

import com.deniscerri.ytdlnis.R;

import java.io.File;
import java.util.ArrayList;

public class DBManager extends SQLiteOpenHelper {

    public static final String db_name = "ytdlnis_db";
    public static final int db_version = 10;
    public static final String results_table_name = "results";
    public static final String history_table_name = "history";
    public static final String id = "id";
    public static final String videoId = "videoId";
    public static final String url = "url";
    public static final String title = "title";
    public static final String author = "author";
    public static final String duration = "duration";
    public static final String thumb = "thumb";
    public static final String downloadedAudio = "downloadedAudio";
    public static final String downloadedVideo = "downloadedVideo";
    public static final String type = "type";
    public static final String time = "time";
    public static final String isPlaylistItem = "isPlaylistItem";
    public static final String website = "website";
    public static final String downloadPath = "downloadPath";
    public static final String downloadingAudio = "downloadingAudio";
    public static final String downloadingVideo = "downloadingVideo";
    public static final String playlistTitle = "playlistTitle";


    public DBManager(Context context){
        super(context, db_name, null, db_version);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String query = "CREATE TABLE " + results_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + url + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + duration + " TEXT,"
                + thumb + " TEXT,"
                + downloadedAudio + " INTEGER,"
                + downloadedVideo + " INTEGER,"
                + isPlaylistItem + " INTENGER,"
                + website + " TEXT,"
                + downloadingAudio + " INTEGER,"
                + downloadingVideo + " INTEGER,"
                + playlistTitle + " TEXT)";

        sqLiteDatabase.execSQL(query);

        query = "CREATE TABLE " + history_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + url + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + duration + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT,"
                + downloadPath + " TEXT,"
                + website + " TEXT)";

        sqLiteDatabase.execSQL(query);
    }

    public void clearHistory(){
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + history_table_name);

        //remove downloaded statuses from results
        db.execSQL("UPDATE "+results_table_name+ " SET downloadedAudio=0, downloadedVideo=0" +
                " WHERE downloadedAudio=1 OR downloadedVideo=1");
    }

    public void clearHistoryItem(Video video){
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + history_table_name + " WHERE id=" + video.getId());

        String where = "";
        switch(video.getDownloadedType()){
            case "audio":
                where = " SET downloadedAudio=0 WHERE downloadedAudio=1 AND videoId='" + video.getVideoId()+"'";
                break;
            case "video":
                where = " SET downloadedVideo=0 WHERE downloadedVideo=1 AND videoId='" + video.getVideoId()+"'";
                break;
        }

        //remove downloaded status from results
        db.execSQL("UPDATE "+results_table_name+ where);
    }

    public void clearDeletedHistory(){
        ArrayList<Video> videos = getHistory("","","","");
        for (int i = 0; i < videos.size(); i++){
            Video video = videos.get(i);
            String path = video.getDownloadPath();
            File file = new File(path);
            if(!file.exists() && !path.isEmpty()){
                clearHistoryItem(video);
            }
        }
    }

    public void clearDuplicateHistory(){
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(
                "DELETE FROM " + history_table_name +
                " WHERE id > (SELECT MIN(h.id) FROM history h WHERE h.url = " + history_table_name + ".url" +
                " AND h.type = " + history_table_name + ".type);"
        );

        //remove downloaded statuses from results
        db.execSQL("UPDATE "+results_table_name+ " SET downloadedAudio=0, downloadedVideo=0" +
                " WHERE downloadedAudio=1 OR downloadedVideo=1");
    }

    public void clearResults(){
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + results_table_name);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVer, int newVer) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + results_table_name);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + history_table_name);
        onCreate(sqLiteDatabase);
    }


    @SuppressLint("Range")
    public ArrayList<Video> getResults(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + results_table_name, null);
        ArrayList<Video> list = new ArrayList<>();

        if(cursor.moveToFirst()){
            do {
                // on below line we are adding the data from cursor to our array list.
                list.add(new Video(cursor.getString(cursor.getColumnIndex(videoId)),
                        cursor.getString(cursor.getColumnIndex(url)),
                        cursor.getString(cursor.getColumnIndex(title)),
                        cursor.getString(cursor.getColumnIndex(author)),
                        cursor.getString(cursor.getColumnIndex(duration)),
                        cursor.getString(cursor.getColumnIndex(thumb)),
                        cursor.getInt(cursor.getColumnIndex(downloadedAudio)),
                        cursor.getInt(cursor.getColumnIndex(downloadedVideo)),
                        cursor.getInt(cursor.getColumnIndex(isPlaylistItem)),
                        cursor.getString(cursor.getColumnIndex(website)),
                        cursor.getInt(cursor.getColumnIndex(downloadedAudio)),
                        cursor.getInt(cursor.getColumnIndex(downloadingVideo)),
                        cursor.getString(cursor.getColumnIndex(playlistTitle))));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    @SuppressLint("Range")
    public ArrayList<Video> getHistory(String query, String format, String site, String sort){
        SQLiteDatabase db = this.getReadableDatabase();

        if (sort == null || sort.isEmpty()) sort = "DESC";
        Cursor cursor = db.rawQuery("SELECT * FROM " + history_table_name
                        + " WHERE title LIKE '%"+query+"%'"+
                        " AND type LIKE '%"+format+"%'"+
                        " AND website LIKE '%"+site+"%'"+
                        " ORDER BY id "+sort,
                null);
        ArrayList<Video> list = new ArrayList<>();

        if(cursor.moveToFirst()){
            do {
                // on below line we are adding the data from cursor to our array list.
                list.add(new Video(cursor.getInt(cursor.getColumnIndex(id)),
                        cursor.getString(cursor.getColumnIndex(url)),
                        cursor.getString(cursor.getColumnIndex(title)),
                        cursor.getString(cursor.getColumnIndex(author)),
                        cursor.getString(cursor.getColumnIndex(duration)),
                        cursor.getString(cursor.getColumnIndex(thumb)),
                        cursor.getString(cursor.getColumnIndex(type)),
                        cursor.getString(cursor.getColumnIndex(time)),
                        cursor.getString(cursor.getColumnIndex(downloadPath)),
                        cursor.getString(cursor.getColumnIndex(website))));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    public void addToResults(ArrayList<Video> videot){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        for(Video v : videot){
            values.put(videoId, v.getVideoId());
            values.put(url, v.getURL());
            values.put(title, v.getTitle());
            values.put(author, v.getAuthor());
            values.put(duration, v.getDuration());
            values.put(thumb, v.getThumb());
            values.put(downloadedAudio, v.isAudioDownloaded());
            values.put(downloadedVideo, v.isVideoDownloaded());
            values.put(isPlaylistItem, v.getIsPlaylistItem());
            values.put(website, v.getWebsite());
            values.put(downloadingAudio, 0);
            values.put(downloadingVideo, 0);
            values.put(playlistTitle, v.getPlaylistTitle());

            db.insert(results_table_name, null, values);
        }

        db.close();
    }

    public void addToHistory(Video v){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(url, v.getURL());
        values.put(title, v.getTitle());
        values.put(author, v.getAuthor());
        values.put(duration, v.getDuration());
        values.put(thumb, v.getThumb());
        values.put(type, v.getDownloadedType());
        values.put(time, v.getDownloadedTime());
        values.put(downloadPath, v.getDownloadPath());
        values.put(website, v.getWebsite());

        db.insert(history_table_name, null, values);
        db.close();
    }

    public void updateDownloadStatusOnResult(String id, String type, boolean downloaded){
        SQLiteDatabase db = this.getReadableDatabase();
        ContentValues values = new ContentValues();

        switch (type){
            case "audio":
                if (downloaded) values.put(downloadedAudio, 1);
                values.put(downloadingAudio, 0);
                break;
            case "video":
                if (downloaded) values.put(downloadedVideo, 1);
                values.put(downloadingVideo, 0);
                break;
        }

        db.update(results_table_name, values, "videoId = ?", new String[]{id});
    }

    public void updateDownloadingStatusOnResult(String id, String type, boolean downloading){
        SQLiteDatabase db = this.getReadableDatabase();
        ContentValues values = new ContentValues();
        type = (type.equals("audio")) ? downloadingAudio : downloadingVideo;
        int value = (downloading) ? 1 : 0;
        values.put(type, value);

        db.update(results_table_name, values, "videoId = ?", new String[]{id});
    }

    public int checkDownloaded(String url, String downloadType){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + history_table_name + " WHERE url='" +
                url + "' AND type='"+downloadType + "' LIMIT 1", null);

        if(cursor.moveToFirst()){
            String path = cursor.getString(8);
            File file = new File(path);
            if(!file.exists() && !path.isEmpty()){
                return 0;
            }else {
                return 1;
            }
        }
        return 0;
    }

}
