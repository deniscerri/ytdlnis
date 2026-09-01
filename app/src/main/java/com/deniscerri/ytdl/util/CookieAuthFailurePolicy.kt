package com.deniscerri.ytdl.util

/** Detects authentication-specific YouTube failures without treating ordinary 403s as login errors. */
object CookieAuthFailurePolicy {
    const val MAX_LOGIN_PROMPTS_PER_DOWNLOAD = 1

    private val authenticationFailure = Regex(
        "sign[ -]?in to confirm|please sign[ -]?in|login required|authentication required|" +
            "cookies?.{0,40}(expired|invalid|fresh)|failed to decrypt cookies",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun shouldRequestYoutubeLogin(url: String, output: String, completedPrompts: Int): Boolean {
        if (completedPrompts >= MAX_LOGIN_PROMPTS_PER_DOWNLOAD) return false
        if (!isYoutubeUrl(url)) return false
        return authenticationFailure.containsMatchIn(output)
    }

    fun isYoutubeUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("youtube.com") ||
            normalized.contains("youtu.be") ||
            normalized.contains("music.youtube.com")
    }
}
