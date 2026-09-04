package com.deniscerri.ytdl.util

/**
 * Classifies failures that are safe to retry at the worker level after yt-dlp has
 * exhausted its own retry handling. Permanent client, authentication and permission
 * failures are deliberately excluded to avoid loops and unnecessary server traffic.
 */
object HttpRetryPolicy {
    const val MAX_TRANSIENT_RETRIES = 2
    const val MAX_EXPIRED_MEDIA_URL_RETRIES = 1

    private val transientStatusCodes = setOf(
        408,
        425,
        429,
        500,
        502,
        503,
        504,
        520,
        521,
        522,
        523,
        524,
    )
    private val statusPattern = Regex(
        "(?:HTTP Error|HTTP status(?: code)?|status code)\\s*[:=]?\\s*(\\d{3})",
        RegexOption.IGNORE_CASE,
    )
    private val retryAfterPattern = Regex(
        "Retry-After(?:\\s+header)?\\s*[:=]\\s*(\\d+)",
        RegexOption.IGNORE_CASE,
    )
    private val expiredMediaMarkers = listOf(
        "unable to download video data",
        "unable to download video",
        "unable to download audio",
        "unable to download fragment",
        "fragment",
    )
    private val authenticationMarkers = listOf(
        "sign in",
        "log in",
        "login",
        "cookie",
        "authentication",
        "private video",
        "members-only",
        "confirm you're not a bot",
    )

    enum class Reason {
        TRANSIENT_HTTP,
        EXPIRED_MEDIA_URL,
    }

    data class Decision(
        val reason: Reason,
        val statusCode: Int,
        val delaySeconds: Long,
    )

    /** Returns a bounded retry decision, or null when the failure must remain terminal. */
    fun nextRetry(
        output: String,
        completedTransientRetries: Int,
        completedExpiredMediaUrlRetries: Int,
    ): Decision? {
        val statusCode = statusCode(output) ?: return null

        if (statusCode in transientStatusCodes &&
            completedTransientRetries < MAX_TRANSIENT_RETRIES
        ) {
            return Decision(
                reason = Reason.TRANSIENT_HTTP,
                statusCode = statusCode,
                delaySeconds = retryDelaySeconds(
                    output = output,
                    statusCode = statusCode,
                    completedRetries = completedTransientRetries,
                ),
            )
        }

        if (statusCode == 403 &&
            completedExpiredMediaUrlRetries < MAX_EXPIRED_MEDIA_URL_RETRIES &&
            isExpiredMediaUrlFailure(output)
        ) {
            return Decision(
                reason = Reason.EXPIRED_MEDIA_URL,
                statusCode = statusCode,
                delaySeconds = 1,
            )
        }

        return null
    }

    fun statusCode(output: String): Int? = statusPattern.findAll(output)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    private fun isExpiredMediaUrlFailure(output: String): Boolean {
        val normalized = output.lowercase()
        return expiredMediaMarkers.any(normalized::contains) &&
            authenticationMarkers.none(normalized::contains)
    }

    private fun retryDelaySeconds(
        output: String,
        statusCode: Int,
        completedRetries: Int,
    ): Long {
        val retryAfter = retryAfterPattern.find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (retryAfter != null) return retryAfter.coerceIn(5, 300)

        val delays = if (statusCode == 429) longArrayOf(30, 90) else longArrayOf(5, 15)
        return delays[completedRetries.coerceIn(delays.indices)]
    }
}
