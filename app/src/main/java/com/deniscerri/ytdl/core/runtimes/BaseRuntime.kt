package com.deniscerri.ytdl.core.runtimes

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.ZipUtils
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import java.io.File
import java.net.URL

abstract class BaseRuntime {
    protected abstract val runtimeName: String      // e.g., "ffmpeg"
    protected abstract val bundledZipName: String   // e.g., "libffmpeg.zip.so"
    protected abstract val manifestURL: String          // RUNTIME ZIP SOURCE API URL

    data class RuntimeUpdateInfo(val version: String, val downloadUrl: String)

    // Preferences Keys
    private val installedKey get() = "${runtimeName}_installed"
    private val versionKey get() = "${runtimeName}_version"
    private val bundledVerKey get() = "${runtimeName}_bundled_ver"

    fun init(context: Context) {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        val packageDir = File(baseDir, "packages/$runtimeName")

        if (!isDownloaded(context)) {
            initBundled(context, packageDir)
        }
    }

    private fun initBundled(context: Context, targetDir: File) {
        val bundledZip = File(context.applicationInfo.nativeLibraryDir, bundledZipName)
        if (!bundledZip.exists()) return

        val currentSize = bundledZip.length().toString()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (!targetDir.exists() || prefs.getString(bundledVerKey, "") != currentSize) {
            FileUtils.deleteQuietly(targetDir)
            targetDir.mkdirs()
            try {
                ZipUtils.unzip(bundledZip, targetDir)
                prefs.edit(commit = true) { putString(bundledVerKey, currentSize) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadAndInstall(context: Context, zipUrl: String, versionTag: String) {
        val downloadDir = File(context.noBackupFilesDir, "runtimes/$runtimeName")
        FileUtils.deleteQuietly(downloadDir)
        downloadDir.mkdirs()

        val tempZip = File(context.cacheDir, "${runtimeName}_tmp.zip")
        try {
            URL(zipUrl).openStream().use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
            }

            ZipUtils.unzip(tempZip, downloadDir)

            // Recursive function to set executable permissions on everything in bin/
            File(downloadDir, "bin").listFiles()?.forEach {
                it.setExecutable(true, false)
            }

            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putBoolean(installedKey, true)
                putString(versionKey, versionTag)
            }
        } finally {
            tempZip.delete()
        }
    }

    fun getVersion(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return if (prefs.getBoolean(installedKey, false)) {
            prefs.getString(versionKey, "unknown") ?: "unknown"
        } else {
            "PRE-BUNDLED"
        }
    }

    open fun checkForUpdates(context: Context): RuntimeUpdateInfo? {
        return try {
            val response = URL(manifestURL).readText()
            val json = JSONObject(response)
            val latestVersion = json.getString("version")
            if (latestVersion != getVersion(context)) {
                val urls = json.getJSONObject("urls")
                val downloadUrl = urls.getString(getArchSuffix())
                RuntimeUpdateInfo(latestVersion, downloadUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isDownloaded(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(installedKey, false)

    fun getArchSuffix(): String {
        val abi = Build.SUPPORTED_ABIS[0]
        return when {
            abi.startsWith("arm64") -> "arm64-v8a"
            abi.startsWith("armeabi") -> "armeabi-v7a"
            abi.startsWith("x86_64") -> "x86_64"
            else -> "arm64-v8a"
        }
    }
}