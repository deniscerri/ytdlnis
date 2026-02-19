package com.deniscerri.ytdl.core

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager.getInstance
import com.deniscerri.ytdl.core.models.ExecuteException
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object YTDLUpdater {
    private const val TAG = "YTDLUpdater"

    private const val dlpBinaryName = "yt-dlp"
    private const val dlpVersionKey = "dlpVersion"
    private const val dlpVersionNameKey = "dlpVersionName"

    @Throws(IOException::class, ExecuteException::class)
    internal fun update(
        appContext: Context?,
        updateChannel: RuntimeManager.UpdateChannel = RuntimeManager.UpdateChannel.STABLE
    ): RuntimeManager.UpdateStatus {
        if (appContext == null) return RuntimeManager.UpdateStatus.ALREADY_UP_TO_DATE

        // 1. Check for update and get JSON response manually
        val jsonString = fetchJsonFromUrl(updateChannel.apiUrl)
            ?: throw IOException("Failed to fetch update info")

        val json = JSONObject(jsonString)
        val newVersion = json.optString("tag_name")
        val oldVersion = version(appContext)

        if (newVersion == oldVersion) {
            return RuntimeManager.UpdateStatus.ALREADY_UP_TO_DATE
        }

        // 2. Parse download URL for the specific binary
        val downloadUrl = getDownloadUrl(json)

        // 3. Download and Install
        val file = download(appContext, downloadUrl)
        val ytdlpDir = getYTDLDir(appContext)
        val binary = File(ytdlpDir, dlpBinaryName)

        try {
            if (ytdlpDir.exists()) FileUtils.deleteDirectory(ytdlpDir)
            ytdlpDir.mkdirs()
            FileUtils.copyFile(file, binary)
        } catch (e: Exception) {
            FileUtils.deleteQuietly(ytdlpDir)
            getInstance().initYTDLP(appContext, ytdlpDir)
            throw ExecuteException(e)
        } finally {
            file.delete()
        }

        updateSharedPrefs(appContext, newVersion, json.optString("name"))
        return RuntimeManager.UpdateStatus.DONE
    }

    private fun fetchJsonFromUrl(apiUrl: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching update JSON", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getDownloadUrl(json: JSONObject): String {
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            // Assuming RuntimeManager.ytdlpBin is "yt-dlp" or similar
            if (RuntimeManager.ytdlpBin == asset.getString("name")) {
                return asset.getString("browser_download_url")
            }
        }
        throw ExecuteException("Unable to get download url")
    }

    private fun download(appContext: Context, url: String): File {
        val file = File.createTempFile(dlpBinaryName, null, appContext.cacheDir)
        val downloadUrl = URL(url)
        FileUtils.copyURLToFile(downloadUrl, file, 10000, 15000)
        return file
    }

    private fun updateSharedPrefs(appContext: Context, tag: String, name: String) {
        PreferenceManager.getDefaultSharedPreferences(appContext).edit {
            putString(dlpVersionKey, tag)
            putString(dlpVersionNameKey, name)
        }
    }

    private fun getYTDLDir(appContext: Context): File {
        val baseDir = File(appContext.noBackupFilesDir, RuntimeManager.BASENAME)
        return File(baseDir, RuntimeManager.ytdlpDirName)
    }

    fun version(appContext: Context?): String? =
        PreferenceManager.getDefaultSharedPreferences(appContext!!).getString(dlpVersionKey, "")

    fun versionName(appContext: Context?): String? =
        PreferenceManager.getDefaultSharedPreferences(appContext!!).getString(dlpVersionNameKey, "")
}