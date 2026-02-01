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
import java.net.HttpURLConnection
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

    fun downloadAndInstall(context: Context, zipUrl: String, versionTag: String, onProgress: (Long, Long) -> Unit) : Boolean {
        // 1. Clean up old installation
        val runtimeDir = File(context.noBackupFilesDir, "runtimes/$runtimeName")
        FileUtils.deleteQuietly(runtimeDir)
        runtimeDir.mkdirs()

        var success = false

        val tempZip = File(context.cacheDir, "${runtimeName}_bundle_tmp.zip")
        try {
            // 2. Download the bundle (handles GitHub 302 redirects)
            downloadWithRedirects(zipUrl, tempZip, onProgress)
            // 3. Unzip the main Bundle (contains libnode.so and libnode.zip.so)
            ZipUtils.unzip(tempZip, runtimeDir)
            // 4. Handle the Bootstrap Zip (Double Unzip)
            // Look for any .zip.so file in the extracted directory
            runtimeDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".zip.so")) {
                    ZipUtils.unzip(file, runtimeDir)
                    file.delete() // Remove the internal zip to save space
                }
            }
            // 5. Global Permission Fix
            // Scan for all files in any 'bin' folder and make them executable
            applyExecutablePermissions(runtimeDir)
            // 6. Save installation state
            saveState(context, versionTag)
            success = true
        } catch (ex: Exception) {
            success = false
        }
        if (tempZip.exists()) tempZip.delete()
        return success
    }

    private fun downloadWithRedirects(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        var currentUrl = url
        var connection: HttpURLConnection
        var redirectCount = 0

        while (true) {
            connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER) {

                currentUrl = connection.getHeaderField("Location")
                redirectCount++
                if (redirectCount > 5) throw Exception("Too many redirects")
                continue
            }
            break
        }

        val fileSize = connection.contentLength.toLong()
        var bytesCopied = 0L
        val buffer = ByteArray(8192)

        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    // Trigger the callback
                    onProgress?.invoke(bytesCopied, fileSize)
                    bytes = input.read(buffer)
                }
            }
        }
    }

    private fun applyExecutablePermissions(file: File) {
        if (file.isDirectory) {
            // Check if this folder is a 'bin' folder
            if (file.name == "bin") {
                file.listFiles()?.forEach { it.setExecutable(true, false) }
            }
            // Recurse into subdirectories
            file.listFiles()?.forEach { applyExecutablePermissions(it) }
        } else if (file.name == "libnode.so") {
            // Specifically ensure our renamed main binary is executable
            file.setExecutable(true, false)
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

    private fun saveState(context: Context, version: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            putBoolean(installedKey, true)
            putString(versionKey, version)
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