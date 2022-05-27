package com.yausername.youtubedl_common;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class SharedPrefsHelper {

    private SharedPrefsHelper() {
    }

    private static final String sharedPrefsName = "youtubedl-android";

    public static void update(Context appContext, String key, String value) {
        SharedPreferences pref = appContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, value);
        editor.apply();
    }

    @Nullable
    public static String get(Context appContext, String key) {
        SharedPreferences pref = appContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        return pref.getString(key, null);
    }
}
