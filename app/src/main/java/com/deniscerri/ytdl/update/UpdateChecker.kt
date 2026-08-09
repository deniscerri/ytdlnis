package com.deniscerri.ytdl.update

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.core.packages.PackageBase.Companion.sharedClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Fetches the remote version manifest and decides whether a newer build exists. Best-effort: any
 * network or parse failure returns null, so a launch is never blocked by a failed update check.
 */
object UpdateChecker {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/LhacenMed/ytdlnis/main/version.json"

    /**
     * Returns the available update, or null when there is none. An update needs both a higher
     * version code and an APK this device can actually install — a release with neither is not
     * something to prompt about.
     */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(MANIFEST_URL).build()
            val json = sharedClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body.string()
            }
            AppUpdate.fromJson(json)
                .takeIf { it.versionCode > BuildConfig.BASE_VERSION_CODE && it.apkUrl != null }
        }.getOrNull()
    }
}
