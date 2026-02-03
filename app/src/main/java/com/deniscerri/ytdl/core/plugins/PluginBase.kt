package com.deniscerri.ytdl.core.plugins

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.file.openInputStream
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.FileUtils
import java.io.File

abstract class PluginBase {
    protected abstract val pluginName: String      // e.g., "ffmpeg"
    protected abstract val bundledZipName: String   // e.g., "libffmpeg.zip.so"
    protected abstract val bundledVersion: String   // e.g., "v7.1"
    protected abstract val githubRepositoryPackageURL: String  // github repository package url

    @Serializable
    data class PluginRelease(
        @SerialName("name")
        val version: String,
        @SerialName("package_html_url")
        val downloadUrl: String,
        @SerialName("created_at")
        val createdAt: String,
        var isInstalled: Boolean
    )

    // Preferences Keys
    private val installedKey get() = "${pluginName}_installed"
    private val versionKey get() = "${pluginName}_version"
    private val bundledVerKey get() = "${pluginName}_bundled_ver"

    lateinit var currentVersion : String

    companion object {
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .build()
        }
    }

    fun init(context: Context) {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        val packageDir = File(baseDir, "packages/$pluginName")

        if (!isDownloaded(context)) {
            initBundled(context, packageDir)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        currentVersion = if (prefs.getBoolean(installedKey, false)) {
            prefs.getString(versionKey, "unknown") ?: "unknown"
        } else {
            bundledVersion
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

    private fun getRuntimeDir(context: Context) : File {
        return File(context.noBackupFilesDir, "runtimes/$pluginName")
    }

    suspend fun downloadRelease(context: Context, release: PluginRelease, onProgress: (Int) -> Unit) : File? {
        val runtimeDir = getRuntimeDir(context)
        FileUtils.deleteQuietly(runtimeDir)
        runtimeDir.mkdirs()

        return withContext(Dispatchers.IO) {
            try {
                val tempZipFile = File(context.cacheDir, "${pluginName}_tmp.zip")

                //download
                val request = Request.Builder().url(release.downloadUrl).build()
                val response = sharedClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body
                val totalBytes = body.contentLength()
                var bytesDownloaded = 0L

                body.byteStream().use { inputStream ->
                    tempZipFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Calculate percentage
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            onProgress(progress)
                        }
                    }
                }

                tempZipFile
            } catch (e: Exception) {
                null
            }
        }
    }

    fun installFromZip(context: Context, zipFile: DocumentFile, versionTag: String? = null) : Result<String> {
        return kotlin.runCatching {
            val runtimeDir = getRuntimeDir(context)
            // 3. Unzip the main Bundle (contains libnode.so and libnode.zip.so)
            val inputStream = zipFile.openInputStream(context)
            ZipUtils.unzip(inputStream, runtimeDir)
            inputStream?.close()
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
            val version = versionTag ?: "IMPORTED"
            saveState(context, version)

            if (versionTag != null) {
                zipFile.delete()
            }

            Result.success(version)
        }.getOrElse {
            Result.failure(it)
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

    private fun saveState(context: Context, version: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            putBoolean(installedKey, true)
            putString(versionKey, version)
        }
    }

    suspend fun getReleases(context: Context) : List<PluginRelease> {
        val request = Request.Builder()
            .url(githubRepositoryPackageURL)
            .header("Accept", "application/vnd.github+json")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = sharedClient.newCall(request).execute()
                val jsonString = response.body.string()
                val json = Json { ignoreUnknownKeys = true }
                if (response.isSuccessful && jsonString.isNotEmpty()) {
                    val supportedArch = getArchSuffix()

                    json.decodeFromString<List<PluginRelease>>(jsonString)
                        .filter { it.version.contains(supportedArch) }
                        .onEach {
                            it.isInstalled = currentVersion == it.version
                        }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                listOf()
            }
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