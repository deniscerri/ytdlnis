package com.deniscerri.ytdlnis.util

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class UpdateUtil(var context: Context) {
    private val tag = "UpdateUtil"
    private val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val compositeDisposable = CompositeDisposable()
    private val ytdlpNightly = "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    fun updateApp(): Boolean {
        if (updatingApp) {
            Toast.makeText(
                context,
                context.getString(R.string.ytdl_already_updating),
                Toast.LENGTH_LONG
            ).show()
            return true
        }
        val res = AtomicReference(JSONObject())
        try {
            val thread = Thread { res.set(checkForAppUpdate()) }
            thread.start()
            thread.join()
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        val version: String
        val body: String?
        try {
            version = res.get().getString("tag_name")
            body = res.get().getString("body")
        } catch (ignored: JSONException) {
            return false
        }
        val versionNameInt = version.split("v")[1].replace(".","").toInt()
        val currentVersionNameInt = BuildConfig.VERSION_NAME.replace(".","").toInt()
        if (currentVersionNameInt > versionNameInt) {
            return false
        }
        val updateDialog = MaterialAlertDialogBuilder(context)
            .setTitle(version)
            .setMessage(body)
            .setIcon(R.drawable.ic_update_app)
            .setNegativeButton("Cancel") { _: DialogInterface?, _: Int -> }
            .setPositiveButton("Update") { _: DialogInterface?, _: Int ->
                startAppUpdate(
                    res.get()
                )
            }
        updateDialog.show()
        return true
    }

    private fun checkForAppUpdate(): JSONObject {
        val url = "https://api.github.com/repos/deniscerri/ytdlnis/releases/latest"
        val reader: BufferedReader
        var line: String?
        val responseContent = StringBuilder()
        val conn: HttpURLConnection
        var json = JSONObject()
        try {
            val req = URL(url)
            conn = req.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 5000
            if (conn.responseCode < 300) {
                reader = BufferedReader(InputStreamReader(conn.inputStream))
                while (reader.readLine().also { line = it } != null) {
                    responseContent.append(line)
                }
                reader.close()
                json = JSONObject(responseContent.toString())
                if (json.has("error")) {
                    throw Exception()
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return json
    }

    private fun startAppUpdate(updateInfo: JSONObject) {
        try {
            val versions = updateInfo.getJSONArray("assets")
            var url = ""
            var appName = ""
            for (i in 0 until versions.length()) {
                val tmp = versions.getJSONObject(i)
                if (tmp.getString("name").contains(Build.SUPPORTED_ABIS[0])) {
                    url = tmp.getString("browser_download_url")
                    appName = tmp.getString("name")
                    break
                }
            }
            if (url.isEmpty()) {
                Toast.makeText(context, R.string.couldnt_find_apk, Toast.LENGTH_SHORT).show()
                return
            }
            val uri = Uri.parse(url)
            Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .mkdirs()
            downloadManager.enqueue(
                DownloadManager.Request(uri)
                    .setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or
                                DownloadManager.Request.NETWORK_MOBILE
                    )
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setTitle(context.getString(R.string.downloading_update))
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, appName)
            )
        } catch (ignored: Exception) {
        }
    }

    suspend fun updateYoutubeDL() : UpdateStatus? =
        withContext(Dispatchers.IO){
            val sharedPreferences =
                context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            if (updatingYTDL) {
                withContext(Dispatchers.Main){
                    Toast.makeText(
                        context,
                        context.getString(R.string.ytdl_already_updating),
                        Toast.LENGTH_LONG
                    ).show()
                }
                UpdateStatus.ALREADY_UP_TO_DATE
            }
            withContext(Dispatchers.Main){
                Toast.makeText(
                    context,
                    context.getString(R.string.ytdl_updating_started),
                    Toast.LENGTH_SHORT
                ).show()
            }
            updatingYTDL = true


            YoutubeDL.getInstance().updateYoutubeDL(
                context, if (sharedPreferences.getBoolean("nightly_ytdl", false) ) ytdlpNightly else null
            ).apply {
                updatingYTDL = false
            }

//                .onFailure {
//                if (BuildConfig.DEBUG) Log.e(tag, context.getString(R.string.ytdl_update_failed), e)
//                Toast.makeText(
//                    context,
//                    context.getString(R.string.ytdl_update_failed),
//                    Toast.LENGTH_LONG
//                ).show()
//                updatingYTDL = false
//            }
        //}
    }

    companion object {
        var updatingYTDL = false
        var updatingApp = false
    }
}