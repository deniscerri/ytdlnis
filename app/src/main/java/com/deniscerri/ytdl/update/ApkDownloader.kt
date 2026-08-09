package com.deniscerri.ytdl.update

import android.content.Context
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.packages.PackageBase.Companion.sharedClient
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import okhttp3.Request
import java.io.File

/**
 * Stateless engine that streams one APK to the cache, emitting [UpdateState] as a cold [Flow] —
 * [UpdateService] collects it on a background scope so the download outlives the dialog.
 *
 * The APK lands in cacheDir rather than the public Downloads folder: it is a transient artifact the
 * system installer consumes and nothing else, and the cache needs no storage permission and is
 * reclaimed automatically if the install never happens.
 */
object ApkDownloader {

    private const val BUFFER_SIZE = 65_536

    /** Where the downloaded APK lands. Stable name, so a re-download overwrites the last attempt. */
    fun apkFile(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }.resolve("ytdlnis-update.apk")

    /**
     * Inspects the staged APK on launch and decides its fate by comparing its packaged versionName
     * against the running build: a not-yet-installed one is returned so the install prompt can
     * resume, an already-installed (or unreadable) one is deleted in place and null returned.
     *
     * versionName rather than versionCode, because AGP gives each ABI split its own code — an
     * archive's code is only comparable to a build of the same ABI, and the universal fallback is
     * neither. The name is the same string on every split. Cheap no-op when nothing is staged.
     */
    fun stagedApk(context: Context): File? {
        val apk = apkFile(context)
        if (!apk.exists()) return null
        val staged = context.packageManager.getPackageArchiveInfo(apk.path, 0)?.versionName
        if (staged == null || staged == BuildConfig.VERSION_NAME) { apk.delete(); return null }
        return apk
    }

    /** Streams [update]'s APK for this device's ABI, emitting progress as it goes. */
    fun download(context: Context, update: AppUpdate): Flow<UpdateState> = flow {
        val apk = apkFile(context.applicationContext)
        apk.delete()

        val url = update.apkUrl
        if (url == null) {
            emit(UpdateState.Error(context.getString(R.string.update_no_apk)))
            return@flow
        }

        emit(UpdateState.Connecting)

        val speed = SpeedTracker()
        try {
            // OkHttp follows the cross-host redirect from the release URL to GitHub's asset CDN,
            // which HttpURLConnection would refuse to do on its own.
            val response = sharedClient.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    apk.delete()
                    emit(UpdateState.Error("HTTP ${it.code}"))
                    return@flow
                }
                val totalBytes = it.body.contentLength().takeIf { len -> len > 0 }
                var received = 0L

                it.body.byteStream().use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (input.read(buf).also { read -> n = read } != -1) {
                            yield()
                            out.write(buf, 0, n)
                            received += n
                            speed.add(n.toLong())
                            emit(UpdateState.Downloading(
                                progress = totalBytes?.let { total -> (received.toFloat() / total).coerceIn(0f, 1f) },
                                log      = formatLog(speed.bytesPerSec(), received, totalBytes),
                            ))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            apk.delete(); throw e
        } catch (e: Exception) {
            apk.delete()
            emit(UpdateState.Error(e.message ?: context.getString(R.string.errored)))
            return@flow
        }

        emit(UpdateState.Downloaded(apk))
    }.flowOn(Dispatchers.IO)

    /** "8.3 MB/s · 45.1 MB / 120 MB" — the line shown in the dialog and the notification. */
    private fun formatLog(speedBps: Long, received: Long, total: Long?): String {
        val rate = "${FileUtil.convertFileSize(speedBps)}/s"
        val got  = FileUtil.convertFileSize(received)
        return if (total != null) "$rate · $got / ${FileUtil.convertFileSize(total)}"
        else "$rate · $got"
    }
}

/**
 * Rolling transfer rate over a short window, so the figure shown reacts to the connection slowing
 * down instead of averaging the whole download into a flat, increasingly meaningless number.
 */
private class SpeedTracker(private val windowMs: Long = 3_000L) {

    private data class Sample(val time: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(bytes: Long) {
        val now = System.currentTimeMillis()
        samples.addLast(Sample(now, bytes))
        prune(now)
    }

    @Synchronized
    fun bytesPerSec(): Long {
        val now = System.currentTimeMillis()
        prune(now)
        if (samples.size < 2) return 0L
        val windowBytes = samples.sumOf { it.bytes }
        val elapsed = (now - samples.first().time).coerceAtLeast(1L)
        return windowBytes * 1_000L / elapsed
    }

    private fun prune(now: Long) {
        val cutoff = now - windowMs
        while (samples.isNotEmpty() && samples.first().time < cutoff) samples.removeFirst()
    }
}
