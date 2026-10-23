package com.deniscerri.ytdl.util

import android.R.attr.action
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.anggrayudi.storage.file.isEmpty
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.services.BgUtilsPoTokenGeneratorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException

object BgUtilsPoTokenGeneratorUtil {

    fun getServerFolder(context: Context) : File {
        val file = File(context.filesDir, "bgutils_pot")
        file.mkdirs()
        return file
    }

    suspend fun runServer(context: Context, progress: ((String) -> Unit)? = null) : Result<Unit> {
        val serverFolder = getServerFolder(context)
        if (serverFolder.isEmpty) {
            return downloadFiles(context, true, progress)
        }

        progress?.invoke("Running server...")
        val intent = Intent(context, BgUtilsPoTokenGeneratorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        return Result.success(Unit)
    }

    fun stopServer(context: Context) {
        val intent = Intent(context, BgUtilsPoTokenGeneratorService::class.java).apply {
            action = "ACTION_EXIT"
        }
        context.startService(intent)
    }

    suspend fun downloadFiles(context: Context, runServerAfterwards: Boolean, progress: ((String) -> Unit)? = null) : Result<Unit> {
        return try {
            downloadFilesImpl(context, runServerAfterwards, progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            progress?.invoke(e.message ?: "")
            Result.failure(e)
        }
    }

    private suspend fun downloadFilesImpl(context: Context, runServerAfterwards: Boolean, progress: ((String) -> Unit)? = null) : Result<Unit> {
        stopServer(context)

        val serverFolder = getServerFolder(context)
        serverFolder.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }

        progress?.invoke("Downloading latest repository code...")
        val zipFile = File(serverFolder, "tmp.zip")
        withContext(Dispatchers.IO) {
            URI.create("https://github.com/Brainicism/bgutil-ytdlp-pot-provider/archive/refs/heads/master.zip")
                .toURL().openStream()
        }.use { input ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        withContext(Dispatchers.IO) {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    // Split the zip path by '/' to strip the top-level GitHub folder name
                    val relativePath = entry.name.substringAfter('/', "")

                    // If relativePath is blank, it was the root folder itself, so skip it
                    if (relativePath.isNotEmpty()) {
                        val outputFile = File(serverFolder, relativePath)

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                outputFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            }
        }
        zipFile.delete()

        progress?.invoke("Downloading yt-dlp plugin...")
        val ytdlResponse = RuntimeManager.getInstance().executePython("-m pip install -U bgutil-ytdlp-pot-provider") { _, _, line ->
            progress?.invoke(line)
        }
        if (ytdlResponse.exitCode != 0) {
            progress?.invoke(ytdlResponse.err)
            return Result.failure(Exception(ytdlResponse.err))
        }
        val hasNode = RuntimeManager.getInstance().nodeLocation.isAvailable
        val hasDeno = RuntimeManager.getInstance().denoLocation.isAvailable

        progress?.invoke("Downloading node-modules...")
        if (hasNode) {
            val serverDir = File(serverFolder, "server")
            val nodeResponse = RuntimeManager.getInstance().executeNpm(
                command = "install --ignore-scripts --no-audit --no-fund",
                executeDirectory = serverDir) { _, _, line ->
                progress?.invoke(line)
            }
            if (nodeResponse.exitCode != 0) {
                progress?.invoke(nodeResponse.err)
                return Result.failure(Exception(nodeResponse.err))
            }

            progress?.invoke("Building typescript files...")
            // install scripts are disabled so run the compiler directly
            val nodeResponse2 = RuntimeManager.getInstance().executeNode(
                command = "node_modules/typescript/bin/tsc",
                executeDirectory = serverDir) { _, _, line ->
                progress?.invoke(line)
            }
            if (nodeResponse2.exitCode != 0) {
                progress?.invoke(nodeResponse2.err)
                return Result.failure(Exception(nodeResponse2.err))
            }
        } else if (hasDeno) {
            val denoResponse = RuntimeManager.getInstance().executeDeno(
                command = "install",
                executeDirectory = File(serverFolder, "server")) { _, _, line ->
                progress?.invoke(line)
            }
            if (denoResponse.exitCode != 0) {
                progress?.invoke(denoResponse.err)
                return Result.failure(Exception(denoResponse.err))
            }

            progress?.invoke("Building typescript files...")
            val denoResponse2 = RuntimeManager.getInstance().executeDeno(
                command = "run -A npm:typescript/tsc --outDir build",
                executeDirectory = File(serverFolder, "server")) { _, _, line ->
                progress?.invoke(line)
            }
            if (denoResponse2.exitCode != 0) {
                progress?.invoke(denoResponse2.err)
                return Result.failure(Exception(denoResponse2.err))
            }
        }

        if (runServerAfterwards) {
            return runServer(context, progress)
        }

        return Result.success(Unit)
    }

    private val activeJobCount = AtomicInteger(0)
    private var shutdownJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var lastStartTime = 0L

    suspend fun isServerAlive(): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            // Using 127.0.0.1 directly bypasses IPv6/localhost resolution bottlenecks
            connection = (URI.create("http://127.0.0.1:4416/ping").toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 1000
                readTimeout = 1000
                useCaches = false
            }
            connection.responseCode == 200
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun acquireServer(context: Context) {
        shutdownJob?.cancel() // Cancel pending auto-shutdown if a new worker arrives
        val wasIdle = activeJobCount.getAndIncrement() == 0

        // The job count alone can't be trusted, the server can die while jobs are still counted
        // (killed by the system under memory pressure, crash). Restart it, unless it was started just now and is still booting.
        val startedRecently = System.currentTimeMillis() - lastStartTime < 5_000
        if (wasIdle || (!startedRecently && !isServerAlive())) {
            lastStartTime = System.currentTimeMillis()
            runServer(context)
        }
    }

    fun resetState() {
        activeJobCount.set(0)
        shutdownJob = null
    }

    fun releaseServer(context: Context, idleDelayMs: Long = 5000) {
        if (activeJobCount.decrementAndGet() <= 0) {
            activeJobCount.set(0)
            // Wait 5 seconds before stopping in case another worker starts right away
            shutdownJob = scope.launch {
                delay(idleDelayMs)
                if (activeJobCount.get() == 0) {
                    stopServer(context)
                }
            }
        }
    }
}