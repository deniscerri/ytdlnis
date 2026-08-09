package com.deniscerri.ytdl.update

import android.os.Build
import org.json.JSONObject

/**
 * Parsed remote version manifest (version.json, published on main by the release pipeline).
 *
 * [versionCode] is the *base* code from `defaultConfig` — the same number the running build carries
 * as `BuildConfig.BASE_VERSION_CODE`. `BuildConfig.VERSION_CODE` cannot be compared against it,
 * because AGP offsets that per ABI split and the offset differs from device to device.
 *
 * Manifest shape:
 * ```json
 * { "versionCode": 1080902, "versionName": "1.8.9.2", "notes": "What's new…",
 *   "apks": { "arm64-v8a": "https://…", "universal": "https://…" } }
 * ```
 */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apks: Map<String, String>,
) {
    /**
     * The APK this device should install: the first of its supported ABIs the manifest offers,
     * falling back to the universal build. Null when the release carries nothing installable here,
     * which makes it not an update at all — [UpdateChecker] drops those.
     */
    val apkUrl: String?
        get() = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { apks[it] } ?: apks[UNIVERSAL]

    /** Serializes back to the manifest shape so [UpdateStore] can persist it across launches. */
    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("notes", notes)
        .put("apks", JSONObject(apks))
        .toString()

    companion object {
        private const val UNIVERSAL = "universal"

        fun fromJson(json: String): AppUpdate = JSONObject(json).run {
            val apks = optJSONObject("apks")
            AppUpdate(
                versionCode = getInt("versionCode"),
                versionName = getString("versionName"),
                notes       = optString("notes"),
                apks        = apks?.keys()?.asSequence()?.associateWith { apks.getString(it) }.orEmpty(),
            )
        }
    }
}
