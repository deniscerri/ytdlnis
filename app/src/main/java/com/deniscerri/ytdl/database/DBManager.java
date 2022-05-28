package com.deniscerri.ytdl.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class DBManager extends SQLiteOpenHelper {

    public static final String db_name = "ytdlnis";
    public static final int db_version = 1;
    public static final String results_table_name = "videoResults";
    public static final String history_table_name = "videoHistory";
    public static final String id = "id";
    public static final String videoId = "videoId";
    public static final String title = "title";
    public static final String author = "author";
    public static final String thumb = "thumb";
    public static final String type = "type";
    public static final String time = "time";
    public static final String isPlaylistItem = "isPlaylistItem";


    public DBManager(Context context){
        super(context, db_name, null, db_version);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String query = "CREATE TABLE " + results_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT,"
                + isPlaylistItem + " INTENGER)";

        sqLiteDatabase.execSQL(query);

        query = "CREATE TABLE " + history_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT,"
                + isPlaylistItem + " INTEGER)";

        sqLiteDatabase.execSQL(query);
    }

    public void recreateResultsTable(SQLiteDatabase sqLiteDatabase){
        String query = "CREATE TABLE " + results_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT,"
                + isPlaylistItem + " BOOLEAN)";

        sqLiteDatabase.execSQL(query);
    }

    public void clearHistory(){
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + history_table_name);

        String query = "CREATE TABLE " + history_table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT,"
                + isPlaylistItem + " BOOLEAN)";

        db.execSQL(query);

    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + results_table_name);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + history_table_name);
        onCreate(sqLiteDatabase);
    }

    public ArrayList<Video> merrVideot(String table_name){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + table_name, null);
        ArrayList<Video> list = new ArrayList<>();

        if(cursor.moveToFirst()){
            do {
                // on below line we are adding the data from cursor to our array list.
                list.add(new Video(cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getInt(7)));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    public ArrayList<Video> merrRezultatet(){
        return merrVideot(results_table_name);
    }

    public ArrayList<Video> merrHistorine(){
        return merrVideot(history_table_name);
    }

    public Video shkoKohenRezultatit(String id, String downloadedTime){
        SQLiteDatabase db = this.getReadableDatabase();
        ContentValues vlerat = new ContentValues();
        vlerat.put(time, downloadedTime);

        db.update(results_table_name, vlerat, "videoId = ?", new String[]{id});
        return null;
    }

    public void shtoVideoRezultat(ArrayList<Video> videot){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues vlerat = new ContentValues();

        db.execSQL("DROP TABLE IF EXISTS " + results_table_name);
        recreateResultsTable(db);

        for(Video v : videot){
            vlerat.put(videoId, v.getVideoId());
            vlerat.put(title, v.getTitle());
            vlerat.put(author, v.getAuthor());
            vlerat.put(thumb, v.getThumb());
            vlerat.put(type, v.getDownloadedType());
            vlerat.put(time, v.getDownloadedTime());
            vlerat.put(isPlaylistItem, v.getIsPlaylistItem());

            db.insert(results_table_name, null, vlerat);
        }

        db.close();
    }

    public void shtoVideoHistori(Video v){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues vlerat = new ContentValues();

        vlerat.put(videoId, v.getVideoId());
        vlerat.put(title, v.getTitle());
        vlerat.put(author, v.getAuthor());
        vlerat.put(thumb, v.getThumb());
        vlerat.put(type, v.getDownloadedType());
        vlerat.put(time, v.getDownloadedTime());

        db.insert(history_table_name, null, vlerat);
        db.close();
    }
}
