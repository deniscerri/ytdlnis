package com.deniscerri.ytdl.work

import android.app.ActivityManager
import android.content.Context
import android.preference.PreferenceManager
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
        return try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val useBgUtilPoTokenServer = sharedPreferences.getBoolean("use_bgutils_potoken_generator", false)
            if (useBgUtilPoTokenServer) {
                // 1. Ensure service is active before starting child worker logic
                if (!isBgUtilsServerAlive()) {
                    val serviceRunning = isBgUtilsServiceRunning(context)
                    if (!serviceRunning) {
                        BgUtilsPoTokenGeneratorUtil.runServer(context)
                    }

                    // Wait for local HTTP server to become responsive
                    val ready = waitForServerReady(timeoutMs = 10000)
                    if (!ready) {
                        return Result.retry()
                    }
                }
            }
            runWork()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    @Suppress("DEPRECATION")
    fun isBgUtilsServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return manager.getRunningServices(Int.MAX_VALUE).any {
            BgUtilsPoTokenGeneratorService::class.java.name == it.service.className
        }
    }

    private suspend fun isBgUtilsServerAlive(): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("http://localhost:4416/ping")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1000
                readTimeout = 1000
                useCaches = false
            }
            val code = connection.responseCode
            code == 200
        } catch (e: Exception) {
            // Temporarily log the exact error to logcat
            android.util.Log.e("YTDLWorker", "Ping failed: ${e.message}", e)
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