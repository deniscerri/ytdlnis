package com.deniscerri.ytdl.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.anggrayudi.storage.file.isEmpty
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.services.BgUtilsPoTokenGeneratorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

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

        progress?.invoke("Downloading node-modules...")
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

        if (runServerAfterwards) {
            return runServer(context, progress)
        }

        return Result.success(Unit)
    }
}