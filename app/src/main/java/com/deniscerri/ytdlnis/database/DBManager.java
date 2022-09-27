package com.deniscerri.ytdlnis.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

public class DBManager extends SQLiteOpenHelper {

    public static final String db_name = "ytdlnis_db";
    public static final int db_version = 6;
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
                + website + " TEXT)";

        sqLiteDatabase.execSQL(query);

        query = "CREATE TABLE " + history_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + duration + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT,"
                + isPlaylistItem + " INTENGER)";

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

    public void clearResults(){
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + results_table_name);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + results_table_name);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + history_table_name);
        onCreate(sqLiteDatabase);
    }


    public ArrayList<Video> getResults(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + results_table_name, null);
        ArrayList<Video> list = new ArrayList<>();

        if(cursor.moveToFirst()){
            do {
                // on below line we are adding the data from cursor to our array list.
                list.add(new Video(cursor.getString(1), //id
                        cursor.getString(2), //url
                        cursor.getString(3), //title
                        cursor.getString(4), //author
                        cursor.getString(5), //duration
                        cursor.getString(6), //thumb
                        cursor.getInt(7), //downloadedAudio
                        cursor.getInt(8), //downloadedVideo
                        cursor.getInt(9), //isPlaylistItem
                        cursor.getString(10))); //website
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    public ArrayList<Video> getHistory(String query){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + history_table_name
                        + " WHERE title LIKE '%"+query+"%' ORDER BY id DESC",
                null);
        ArrayList<Video> list = new ArrayList<>();

        if(cursor.moveToFirst()){
            do {
                // on below line we are adding the data from cursor to our array list.
                list.add(new Video(cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getString(7),
                        cursor.getInt(8)));
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

            db.insert(results_table_name, null, values);
        }

        db.close();
    }

    public void addToHistory(Video v){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(videoId, v.getVideoId());
        values.put(title, v.getTitle());
        values.put(author, v.getAuthor());
        values.put(duration, v.getDuration());
        values.put(thumb, v.getThumb());
        values.put(type, v.getDownloadedType());
        values.put(time, v.getDownloadedTime());

        db.insert(history_table_name, null, values);
        db.close();
    }

    public void updateDownloadStatusOnResult(String id, String type){
        SQLiteDatabase db = this.getReadableDatabase();
        ContentValues values = new ContentValues();

        switch (type){
            case "audio":
                values.put(downloadedAudio, 1);
                break;
            case "video":
                values.put(downloadedVideo, 1);
                break;
        }

        db.update(results_table_name, values, "videoId = ?", new String[]{id});
    }

    public int checkDownloaded(String id, String downloadType){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + history_table_name + " WHERE videoId='" +
                id + "' AND type='"+downloadType + "' LIMIT 1", null);

        if(cursor.moveToFirst()){
           return 1;
        }
        return 0;
    }

}
