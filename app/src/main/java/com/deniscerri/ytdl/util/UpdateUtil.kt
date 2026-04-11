package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.core.packages.PackageBase.Companion.sharedClient
import com.deniscerri.ytdl.database.models.GithubRelease
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException


class UpdateUtil(var context: Context) {
    private val tag = "UpdateUtil"
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val channelMap = mapOf(
        Pair<String, RuntimeManager.UpdateChannel>("stable", RuntimeManager.UpdateChannel.STABLE),
        Pair<String, RuntimeManager.UpdateChannel>("nightly", RuntimeManager.UpdateChannel.NIGHTLY),
        Pair<String, RuntimeManager.UpdateChannel>("master", RuntimeManager.UpdateChannel.MASTER)
    )

    private fun String.tagNameToVersionNumber() : Int {
        return this.replace("-beta", "").replace(".", "").padEnd(10,'0').toInt()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun tryGetNewVersion() : Result<GithubRelease> {
        try {
            val skippedVersions = sharedPreferences.getString("skip_updates", "")?.split(",")?.distinct()?.toMutableList() ?: mutableListOf()
            val res = getGithubReleases()

            if (res.isEmpty()){
                return Result.failure(Error(context.getString(R.string.network_error)))
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val currentVerNumber = currentVersion.tagNameToVersionNumber()

            val useBeta = sharedPreferences.getBoolean("update_beta", false)
            var isInLatest = true

            var v: GithubRelease
            if (useBeta) {
                v = res.firstOrNull { it.tag_name.contains("beta", true) } ?: res.first()
                val stableV = res.first { !it.tag_name.contains("beta", true) }

                val incomingVerNumber = v.tag_name.removePrefix("v").tagNameToVersionNumber()
                val incomingStableVerNumber = stableV.tag_name.removePrefix("v").tagNameToVersionNumber()

                //if in beta but latest stable higher
                if (currentVerNumber < incomingStableVerNumber) {
                    isInLatest = false
                    v = stableV
                }else{
                    isInLatest = currentVerNumber >= incomingVerNumber
                }
            }else {
                v = res.first { !it.tag_name.contains("beta", true) }
                val incomingVerNumber = v.tag_name.removePrefix("v").tagNameToVersionNumber()

                //if current version is beta but wants to downgrade to stable, allow it
                isInLatest = if (currentVersion.contains("beta", true)) {
                    false
                }else {
                    currentVerNumber >= incomingVerNumber
                }
            }

            if (skippedVersions.contains(v.tag_name)) isInLatest = true
            if (isInLatest) return Result.failure(Error(context.getString(R.string.you_are_in_latest_version)))
            return Result.success(v)
        }catch (e: Exception){
            e.printStackTrace()
            return Result.failure(e)
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

    suspend fun updateYTDL(c: String? = null) : YTDLPUpdateResponse =
        withContext(Dispatchers.IO){
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (updatingYTDL) {
                YTDLPUpdateResponse(YTDLPUpdateStatus.PROCESSING)
            }

            updatingYTDL = true

            val channel = if (c.isNullOrBlank()) sharedPreferences.getString("ytdlp_source", "stable") else c

            when(channel) {
                "stable", "nightly", "master" -> {
                    val res = RuntimeManager.getInstance().updateYTDL(context, channelMap[channel]!!)
                    if (res != RuntimeManager.UpdateStatus.DONE) {
                        YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE)
                    }else {
                        val version = RuntimeManager.getInstance().version(context)
                        YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, "Updated yt-dlp to ${channel}@${version}")
                    }
                }
                else -> {
                    val request = YTDLRequest(emptyList())
                    request.addOption("--update-to", "$channel")

                    val res = RuntimeManager.getInstance().execute(request)
                    val out = res.out.lines().last { it.isNotBlank() }

                    if (out.contains("ERROR")) YTDLPUpdateResponse(YTDLPUpdateStatus.ERROR, out)
                    if (out.contains("yt-dlp is up to date")) YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE, out)
                    else YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, out)
                }
            }


    }

    @SuppressLint("Range", "UnspecifiedRegisterReceiverFlag")
    suspend fun downloadReleaseApk(release: GithubRelease, onProgress: (Long) -> Unit) : Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val releaseVersion = release.assets.firstOrNull { it.name.contains(Build.SUPPORTED_ABIS[0]) }!!
                File(FileUtil.getDefaultApksPath()).mkdirs()
                val tempApk = File(FileUtil.getDefaultApksPath(), "${releaseVersion.browser_download_url.split("/").last()}")

                //download
                val request = Request.Builder()
                    .url(releaseVersion.browser_download_url)
                    .build()

                val response = sharedClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Throwable(response.body.string()))
                }

                val body = response.body
                val totalBytes = body.contentLength()
                var bytesDownloaded = 0L

                body.byteStream().use { inputStream ->
                    tempApk.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled by user")

                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Calculate percentage
                            val progress = ((bytesDownloaded * 100) / totalBytes)
                            onProgress(progress)
                        }
                    }
                }

                Result.success(tempApk)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        var updatingYTDL = false
        var updatingApp = false
    }
}