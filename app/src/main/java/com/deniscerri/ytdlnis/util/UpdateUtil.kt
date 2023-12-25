package com.deniscerri.ytdlnis.util

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
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.GithubRelease
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale


class UpdateUtil(var context: Context) {
    private val tag = "UpdateUtil"
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun updateApp(result: (result: String) -> Unit) {
        try {
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
                    .setNeutralButton(R.string.ignore){ d: DialogInterface?, _:Int ->
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
                                    .setTitle(releaseVersion.name)
                                    .setDescription(context.getString(R.string.downloading_update))
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

    fun showChangeLog(activity: Activity){
        runCatching {
            val scrollView = ScrollView(activity)

            val linearLayout = LinearLayout(activity)
            linearLayout.orientation = LinearLayout.VERTICAL

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )

            layoutParams.setMargins(20, 20, 20, 0)
            scrollView.layoutParams = layoutParams
            scrollView.addView(linearLayout)
            val releases = getGithubReleases()

            val changeLogDialog = MaterialAlertDialogBuilder(context)
                .setTitle(activity.getString(R.string.changelog))
                .setView(scrollView)
                .setIcon(R.drawable.ic_chapters)
                .setNegativeButton(context.resources.getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
            Handler(Looper.getMainLooper()).post {
                changeLogDialog.show()
            }

            CoroutineScope(Dispatchers.IO).launch {
                releases.forEach {
                    (activity.layoutInflater.inflate(R.layout.changelog_item, null) as MaterialCardView).apply {
                        this.layoutParams = layoutParams
                        findViewById<TextView>(R.id.version).text = it.tag_name
                        findViewById<TextView>(R.id.date).text =  SimpleDateFormat(
                            DateFormat.getBestDateTimePattern(
                                Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(it.published_at.time)

                        val mdText = findViewById<TextView>(R.id.content)
                        val mw = Markwon.builder(context).usePlugin(object: AbstractMarkwonPlugin() {
                            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                                builder.linkResolver { view, link ->
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                    startActivity(context, browserIntent, Bundle())
                                }
                            }
                        }).build()
                        mw.setMarkdown(mdText, it.body)


                        val assetGroup = findViewById<ChipGroup>(R.id.assets)
                        it.assets.forEachIndexed { idx, c ->
                            val tmp = activity.layoutInflater.inflate(R.layout.suggestion_chip, assetGroup, false) as Chip
                            tmp.text = c.name
                            tmp.id = idx
                            tmp.setOnClickListener {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(c.browser_download_url))
                                startActivity(context, browserIntent, Bundle())
                            }
                            assetGroup!!.addView(tmp)
                        }

                        withContext(Dispatchers.Main){
                            linearLayout.addView(this@apply)
                        }
                    }
                }
            }

        }.onFailure {
            if (it.message != null){
                Handler(Looper.getMainLooper()).post {
                    UiUtil.showErrorDialog(context, it.message!!)
                }
            }
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
                UpdateStatus.ALREADY_UP_TO_DATE
            }
            updatingYTDL = true

            val channelMap = mapOf(
                "stable" to YoutubeDL.UpdateChannel._STABLE,
                "nightly" to YoutubeDL.UpdateChannel._NIGHTLY,
                "master" to YoutubeDL.UpdateChannel._MASTER,
            )
            val channel = sharedPreferences.getString("ytdlp_source", "nightly")
            YoutubeDL.getInstance().updateYoutubeDL(context, channelMap[channel] ?: YoutubeDL.UpdateChannel._NIGHTLY).apply {
                updatingYTDL = false
            }
    }

    companion object {
        var updatingYTDL = false
        var updatingApp = false
    }
}