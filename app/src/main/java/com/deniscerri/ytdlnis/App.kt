package com.deniscerri.ytdlnis

import android.app.Application
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executors


class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        val sharedPreferences =  PreferenceManager.getDefaultSharedPreferences(this)
        setDefaultValues()
        applicationScope = CoroutineScope(SupervisorJob())
        applicationScope.launch((Dispatchers.IO)) {
            try {
                initLibraries()
                val appVer = sharedPreferences.getString("version", "")!!
                if(appVer.isEmpty() || appVer != BuildConfig.VERSION_NAME){
                    UpdateUtil(this@App).updateYoutubeDL()
                    sharedPreferences.edit(commit = true){
                        putString("version", BuildConfig.VERSION_NAME)
                    }
                }
            }catch (e: Exception){
                Toast.makeText(this@App, e.message, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        WorkManager.initialize(
            this@App,
            Configuration.Builder()
                .setExecutor(Executors.newFixedThreadPool(
                    sharedPreferences.getInt("concurrent_downloads", 1)))
                .build())

    }
    @Throws(YoutubeDLException::class)
    private fun initLibraries() {
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
        Aria2c.getInstance().init(this)
    }

    private fun setDefaultValues(){
        val SPL = 1
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (sp.getInt("spl", 0) != SPL) {
            PreferenceManager.setDefaultValues(this, R.xml.root_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.downloading_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.general_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.processing_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.folders_preference, true)
            PreferenceManager.setDefaultValues(this, R.xml.updating_preferences, true)
            sp.edit().putInt("spl", SPL).apply()
        }

    }

    private fun createNotificationChannels() {
        val notificationUtil = NotificationUtil(this)
        notificationUtil.createNotificationChannel()
    }

    companion object {
        private const val TAG = "App"
        private lateinit var applicationScope: CoroutineScope
        lateinit var instance: App
    }
}