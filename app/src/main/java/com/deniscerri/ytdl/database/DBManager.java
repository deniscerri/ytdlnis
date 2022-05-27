package com.deniscerri.ytdl.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DBManager extends SQLiteOpenHelper {

    public static final String db_name = "ytdlnis";
    public static final int db_version = 1;
    public static final String table_name = "video";
    public static final String id = "id";
    public static final String videoId = "videoId";
    public static final String title = "title";
    public static final String author = "author";
    public static final String thumb = "thumb";
    public static final String type = "type";
    public static final String time = "time";


    public DBManager(Context context){
        super(context, db_name, null, db_version);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String query = "CREATE TABLE " + table_name + " ("
                + id + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + videoId + " TEXT,"
                + title + " TEXT,"
                + author + " TEXT,"
                + thumb + " TEXT,"
                + type + " TEXT,"
                + time + " TEXT)";

        sqLiteDatabase.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + table_name);
        onCreate(sqLiteDatabase);
    }

    public ArrayList<Video> merrVideot(){
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
                        cursor.getString(6)));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }
//
//    public Video merrVideonmeID(String id){
//        SQLiteDatabase db = this.getReadableDatabase();
//        Cursor cursor = db.rawQuery("SELECT * FROM " + table_name + " WHERE "+ id + " = table_name.videoId", null);
//
//        if(cursor.moveToFirst()){
//            String title = cursor.getString(2);
//            String author = cursor.getString(3);
//            String thumb = cursor.getString(4);
//
//            return new Video(id, title, author, thumb);
//        }
//
//        cursor.close();
//
//        return null;
//    }

    public void shtoVideo(String videoID, String videoTitle, String videoAuthor, String videoThumb, String downloadedType, String downloadedTime){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues vlerat = new ContentValues();

        vlerat.put(videoId, videoID);
        vlerat.put(title, videoTitle);
        vlerat.put(author, videoAuthor);
        vlerat.put(thumb, videoThumb);
        vlerat.put(type, downloadedType);
        vlerat.put(time, downloadedTime);

        db.insert(table_name, null, vlerat);

        db.close();
    }
}
