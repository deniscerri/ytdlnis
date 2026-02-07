package com.deniscerri.ytdl.core.plugins

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.file.toRawFile
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
import java.time.LocalDate

abstract class PluginBase {
    protected abstract val executableName: String      // e.g., "ffmpeg"
    protected abstract val pluginFolderName: String      // e.g., "ffmpeg"
    protected abstract val bundledZipName: String   // e.g., "libffmpeg.zip.so"
    fun getInstance(): PluginBase = this

    abstract val bundledVersion: String?
    var downloadedVersion: String? = null
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

    data class PluginLocation(
        val binDir: File,
        val ldDir: File,
        val executable: File,
        val isDownloaded: Boolean,
        val isBundled: Boolean,
        val isAvailable: Boolean
    )

    // Preferences Keys
    private val downloadedVersionKey get() = "${executableName}_downloaded_ver"
    private val bundledVerKey get() = "${executableName}_bundled_ver"

    private val packagesRoot = "packages"
    private val downloadedPackagesRoot = "downloaded_packages"


    lateinit var location: PluginLocation

    companion object {
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .build()
        }
    }

    fun init(context: Context) {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        val packageDir = File(baseDir, "$packagesRoot/$pluginFolderName")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        //try init bundled
        initBundled(context, packageDir)

        location = getLocation(context, baseDir)
        downloadedVersion = if (location.isDownloaded) {
            prefs.getString(downloadedVersionKey, null)
        } else {
            ""
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

    private fun getDownloadedDir(context: Context) : File {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        return File(baseDir, "$downloadedPackagesRoot/$pluginFolderName")
    }

    fun getLocation(context: Context, baseDir: File): PluginLocation {
        //downloaded
        val downloadedDir = getDownloadedDir(context)
        val downloadedBinDir = downloadedDir
        val downloadedLDLibDir = downloadedDir
        val downloadedExe = File(downloadedDir, "lib$executableName.so")

        //bundled
        val bundledDir = File(baseDir, packagesRoot)
        val bundledBinDir = File(context.applicationInfo.nativeLibraryDir)
        val bundledLDLibDir = File(bundledDir, pluginFolderName)
        val bundledExe = File(bundledBinDir, "lib$executableName.so")

        val isDownloaded = downloadedExe.exists()
        val isBundled = bundledExe.exists()

        return PluginLocation(
            binDir = if (isDownloaded) downloadedBinDir else bundledBinDir,
            ldDir = if (isDownloaded) downloadedLDLibDir else bundledLDLibDir,
            executable = if (isDownloaded) downloadedExe else bundledExe,
            isDownloaded,
            isBundled,
            isDownloaded || isBundled
        )
    }

    suspend fun downloadRelease(context: Context, release: PluginRelease, onProgress: (Int) -> Unit) : DocumentFile? {
        val runtimeDir = getDownloadedDir(context)
        FileUtils.deleteQuietly(runtimeDir)
        runtimeDir.mkdirs()

        return withContext(Dispatchers.IO) {
            try {
                val tempZipFile = File(context.cacheDir, "${pluginFolderName}_tmp.zip")

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

                DocumentFile.fromFile(tempZipFile)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun installFromZip(context: Context, zipFile: DocumentFile, versionTag: String? = null) : Result<String> {
        return kotlin.runCatching {
            val runtimeDir = getDownloadedDir(context)
            runtimeDir.deleteRecursively()
            runtimeDir.createNewFile()
            // 3. Unzip the main Bundle (contains libnode.so and libnode.zip.so)
            context.contentResolver.openInputStream(zipFile.uri).use { inputStream ->
                ZipUtils.unzip(inputStream, runtimeDir)
            }
            // 4. Handle the Bootstrap Zip (Double Unzip)
            // Look for any .zip.so file in the extracted directory
            runtimeDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".zip.so")) {
                    ZipUtils.unzip(file, runtimeDir)
                    file.delete() // Remove the internal zip to save space
                }
            }

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

    fun uninstall(context: Context): Result<Unit> {
        return kotlin.runCatching {
            val runtimeDir = getDownloadedDir(context)

            if (runtimeDir.exists()) {
                val deleted = runtimeDir.deleteRecursively()
                if (!deleted) {
                    throw Exception("Failed to delete runtime directory at ${runtimeDir.path}")
                }
            }

            Result.success(Unit)
        }.getOrElse {
            Result.failure(it)
        }
    }

    private fun applyExecutablePermissions(file: File) {
        Runtime.getRuntime().exec(arrayOf("chmod", "-R", "755", file.absolutePath)).waitFor()
    }

    private fun saveState(context: Context, version: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            putString(downloadedVersionKey, version)
        }
    }

    suspend fun getReleases() : List<PluginRelease> {
//
//        return listOf(
//            PluginRelease(
//                version = "1.0",
//                downloadUrl = "http://192.168.1.144:8080/x86_64/x86_64.zip",
//                createdAt = LocalDate.now().toString(),
//                isInstalled = downloadedVersion == "1.0"
//            )
//        )

        if (githubRepositoryPackageURL.isEmpty()) return listOf()

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
                            it.isInstalled = downloadedVersion == it.version
                        }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                listOf()
            }
        }
    }


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