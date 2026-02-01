package com.deniscerri.ytdl.core

import android.content.Context
import android.os.Build
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.models.ExecuteException
import com.deniscerri.ytdl.core.models.ExecuteResponse
import com.deniscerri.ytdl.core.models.RuntimeLocation
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.core.runtimes.Aria2c
import com.deniscerri.ytdl.core.runtimes.FFmpeg
import com.deniscerri.ytdl.core.runtimes.NodeJS
import com.deniscerri.ytdl.core.runtimes.Python
import com.deniscerri.ytdl.core.stream.StreamGobbler
import com.deniscerri.ytdl.core.stream.StreamProcessExtractor
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.Collections

object RuntimeManager {
    val idProcessMap = Collections.synchronizedMap(HashMap<String, Process>())
    lateinit var pythonLocation: RuntimeLocation
    lateinit var ffmpegLocation: RuntimeLocation
    lateinit var aria2Location: RuntimeLocation
    lateinit var nodeLocation : RuntimeLocation
    lateinit var quickJsLocation : RuntimeLocation
    var ytdlpPath: File? = null

    const val PREFS_NAME = "runtime_prefs"

    private var initialized = false
    private const val RUNTIME_ROOT = "runtimes"
    private const val PACKAGES_ROOT = "packages"
    const val BASENAME = "ytdlnis"
    const val ytdlpDirName = "yt-dlp"
    const val ytdlpBin = "yt-dlp"

    private var ENV_LD_LIBRARY_PATH: String? = null
    private var PATH: String? = null
    private var ENV_SSL_CERT_FILE: String? = null
    private var ENV_PYTHONHOME: String? = null
    private var TMPDIR: String = ""

    fun init(appContext: Context) {
        if (initialized) return
        val baseDir = File(appContext.noBackupFilesDir, BASENAME).apply { if (!exists()) mkdir() }

        //extract bundled libraries if present
        Python.getInstance().init(appContext)
        FFmpeg.getInstance().init(appContext)
        Aria2c.getInstance().init(appContext)
        NodeJS.getInstance().init(appContext)

        //find location of libraries either from bundled or downloaded paths
        pythonLocation = getLocation(appContext, baseDir, "python", "python")
        ffmpegLocation = getLocation(appContext, baseDir, "ffmpeg", "ffmpeg")
        aria2Location = getLocation(appContext, baseDir, "aria2", "aria2")
        nodeLocation = getLocation(appContext, baseDir, "node", "node")
        quickJsLocation = getLocation(appContext, baseDir, "quickjs", "qjs")

        val ytdlpDir = File(baseDir, ytdlpDirName)
        ytdlpPath = File(ytdlpDir, ytdlpBin)
        init_ytdlp(appContext, ytdlpDir)

        val locations = listOf(
            pythonLocation,
            ffmpegLocation,
            aria2Location,
            nodeLocation,
            quickJsLocation
        )

        val ldPaths = mutableListOf<String>()
        locations.forEach {
            val usrLib = File(it.ldLibraryDir, "usr/lib")
            if (usrLib.exists()) {
                ldPaths.add(usrLib.absolutePath)
            } else if (it.ldLibraryDirExists) {
                ldPaths.add(it.ldLibraryDir.absolutePath)
            }
        }
        ldPaths.add(appContext.applicationInfo.nativeLibraryDir)
        ENV_LD_LIBRARY_PATH = ldPaths.distinct().joinToString(":")

        val binPaths = locations.filter { it.binDirExists }.map { it.binDir.absolutePath }.toMutableList()
        binPaths.add(System.getenv("PATH") ?: "/system/bin")
        PATH = binPaths.distinct().joinToString(":")

        ENV_SSL_CERT_FILE = if (pythonLocation.isDownloaded) {
            File(pythonLocation.ldLibraryDir.parentFile, "usr/etc/tls/cert.pem").absolutePath
        } else {
            pythonLocation.ldLibraryDir.absolutePath + "/usr/etc/tls/cert.pem"
        }

        ENV_PYTHONHOME = if (pythonLocation.isDownloaded) {
            pythonLocation.ldLibraryDir.parent
        } else {
            pythonLocation.ldLibraryDir.absolutePath + "/usr"
        }
        TMPDIR = appContext.cacheDir.absolutePath

        initialized = true
    }

    fun reInit(context: Context) {
        initialized = false
        init(context)
    }

    private fun assertInit() {
        check(initialized) { "instance not initialized" }
    }

    fun getLocation(context: Context, baseDir: File, libName: String, exeName: String): RuntimeLocation {

        val isDownloaded = isDownloaded(context, libName)
        val binDir: File
        val ldLibDir: File
        val potentialExe: File

        if (isDownloaded(context, libName)) {
            val downloadedDir = File(context.noBackupFilesDir, "$RUNTIME_ROOT/$libName")
            binDir = File(downloadedDir, "bin")
            ldLibDir = downloadedDir
            potentialExe = File(binDir, exeName)
        } else {
            val packagesDir = File(baseDir, PACKAGES_ROOT)
            binDir = File(context.applicationInfo.nativeLibraryDir)
            ldLibDir = File(packagesDir, libName)
            potentialExe = File(binDir, "lib$exeName.so")
        }

        return RuntimeLocation(
            binDir = binDir,
            binDirExists = binDir.exists(),
            ldLibraryDir = ldLibDir,
            ldLibraryDirExists = ldLibDir.exists(),
            isDownloaded = isDownloaded,
            // Only store the path if the file exists and is a file
            exePath = if (potentialExe.exists() && potentialExe.isFile) {
                potentialExe.absolutePath
            } else {
                null
            }
        )
    }

    @Throws(ExecuteException::class)
    fun init_ytdlp(appContext: Context, ytdlpDir: File) {
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        val ytdlpBinary = File(ytdlpDir, ytdlpBin)
        if (!ytdlpBinary.exists()) {
            try {
                val inputStream = appContext.resources.openRawResource(R.raw.ytdlp)
                FileUtils.copyInputStreamToFile(inputStream, ytdlpBinary)
            } catch (e: Exception) {
                FileUtils.deleteQuietly(ytdlpDir)
                throw ExecuteException("failed to initialize", e)
            }
        }
    }

    fun destroyProcessById(id: String): Boolean {
        if (idProcessMap.containsKey(id)) {
            val p = idProcessMap[id]
            var alive = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                alive = p!!.isAlive
            }
            if (alive) {
                destroyChildProcesses(id)
                p?.destroy()
                idProcessMap.remove(id)
                return true
            }
        }
        return false
    }

    private fun destroyChildProcesses(id: String) : Boolean {
        try {
            val command = "pstree -p $id | grep -oP '\\(\\K[^\\)]+' | xargs kill"
            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
            val process = processBuilder.start()
            val res = process.waitFor()
            return res == 0
        }catch (e: Exception) {
            return false
        }
    }

    class CanceledException : Exception()

    fun execute(
        request: YTDLRequest,
        processId: String? = null,
        redirectErrorStream: Boolean = false,
        usingCacheDir: Boolean = false,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {
        assertInit()
        if (processId != null && idProcessMap.containsKey(processId)) {
            throw ExecuteException("Process ID already exists")
        }

        ffmpegLocation.exePath?.apply {
            request.addOption("--ffmpeg-location", this)
        }

        if (nodeLocation.exePath != null) {
            request.addOption("--js-runtimes", "node:${nodeLocation.exePath}")
        } else if (quickJsLocation.exePath != null) {
            request.addOption("--js-runtimes", "quickjs:${quickJsLocation.exePath}")
        }

        if (!usingCacheDir) {
            request.addOption("--no-cache-dir")
        }

        val startTime = System.currentTimeMillis()
        val fullCommand = mutableListOf<String>(pythonLocation.exePath!!, ytdlpPath!!.absolutePath) + request.buildCommand()

        val processBuilder = ProcessBuilder(fullCommand).redirectErrorStream(redirectErrorStream)

        processBuilder.environment().apply {
            this["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
            this["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
            this["PATH"] = PATH
            this["PYTHONHOME"] = ENV_PYTHONHOME
            this["HOME"] = ENV_PYTHONHOME
            this["TMPDIR"] = TMPDIR
        }

        val outBuffer = StringBuffer()
        val errBuffer = StringBuffer()

        val process = try {
            processBuilder.start().also {
                if (processId != null) idProcessMap[processId] = it
            }
        } catch (e: IOException) {
            throw ExecuteException(e)
        }

        return try {
            val stdOutProcessor = StreamProcessExtractor(outBuffer, process.inputStream, callback)
            val stdErrProcessor = StreamGobbler(errBuffer, process.errorStream)

            stdOutProcessor.join()
            stdErrProcessor.join()

            val exitCode = process.waitFor()
            val out = outBuffer.toString()
            val err = errBuffer.toString()

            val successCodes = listOf(
                0, //Everything is successful,
                100, //yt-dlp -U fails but the downloads is successful
            )

            if (!successCodes.contains(exitCode)) {
                // Check if process was manually killed (removed from map)
                if (processId != null && !idProcessMap.containsKey(processId)) throw CanceledException()
                if (out.isEmpty()) throw ExecuteException(err)
            }

            ExecuteResponse(fullCommand, exitCode, System.currentTimeMillis() - startTime, out, err)
        } catch (e: InterruptedException) {
            process.destroy()
            throw e
        } finally {
            if (processId != null) idProcessMap.remove(processId)
        }
    }

    @Synchronized
    @Throws(ExecuteException::class)
    fun updateYTDL(
        appContext: Context,
        updateChannel: UpdateChannel = UpdateChannel.STABLE
    ): UpdateStatus? {
        assertInit()
        return try {
            YTDLUpdater.update(appContext, updateChannel)
        } catch (e: IOException) {
            throw ExecuteException("failed to update youtube-dl", e)
        }
    }

    fun version(appContext: Context?): String? {
        return YTDLUpdater.version(appContext)
    }

    fun versionName(appContext: Context?): String? {
        return YTDLUpdater.versionName(appContext)
    }

    enum class UpdateStatus {
        DONE, ALREADY_UP_TO_DATE
    }

    open class UpdateChannel(val apiUrl: String) {
        object STABLE : UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
        object NIGHTLY :
            UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest")
        object MASTER :
            UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp-master-builds/releases/latest")

        companion object {
            @JvmField
            val _STABLE: STABLE = STABLE

            @JvmField
            val _NIGHTLY: NIGHTLY = NIGHTLY

            @JvmField
            val _MASTER: MASTER = MASTER
        }
    }

    //private helpers
    private fun isDownloaded(context: Context, libName: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("${libName}_installed", false)
    }
    @JvmStatic
    fun getInstance() = this
}