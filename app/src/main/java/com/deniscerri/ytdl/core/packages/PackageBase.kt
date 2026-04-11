package com.deniscerri.ytdl.core.packages

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.ZipUtils
import com.deniscerri.ytdl.database.models.GithubReleaseAsset
import com.deniscerri.ytdl.util.FileUtil
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

abstract class PackageBase {
    protected abstract val executableName: String      // e.g., "ffmpeg"
    protected abstract val packageFolderName: String      // e.g., "ffmpeg"
    protected abstract val bundledZipName: String   // e.g., "libffmpeg.zip.so"
    protected abstract val canUninstall: Boolean   // set false if library is essential
    protected abstract val githubRepo: String  // e.g deniscerri/ytdlnis-packages
    protected abstract val githubPackageName: String  // e.g ffmpeg
    abstract val apkPackage: String // e.g. com.deniscerri.ytdl.ffmpeg
    fun getInstance(): PackageBase = this

    abstract val bundledVersion: String?
    var downloadedVersion: String? = null

    @Serializable
    data class PackageRelease(
        @SerializedName(value = "tag_name")
        var tag_name: String,
        @SerialName("published_at")
        val published_at: String,
        @SerializedName(value = "assets")
        var assets: List<GithubReleaseAsset>,
        @SerializedName(value = "body")
        val body: String,
        var version: String = "",
        var downloadSize: Long = 0,
        var isInstalled: Boolean = false,
        var isBundled: Boolean = false
    )

    data class PackageLocation(
        val binDir: File,
        val ldDir: File,
        val executable: File,
        val isDownloaded: Boolean,
        val isBundled: Boolean,
        val isAvailable: Boolean,
        val canUninstall: Boolean
    )

    // Preferences Keys
    private val downloadedVersionKey get() = "${executableName}_downloaded_ver"
    private val bundledVerKey get() = "${executableName}_bundled_ver"

    private val packagesRoot = "packages"
    private val downloadedPackagesRoot = "downloaded_packages"


    lateinit var location: PackageLocation

    companion object {
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .build()
        }
    }

    fun init(context: Context) {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        val packageDir = File(baseDir, "$packagesRoot/$packageFolderName")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        //try init bundled
        val bundledZip = File(context.applicationInfo.nativeLibraryDir, bundledZipName)
        initBundled(context, bundledZip, packageDir)
        if (apkPackage.isNotBlank()) {
            //try init from package apk
            try {
                context.packageManager.getApplicationInfo(apkPackage, 0)
            } catch(e: Exception) {
                null
            }?.apply {
                val apkBundledZip = File(this.nativeLibraryDir, bundledZipName)
                initBundled(context, apkBundledZip, getDownloadedDir(context), packageApkVersion = getDownloadedPackageAppVersion(context))
            }
        }

        location = getLocation(context, baseDir)
        downloadedVersion = if (location.isDownloaded) {
            prefs.getString(downloadedVersionKey, null)
        } else {
            ""
        }
    }

    private fun initBundled(context: Context, bundledZip: File, targetDir: File, packageApkVersion: String? = null) {
        if (!bundledZip.exists()) return

        val currentSize = bundledZip.length().toString()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val bundledVerKey = prefs.getString(bundledVerKey, "")
        val downloadedVerKey = prefs.getString(downloadedVersionKey, "")

        val sizeMismatch = if (packageApkVersion != null) downloadedVerKey != packageApkVersion else bundledVerKey != currentSize

        if (!targetDir.exists() || sizeMismatch) {
            FileUtils.deleteQuietly(targetDir)
            targetDir.mkdirs()
            try {
                ZipUtils.unzip(bundledZip, targetDir)
                prefs.edit(commit = true) {
                    if (packageApkVersion != null) {
                        putString(downloadedVersionKey, packageApkVersion)
                    } else {
                        putString(bundledVerKey, currentSize)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDownloadedDir(context: Context) : File {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        return File(baseDir, "$downloadedPackagesRoot/$packageFolderName")
    }

    fun getLocation(context: Context, baseDir: File): PackageLocation {
        //downloaded apk location
        var packageBinDir: String? = null
        if (apkPackage.isNotBlank()) {
            try {
                packageBinDir = context.packageManager.getApplicationInfo(apkPackage, 0).nativeLibraryDir
            } catch (e: Exception) {}
        }
        val downloadedBinDir = File(packageBinDir ?: "")
        val downloadedLDDir = getDownloadedDir(context)
        val downloadedExe = File(packageBinDir, "lib$executableName.so")

        //bundled location
        val bundledBinDir = File(context.applicationInfo.nativeLibraryDir)
        val bundledLDDir = File(File(baseDir, packagesRoot), packageFolderName)
        val bundledExe = File(bundledBinDir, "lib$executableName.so")

        val isPackageActive = downloadedExe.exists()
        val isBundleActive = bundledExe.exists()

        val finalExe: File
        val binDir: File
        val ldDir: File

        if (isPackageActive) {
            finalExe = downloadedExe
            binDir = downloadedBinDir
            ldDir = downloadedLDDir
        } else {
            finalExe = bundledExe
            binDir = bundledBinDir
            ldDir = bundledLDDir
        }

        return PackageLocation(
            binDir = binDir,
            ldDir = ldDir,
            executable = finalExe,
            isDownloaded = isPackageActive,
            isBundled = isBundleActive,
            isPackageActive || isBundleActive,
            canUninstall
        )
    }

    private var downloadReleaseId = -1L
    private val onDownloadReleaseComplete = object : BroadcastReceiver() {
        @SuppressLint("Range")
        override fun onReceive(context: Context?, intent: Intent) {
            if (context == null) return;
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            if (id == downloadReleaseId) {
                context.unregisterReceiver(this)

                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                        FileUtil.openFileIntent(context, localUri)
                    }
                }
                cursor.close()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "Range")
    suspend fun downloadReleaseApk(context: Context, release: PackageRelease, onProgress: (Long) -> Unit) : Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val releaseVersion = release.assets.first()

                val uri = releaseVersion.browser_download_url.toUri()
                Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .mkdirs()
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadReleaseId = downloadManager.enqueue(
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

                var downloading = true
                while (downloading) {
                    val query = DownloadManager.Query().setFilterById(downloadReleaseId)
                    val cursor = downloadManager.query(query)

                    if (cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val totalBytes = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                        }

                        if (totalBytes > 0) {
                            val progress = (bytesDownloaded * 100L / totalBytes)
                            onProgress(progress)
                        }
                    }
                    cursor.close()
                    delay(500)
                }

                val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(onDownloadReleaseComplete, intentFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(onDownloadReleaseComplete, intentFilter)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun uninstallDownloadedRuntimeDir(context: Context): Result<Unit> {
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

    suspend fun getReleases() : Result<List<PackageRelease>> {
        if (githubRepo.isEmpty()) return Result.success(listOf())

        val request = Request.Builder()
            .url("https://api.github.com/repos/${githubRepo}/releases")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = sharedClient.newCall(request).execute()
                val jsonString = response.body.string()
                val json = Json { ignoreUnknownKeys = true }
                if (response.isSuccessful && jsonString.isNotEmpty()) {
                    val supportedArch = getArchSuffix()

                    val releases = json.decodeFromString<List<PackageRelease>>(jsonString)
                        .filter {
                            it.tag_name.contains(githubPackageName)
                        }
                        .onEach {
                            // e.g. nodejs-1.0.0-arm64-v8a
                            it.version = it.tag_name.split("-")[1]
                            it.assets = it.assets.filter { a -> a.name.contains(supportedArch) }
                            it.downloadSize = it.assets.first().size
                            it.isInstalled = downloadedVersion == "v${it.version}"
                            it.isBundled = bundledVersion == "v${it.version}"
                        }

                    Result.success(releases)
                } else {
                    Result.failure(Throwable(JSONObject(jsonString).getString("message")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getDownloadedPackageAppVersion(context: Context): String? {
        if (apkPackage.isBlank()) return null
        return try {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val info = packageManager.getPackageInfo(apkPackage, PackageManager.PackageInfoFlags.of(0))
                "v${info.versionName}"
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(apkPackage, 0)
                "v${info.versionName}"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null // Package not installed
        }
    }

    fun isPackageAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(apkPackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
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