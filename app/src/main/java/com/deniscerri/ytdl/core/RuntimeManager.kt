package com.deniscerri.ytdl.core

import android.content.Context
import android.os.Build
import android.os.Environment
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.models.ExecuteException
import com.deniscerri.ytdl.core.models.ExecuteResponse
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.core.packages.Aria2c
import com.deniscerri.ytdl.core.packages.Deno
import com.deniscerri.ytdl.core.packages.FFmpeg
import com.deniscerri.ytdl.core.packages.NodeJS
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.core.packages.Python
import com.deniscerri.ytdl.core.packages.QuickJS
import com.deniscerri.ytdl.core.stream.StreamGobbler
import com.deniscerri.ytdl.core.stream.StreamProcessExtractor
import com.deniscerri.ytdl.database.models.PackageItem
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.concurrent.Volatile

object RuntimeManager {
    val idProcessMap = Collections.synchronizedMap(HashMap<String, Process>())
    lateinit var pythonLocation: PackageBase.PackageLocation
    lateinit var ffmpegLocation: PackageBase.PackageLocation
    lateinit var aria2Location: PackageBase.PackageLocation
    lateinit var nodeLocation : PackageBase.PackageLocation
    lateinit var denoLocation : PackageBase.PackageLocation
    lateinit var quickJsLocation : PackageBase.PackageLocation
    var ytdlpPath: File? = null

    @Volatile
    var initialized = false
        private set

    private var initLatch = CountDownLatch(1)
    private val initLock = Any()

    private var updateLatch = CountDownLatch(1)
    private val updateLock = Any()

    const val BASENAME = "ytdlnis"
    const val ytdlpDirName = "yt-dlp"
    const val ytdlpBin = "yt-dlp"

    private var ENV_LD_LIBRARY_PATH: String? = null
    private var PATH: String? = null
    private var ENV_SSL_CERT_FILE: String? = null
    private var OPEN_SSL_CONF: String? = null
    private var ENV_PYTHONHOME: String? = null
    private var TMPDIR: String = ""

    private var NPM_CONFIG_PREFIX: String = ""
    private var NPM_CONFIG_CACHE: String = ""
    private var NPM_CLI_PATH: String = ""
    private var NODE_OPTIONS: String = ""

    val packages: List<PackageItem> = listOf(
        PackageItem("Python", Python),
        PackageItem("FFmpeg", FFmpeg),
        PackageItem("NodeJS", NodeJS),
        PackageItem("Deno", Deno),
        PackageItem("Aria2c", Aria2c)
    )

    fun init(appContext: Context) {
        if (initialized) return

        synchronized(initLock) {
            val baseDir = File(appContext.noBackupFilesDir, BASENAME).apply { if (!exists()) mkdir() }

            val python = Python.getInstance()
            val ffmpeg = FFmpeg.getInstance()
            val aria2c = Aria2c.getInstance()
            val nodeJS = NodeJS.getInstance()
            val quickJS = QuickJS.getInstance()
            val deno = Deno.getInstance()

            python.init(appContext)
            ffmpeg.init(appContext)
            aria2c.init(appContext)
            nodeJS.init(appContext)
            quickJS.init(appContext)
            deno.init(appContext)

            //find location of libraries either from bundled or downloaded paths
            pythonLocation = python.location
            ffmpegLocation = ffmpeg.location
            aria2Location = aria2c.location
            nodeLocation = nodeJS.location
            denoLocation = deno.location
            quickJsLocation = quickJS.location

            val ytdlpDir = File(baseDir, ytdlpDirName)
            ytdlpPath = File(ytdlpDir, ytdlpBin)
            initYTDLP(appContext, ytdlpDir)

            val locations = listOf(
                pythonLocation,
                ffmpegLocation,
                aria2Location,
                nodeLocation,
                quickJsLocation,
                denoLocation,
            )

            val ldPaths = mutableListOf<String>()
            locations.forEach {
                val usrLib = File(it.ldDir, "usr/lib")
                if (usrLib.exists()) {
                    ldPaths.add(usrLib.absolutePath)
                } else if (it.ldDir.exists()) {
                    ldPaths.add(it.ldDir.absolutePath)
                }
            }
            ldPaths.add(appContext.applicationInfo.nativeLibraryDir)
            ENV_LD_LIBRARY_PATH = ldPaths.distinct().joinToString(":")

            val binPaths = locations.filter { it.binDir.exists() }.map { it.binDir.absolutePath }.toMutableList()
            binPaths.add(System.getenv("PATH") ?: "/system/bin")
            PATH = binPaths.distinct().joinToString(":")

            ENV_SSL_CERT_FILE = if (pythonLocation.isDownloaded) {
                File(pythonLocation.ldDir.parentFile, "usr/etc/tls/cert.pem").absolutePath
            } else {
                pythonLocation.ldDir.absolutePath + "/usr/etc/tls/cert.pem"
            }

            OPEN_SSL_CONF = ""
            if (nodeLocation.ldDir.exists()) {
                OPEN_SSL_CONF = if (nodeLocation.isDownloaded) {
                    File(nodeLocation.ldDir.parentFile, "usr/etc/tls/openssl.cnf").absolutePath
                } else {
                    nodeLocation.ldDir.absolutePath + "/usr/etc/tls/openssl.cnf"
                }
            }

            ENV_PYTHONHOME = if (pythonLocation.isDownloaded) {
                pythonLocation.ldDir.absolutePath + "/usr"
            } else {
                pythonLocation.ldDir.absolutePath + "/usr"
            }
            TMPDIR = appContext.cacheDir.absolutePath

            NPM_CONFIG_PREFIX = File(appContext.filesDir, ".npm-global").absolutePath
            NPM_CONFIG_CACHE = File(appContext.filesDir, ".npm-cache").absolutePath
            if (nodeLocation.executable.exists()) {
                NPM_CLI_PATH = File(nodeLocation.ldDir.absolutePath, "usr/lib/node_modules/npm/bin/npm-cli.js").absolutePath

                val optionsFile = File(appContext.filesDir, "node_dns_setup.js")
                optionsFile.writeText(NodeJS.getDNSSetup())
                NODE_OPTIONS = "--require ${optionsFile.absolutePath}"
            }

            initialized = true
            initLatch.countDown()
            updateLatch.countDown()
        }
    }

    fun reInit(context: Context) {
        synchronized(initLock) {
            initialized = false
            initLatch = CountDownLatch(1)
            init(context)
        }

    }

    fun assertInit() {
        val success = initLatch.await(30, TimeUnit.SECONDS)
        if (!success || !initialized) {
            throw IllegalStateException("Instance not initialized")
        }
    }

    fun assertNoUpdate() {
        val completed = updateLatch.await(2, TimeUnit.MINUTES)
        if (!completed) {
            throw IllegalStateException("Update timed out or failed to complete")
        }
    }

    @Throws(ExecuteException::class)
    fun initYTDLP(appContext: Context, ytdlpDir: File) {
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

    private fun buildYTDLCommand(
        request: YTDLRequest,
        usingCacheDir: Boolean
    ): List<String> {
        assertInit()
        assertNoUpdate()

        if (ffmpegLocation.isAvailable) {
            request.addOption("--ffmpeg-location", ffmpegLocation.executable.absolutePath)
        }

        if (nodeLocation.isAvailable) {
            request.addOption("--js-runtimes", "node:${nodeLocation.executable.absolutePath} --allow-fs-read=*")
        }

        if (denoLocation.isAvailable) {
            request.addOption("--js-runtimes", "deno:${denoLocation.executable.absolutePath}")
        }

        if (quickJsLocation.isAvailable) {
            request.addOption("--js-runtimes", "quickjs:${quickJsLocation.executable.absolutePath}")
        }

        if (request.buildCommand().contains("libaria2c.so")) {
            request.addOption(
                "--external-downloader-args",
                "aria2c:--ca-certificate=$ENV_SSL_CERT_FILE"
            )
        }

        if (!usingCacheDir) {
            request.addOption("--no-cache-dir")
        }

        request.addOption("--progress-delta", 0.1)

        return mutableListOf(pythonLocation.executable.absolutePath, ytdlpPath!!.absolutePath) + request.buildCommand()
    }

    fun execute(
        request: YTDLRequest,
        processId: String? = null,
        redirectErrorStream: Boolean = false,
        usingCacheDir: Boolean = false,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {
        val fullCommand = buildYTDLCommand(request, usingCacheDir)
        return executeImpl(fullCommand, processId, redirectErrorStream, callback = callback)
    }

    fun executePython(
        command: String,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {
        assertInit()

        val fullCommand = mutableListOf<String>(pythonLocation.executable.absolutePath)
        fullCommand.addAll(command.split(" "))
        return executeImpl(fullCommand, processId, true, callback = callback)
    }

    fun executeNode(
        command: String,
        processId: String? = null,
        executeDirectory: File? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {
        assertInit()

        val fullCommand = mutableListOf<String>(nodeLocation.executable.absolutePath)
        fullCommand.addAll(command.split(" "))
        return executeImpl(fullCommand, processId, true, executeDirectory = executeDirectory, callback = callback)
    }

    fun executeNpm(
        command: String,
        processId: String? = null,
        executeDirectory: File? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {
        assertInit()

        val fullCommand = mutableListOf<String>(nodeLocation.executable.absolutePath, NPM_CLI_PATH)
        fullCommand.addAll(command.split(" "))
        return executeImpl(fullCommand, processId, true, executeDirectory = executeDirectory, callback = callback)
    }


    fun executeDeno(
        command: String,
        processId: String? = null,
        executeDirectory: File? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {
        assertInit()

        val fullCommand = mutableListOf<String>(denoLocation.executable.absolutePath)
        fullCommand.addAll(command.split(" "))
        return executeImpl(fullCommand, processId, true, executeDirectory = executeDirectory, callback = callback)
    }

    fun executeImpl(
        fullCommand: List<String>,
        processId: String? = null,
        redirectErrorStream: Boolean = false,
        executeDirectory: File? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ) : ExecuteResponse {

        if (processId != null && idProcessMap.containsKey(processId)) {
            throw ExecuteException("Process ID already exists")
        }

        val startTime = System.currentTimeMillis()
        val processBuilder = ProcessBuilder(fullCommand).redirectErrorStream(redirectErrorStream)

        processBuilder.environment().putAll(getEnvironment())

        val outBuffer = StringBuffer()
        val errBuffer = StringBuffer()

        if (executeDirectory != null) {
            processBuilder.directory(executeDirectory)
        }

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
                0, //Everything is successful
            )

            if (!successCodes.contains(exitCode)) {
                // Check if process was manually killed (removed from map)
                if (processId != null && !idProcessMap.containsKey(processId)) throw CanceledException()
                throw ExecuteException(err)
            }

            ExecuteResponse(fullCommand, exitCode, System.currentTimeMillis() - startTime, out, err)
        } catch (e: InterruptedException) {
            process.destroy()
            throw e
        } finally {
            if (processId != null) idProcessMap.remove(processId)
        }
    }

    private fun startProcess(
        fullCommand: List<String>,
        processId: String?,
        executeDirectory: File?
    ): Process {
        if (processId != null && idProcessMap.containsKey(processId)) {
            throw ExecuteException("Process ID already exists")
        }

        val processBuilder = ProcessBuilder(fullCommand)
        processBuilder.environment().putAll(getEnvironment())
        if (executeDirectory != null) {
            processBuilder.directory(executeDirectory)
        }

        return try {
            processBuilder.start().also {
                if (processId != null) idProcessMap[processId] = it
            }
        } catch (e: IOException) {
            throw ExecuteException(e)
        }
    }

    fun <T> executeStreaming(
        request: YTDLRequest,
        processId: String? = null,
        usingCacheDir: Boolean = false,
        outputHandler: (InputStream) -> T
    ): T {
        val fullCommand = buildYTDLCommand(request, usingCacheDir)
        return executeStreamingImpl(fullCommand, processId, outputHandler = outputHandler)
    }

    fun <T> executeStreamingImpl(
        fullCommand: List<String>,
        processId: String? = null,
        executeDirectory: File? = null,
        outputHandler: (InputStream) -> T
    ): T {
        val process = startProcess(fullCommand, processId, executeDirectory = executeDirectory)
        val errBuffer = StringBuffer()

        return try {
            val stdErrProcessor = StreamGobbler(errBuffer, process.errorStream)

            // Consume + fully drain stdout via the caller's handler BEFORE waitFor(),
            // to avoid deadlocking on a full stdout pipe while the process still runs.
            val result = process.inputStream.use { outputHandler(it) }

            stdErrProcessor.join()
            val exitCode = process.waitFor()
            val err = errBuffer.toString()

            if (exitCode != 0) {
                if (processId != null && !idProcessMap.containsKey(processId)) throw CanceledException()
                throw ExecuteException(err)
            }

            result
        } catch (e: InterruptedException) {
            process.destroy()
            throw e
        } finally {
            if (processId != null) idProcessMap.remove(processId)
        }
    }

    fun getEnvironment() : Map<String, String?> {
        val env = mutableMapOf<String, String?>()

        env["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
        if (OPEN_SSL_CONF != "") {
            env["OPENSSL_CONF"] = OPEN_SSL_CONF
        }
        env["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
        env["PATH"] = PATH
        env["PYTHONHOME"] = ENV_PYTHONHOME
        env["HOME"] = ENV_PYTHONHOME
        env["TMPDIR"] = TMPDIR
        env["NPM_CONFIG_PREFIX"] = NPM_CONFIG_PREFIX
        env["NPM_CONFIG_CACHE"] = NPM_CONFIG_CACHE
        env["NPM_CLI_PATH"] = NPM_CLI_PATH
        env["NODE_OPTIONS"] = NODE_OPTIONS
        env["TERM"] = "xterm-256color"

        return env
    }

    fun getEnvironmentForTerminal(): MutableMap<String, String?> {
        val env = getEnvironment().toMutableMap()
        env["HOME"] = Environment.getExternalStorageDirectory().path
        return env
    }

    @Synchronized
    @Throws(ExecuteException::class)
    fun updateYTDL(
        appContext: Context,
        updateChannel: UpdateChannel = UpdateChannel.STABLE
    ): UpdateStatus? {
        assertInit()

        synchronized(updateLock) {
            if (updateLatch.count >= 0) {
                updateLatch = CountDownLatch(1)
            }
        }

        return try {
            YTDLUpdater.update(appContext, updateChannel)
        } catch (e: IOException) {
            throw ExecuteException("failed to update youtube-dl", e)
        } finally {
            synchronized(updateLock) {
                updateLatch.countDown()
            }
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

    @JvmStatic
    fun getInstance() = this
}