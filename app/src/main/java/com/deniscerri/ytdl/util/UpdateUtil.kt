package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager.UpdateResult
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.ui.adapter.ChangelogAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class UpdateUtil(var context: Context) {
    private val tag = "UpdateUtil"
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val channelMap = mapOf(
        Pair<String, YoutubeDL.UpdateChannel>("stable", YoutubeDL.UpdateChannel.STABLE),
        Pair<String, YoutubeDL.UpdateChannel>("nightly", YoutubeDL.UpdateChannel.NIGHTLY),
        Pair<String, YoutubeDL.UpdateChannel>("master", YoutubeDL.UpdateChannel.MASTER)
    )

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun tryGetNewVersion() : Result<GithubRelease> {
        try {
            val skippedVersions = sharedPreferences.getString("skip_updates", "")?.split(",")?.distinct()?.toMutableList() ?: mutableListOf()
            val res = getGithubReleases()

            if (res.isEmpty()){
                return Result.failure(Error(context.getString(R.string.network_error)))
            }

            val useBeta = sharedPreferences.getBoolean("update_beta", false)
            val v: GithubRelease
            if (useBeta){
                val tmp = res.firstOrNull { it.tag_name.contains("beta", true) && res.indexOf(it) == 0 }
                v = tmp ?: res.first()
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
                return Result.failure(Error(context.getString(R.string.you_are_in_latest_version)))
            }

            return Result.success(v)
        }catch (e: Exception){
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    fun showChangeLog(activity: Activity){
        runCatching {
            val releases = getGithubReleases()

            val view = activity.layoutInflater.inflate(R.layout.generic_list, null)
            val adapter = ChangelogAdapter(activity)
            val recycler = view.findViewById<RecyclerView>(R.id.download_recyclerview)
            recycler.layoutManager = LinearLayoutManager(activity)
            recycler.adapter = adapter
            adapter.submitList(releases)
            recycler.enableFastScroll()

            val changeLogDialog = MaterialAlertDialogBuilder(context)
                .setTitle(activity.getString(R.string.changelog))
                .setView(view)
                .setIcon(R.drawable.ic_chapters)
                .setNegativeButton(context.resources.getString(R.string.dismiss)) { _: DialogInterface?, _: Int -> }
            Handler(Looper.getMainLooper()).post {
                changeLogDialog.show()
            }

        }.onFailure {
            if (it.message != null){
                Handler(Looper.getMainLooper()).post {
                    UiUtil.showErrorDialog(context, it.message!!)
                }
            }
        }
    }

    fun getGithubReleases(): List<GithubRelease> {
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

    data class YTDLPUpdateResponse (
        val status: YTDLPUpdateStatus,
        val message: String = ""
    )

    enum class YTDLPUpdateStatus {
        DONE, ALREADY_UP_TO_DATE, PROCESSING, ERROR
    }

    suspend fun updateYoutubeDL(c: String? = null) : YTDLPUpdateResponse =
        withContext(Dispatchers.IO){
            val sharedPreferences =
                 PreferenceManager.getDefaultSharedPreferences(context)
            if (updatingYTDL) {
                YTDLPUpdateResponse(YTDLPUpdateStatus.PROCESSING)
            }

            updatingYTDL = true

            val channel = if (c.isNullOrBlank()) sharedPreferences.getString("ytdlp_source", "stable") else c



            when(channel) {
                "stable", "nightly", "master" -> {
                    val res = YoutubeDL.updateYoutubeDL(context, channelMap[channel]!!)
                    if (res != YoutubeDL.UpdateStatus.DONE) {
                        YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE)
                    }else {
                        val version = YoutubeDL.version(context)
                        YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, "Updated yt-dlp to ${channel}@${version}")
                    }
                }
                else -> {
                    val request = YoutubeDLRequest(emptyList())
                    request.addOption("--update-to", "${channel}@latest")

                    val res = YoutubeDL.getInstance().execute(request)
                    val out = res.out.split(System.getProperty("line.separator")).last { it.isNotBlank() }

                    if (out.contains("ERROR")) YTDLPUpdateResponse(YTDLPUpdateStatus.ERROR, out)
                    if (out.contains("yt-dlp is up to date")) YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE, out)
                    else YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, out)
                }
            }


    }

    companion object {
        var updatingYTDL = false
        var updatingApp = false
    }
}