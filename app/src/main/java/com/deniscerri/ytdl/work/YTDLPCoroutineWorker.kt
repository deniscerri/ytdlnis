package com.deniscerri.ytdl.work

import android.app.ActivityManager
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.services.BgUtilsPoTokenGeneratorService
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

abstract class YTDLPCoroutineWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    abstract suspend fun runWork(): Result

    override suspend fun doWork(): Result {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val useBgUtilPoTokenServer = sharedPreferences.getBoolean("use_bgutils_potoken_generator", false)
        val bgUtilsMethod = sharedPreferences.getString("bgutils_potoken_method", "server")

        val requiresServer = useBgUtilPoTokenServer && bgUtilsMethod == "server"

        return try {
            if (requiresServer) {
                // Increment job count & start server if count was 0
                BgUtilsPoTokenGeneratorUtil.acquireServer(context)

                if (!waitForServerReady(timeoutMs = 10000)) {
                    return Result.retry()
                }
            }
            runWork()
        } catch (e: Exception) {
            Result.failure()
        } finally {
            if (requiresServer) {
                // Decrement job count & auto-stop service when 0 active jobs remain
                BgUtilsPoTokenGeneratorUtil.releaseServer(context)
            }
        }
    }

    private suspend fun isBgUtilsServerAlive(): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            // Using 127.0.0.1 directly bypasses IPv6/localhost resolution bottlenecks
            val url = URL("http://127.0.0.1:4416/ping")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1000
                readTimeout = 1000
                useCaches = false
            }
            connection.responseCode == 200
        } catch (e: Exception) {
            android.util.Log.d("YTDLWorker", "Ping failed: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun waitForServerReady(timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isBgUtilsServerAlive()) return true
            delay(500)
        }
        return false
    }
}