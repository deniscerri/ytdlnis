package com.deniscerri.ytdl.util.extractors

import java.net.URLEncoder

/** Crowdsourced, de-clickbaited YouTube titles from DeArrow (by the SponsorBlock team). */
object DeArrowUtil {
    private const val API_URL = "https://sponsor.ajay.app/api/branding?videoID="

    /**
     * The best confirmed DeArrow title for a video, or null to keep the original. Network call, run it off the main thread.
     * Same rule as the bundled dearrow yt-dlp plugin: titles come sorted best first, only locked or upvoted ones count,
     * and if the best confirmed one is the original title the community kept it.
     */
    fun getTitle(videoId: String): String? {
        val titles = NetworkUtil.genericRequest(API_URL + URLEncoder.encode(videoId, "UTF-8"))
            .optJSONArray("titles") ?: return null
        for (i in 0 until titles.length()) {
            val entry = titles.optJSONObject(i) ?: continue
            if (!entry.optBoolean("locked") && entry.optInt("votes") <= 0) continue
            if (entry.optBoolean("original")) return null
            return entry.optString("title").trim().ifEmpty { null }
        }
        return null
    }
}
