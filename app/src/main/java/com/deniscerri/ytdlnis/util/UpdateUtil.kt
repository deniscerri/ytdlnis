package com.deniscerri.ytdlnis.util

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.GithubRelease
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class UpdateUtil(var context: Context) {
    private val tag = "UpdateUtil"
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun updateApp(result: (result: String) -> Unit) {
        try {
            if (updatingApp) {
                Toast.makeText(
                    context,
                    context.getString(R.string.ytdl_already_updating),
                    Toast.LENGTH_LONG
                ).show()
            }
            val skippedVersions = sharedPreferences.getString("skip_updates", "")?.split(",")?.distinct()?.toMutableList() ?: mutableListOf()
            val res = getGithubReleases()

            if (res.isEmpty()){
                result(context.getString(R.string.network_error))
                return
            }

            val useBeta = sharedPreferences.getBoolean("update_beta", false)
            var v: GithubRelease?
            if (useBeta){
                v = res.firstOrNull { it.tag_name.contains("beta", true) && res.indexOf(it) == 0 }
                if (v == null) v = res.first()
            }else{
                v = res.first { !it.tag_name.contains("beta", true) }
            }

            val current = BuildConfig.VERSION_NAME.replace("-beta", "").replace(".", "").padEnd(10,'0').toInt()
            val incoming = v.tag_name.removePrefix("v").replace("-beta", "").replace(".", "").padEnd(10,'0').toInt()


            var isInLatest = true
            if ((current < incoming) ||
                (current > incoming && BuildConfig.VERSION_NAME.contains("beta", true) && !useBeta)){
                isInLatest = false
            }

            if (skippedVersions.contains(v.tag_name)) isInLatest = true

            if (isInLatest){
                result(context.getString(R.string.you_are_in_latest_version))
                return
            }

            Handler(Looper.getMainLooper()).post {
                val updateDialog = MaterialAlertDialogBuilder(context)
                    .setTitle(v.tag_name)
                    .setMessage(v.body)
                    .setIcon(R.drawable.ic_update_app)
                    .setNeutralButton(R.string.skip){ d: DialogInterface?, _:Int ->
                        skippedVersions.add(v.tag_name)
                        sharedPreferences.edit().putString("skip_updates", skippedVersions.joinToString(",")).apply()
                        d?.dismiss()
                    }
                    .setNegativeButton(context.resources.getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
                    .setPositiveButton(context.resources.getString(R.string.update)) { _: DialogInterface?, _: Int ->
                        runCatching {
                            val releaseVersion = v.assets.firstOrNull { it.name.contains(Build.SUPPORTED_ABIS[0]) }
                            if (releaseVersion == null){
                                Toast.makeText(context, R.string.couldnt_find_apk, Toast.LENGTH_SHORT).show()
                                return@runCatching
                            }


                            val uri = Uri.parse(releaseVersion.browser_download_url)
                            Environment
                                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                .mkdirs()
                            val downloadID = downloadManager.enqueue(
                                DownloadManager.Request(uri)
                                    .setAllowedNetworkTypes(
                                        DownloadManager.Request.NETWORK_WIFI or
                                                DownloadManager.Request.NETWORK_MOBILE
                                    )
                                    .setAllowedOverRoaming(true)
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setTitle(context.getString(R.string.downloading_update))
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, releaseVersion.name)
                            )

                            val onDownloadComplete: BroadcastReceiver =
                                object : BroadcastReceiver() {
                                    override fun onReceive(context: Context?, intent: Intent) {
                                        context?.unregisterReceiver(this)
                                        UiUtil.openFileIntent(context!!,
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath +
                                                    File.separator + releaseVersion.name)
                                    }
                                }

                            context.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
                        }

                    }
                val view = updateDialog.show()
                val textView = view.findViewById<TextView>(android.R.id.message)
                textView!!.movementMethod = LinkMovementMethod.getInstance()
                val mw = Markwon.builder(context).usePlugin(object: AbstractMarkwonPlugin() {

                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.linkResolver { view, link ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            startActivity(context, browserIntent, Bundle())
                        }
                    }
                }).build()
                mw.setMarkdown(textView, v.body)
            }
            return
        }catch (e: Exception){
            e.printStackTrace()
            result(e.message.toString())
            return
        }
    }

    private fun getGithubReleases(): List<GithubRelease> {
        val url = "https://api.github.com/repos/deniscerri/ytdlnis/releases"
        val conn: HttpURLConnection
        var json = listOf<GithubRelease>()
        try {
            val req = URL(url)
            conn = req.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 5000
            if (conn.responseCode < 300) {
                val myType = object : TypeToken<List<GithubRelease>>() {}.type
                json = Gson().fromJson(InputStreamReader(conn.inputStream), myType)
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return json
    }

    suspend fun updateYoutubeDL() : UpdateStatus? =
        withContext(Dispatchers.IO){
            val sharedPreferences =
                 PreferenceManager.getDefaultSharedPreferences(context)
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
            updatingYTDL = true


            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    context, if (sharedPreferences.getBoolean("nightly_ytdl", false) ) YoutubeDL.UpdateChannel._NIGHTLY else YoutubeDL.UpdateChannel._STABLE
                ).apply {
                    updatingYTDL = false
                }
            }catch (e: Exception){
                e.printStackTrace()
                updatingYTDL = false
                null
            }
    }

    companion object {
        var updatingYTDL = false
        var updatingApp = false
    }
}