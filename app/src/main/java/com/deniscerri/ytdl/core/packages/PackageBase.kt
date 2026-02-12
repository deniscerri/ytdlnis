package com.deniscerri.ytdl.core.packages

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.ZipUtils
import com.deniscerri.ytdl.database.models.GithubReleaseAsset
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
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
        val mainNativeDir = context.applicationInfo.nativeLibraryDir

        var packageBinDir: String? = null
        if (apkPackage.isNotBlank()) {
            try {
                packageBinDir = context.packageManager.getApplicationInfo(apkPackage, 0).nativeLibraryDir
            } catch (e: Exception) {}
        }

        val packageExe = if (packageBinDir != null) File(packageBinDir, "lib$executableName.so") else null
        val isPackageActive = packageExe?.exists() == true
        var isBundleActive = false

        val finalExe: File
        val binDir: File
        val ldDir: File

        if (isPackageActive) {
            finalExe = packageExe
            binDir = File(packageBinDir!!)
            ldDir = getDownloadedDir(context)
        } else {
            val bundledDir = File(baseDir, packagesRoot)
            binDir = File(context.applicationInfo.nativeLibraryDir)
            ldDir = File(bundledDir, packageFolderName)
            finalExe = File(binDir, "lib$executableName.so")
            isBundleActive = finalExe.exists()
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

    suspend fun downloadRelease(context: Context, release: PackageRelease, onProgress: (Int) -> Unit) : Result<DocumentFile> {
        return withContext(Dispatchers.IO) {
            try {
                val tempZipFile = File(context.cacheDir, "${packageFolderName}_tmp.zip")

                //download
                val request = Request.Builder()
                    .url(release.assets.first().browser_download_url)
                    .build()

                val response = sharedClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Throwable(response.body.string()))
                }

                val body = response.body
                val totalBytes = body.contentLength()
                var bytesDownloaded = 0L

                body.byteStream().use { inputStream ->
                    tempZipFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled by user")

                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Calculate percentage
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            onProgress(progress)
                        }
                    }
                }

                Result.success(DocumentFile.fromFile(tempZipFile))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun installFromZip(context: Context, zipFile: DocumentFile, versionTag: String? = null) : Result<String> {
        return kotlin.runCatching {
            val runtimeDir = getDownloadedDir(context)
            FileUtils.deleteQuietly(runtimeDir)
            runtimeDir.mkdirs()

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
                            // nodejs-1.0.0-arm64-v8a
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