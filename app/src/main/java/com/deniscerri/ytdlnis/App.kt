package com.deniscerri.ytdlnis

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.color.DynamicColors
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.observers.DisposableCompletableObserver
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        createNotificationChannels()
        PreferenceManager.setDefaultValues(
            this,
            "root_preferences",
            MODE_PRIVATE,
            R.xml.root_preferences,
            false
        )

        WorkManager.initialize(
            this@App,
            Configuration.Builder()
                .setExecutor(Executors.newFixedThreadPool(
                    getSharedPreferences("root_preferences", MODE_PRIVATE)
                        .getInt("concurrent_downloads", 1)))
                .build())

        try {
            initLibraries()
        }catch (e: Exception){
            Toast.makeText(this@App, e.message, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    @Throws(YoutubeDLException::class)
    private fun initLibraries() {
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
        Aria2c.getInstance().init(this)
    }

    private fun createNotificationChannels() {
        val notificationUtil = NotificationUtil(this)
        notificationUtil.createNotificationChannel()
    }

    companion object {
        private const val TAG = "App"
    }
}