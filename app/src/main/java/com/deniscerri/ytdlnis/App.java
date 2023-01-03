package com.deniscerri.ytdlnis;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import com.deniscerri.ytdlnis.util.NotificationUtil;
import com.google.android.material.color.DynamicColors;
import com.yausername.aria2c.Aria2c;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

public class App extends Application {

    private static final String TAG = "App";
    public static NotificationUtil notificationUtil;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        notificationUtil = new NotificationUtil(this);
        createNotificationChannels();
        PreferenceManager.setDefaultValues(this, "root_preferences", this.MODE_PRIVATE, R.xml.root_preferences, false);
        configureRxJavaErrorHandler();
        Completable.fromAction(this::initLibraries).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new DisposableCompletableObserver() {
            @Override
            public void onComplete() {
                // it worked
            }

            @Override
            public void onError(Throwable e) {
                if(BuildConfig.DEBUG) Log.e(TAG, "failed to initialize youtubedl-android", e);
                Toast.makeText(getApplicationContext(), "initialization failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configureRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {

            if (e instanceof UndeliverableException) {
                // As UndeliverableException is a wrapper, get the cause of it to get the "real" exception
                e = e.getCause();
            }

            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }

            Log.e(TAG, "Undeliverable exception received, not sure what to do", e);
        });
    }

    private void initLibraries() throws YoutubeDLException {
        YoutubeDL.getInstance().init(this);
        FFmpeg.getInstance().init(this);
	    Aria2c.getInstance().init(this);
    }

    private void createNotificationChannels() {
        notificationUtil.createNotificationChannel();
    }
}
