package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRetryPolicyTest {
    @Test
    fun `retries recognized temporary HTTP and CDN statuses`() {
        val statuses = listOf(408, 425, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524)

        statuses.forEach { status ->
            val retry = HttpRetryPolicy.nextRetry(
                output = "ERROR: HTTP Error $status",
                completedTransientRetries = 0,
                completedExpiredMediaUrlRetries = 0,
            )

            assertEquals(status, retry?.statusCode)
            assertEquals(HttpRetryPolicy.Reason.TRANSIENT_HTTP, retry?.reason)
        }
    }

    @Test
    fun `does not retry permanent client errors`() {
        listOf(400, 401, 404, 410).forEach { status ->
            assertNull(
                HttpRetryPolicy.nextRetry(
                    output = "ERROR: HTTP Error $status",
                    completedTransientRetries = 0,
                    completedExpiredMediaUrlRetries = 0,
                ),
            )
        }
    }

    @Test
    fun `stops after bounded transient retry budget`() {
        assertNull(
            HttpRetryPolicy.nextRetry(
                output = "ERROR: HTTP Error 503",
                completedTransientRetries = HttpRetryPolicy.MAX_TRANSIENT_RETRIES,
                completedExpiredMediaUrlRetries = 0,
            ),
        )
    }

    @Test
    fun `honors bounded Retry-After seconds`() {
        val retry = HttpRetryPolicy.nextRetry(
            output = "HTTP Error 429\nRetry-After: 75",
            completedTransientRetries = 0,
            completedExpiredMediaUrlRetries = 0,
        )

        assertEquals(75L, retry?.delaySeconds)
    }

    @Test
    fun `refreshes an expired media URL once`() {
        val output = "ERROR: unable to download video data: HTTP Error 403: Forbidden"

        val retry = HttpRetryPolicy.nextRetry(
            output = output,
            completedTransientRetries = 0,
            completedExpiredMediaUrlRetries = 0,
        )
        assertEquals(HttpRetryPolicy.Reason.EXPIRED_MEDIA_URL, retry?.reason)
        assertNull(
            HttpRetryPolicy.nextRetry(
                output = output,
                completedTransientRetries = 0,
                completedExpiredMediaUrlRetries =
                    HttpRetryPolicy.MAX_EXPIRED_MEDIA_URL_RETRIES,
            ),
        )
    }

    @Test
    fun `does not retry an authentication-related 403`() {
        val output = "ERROR: Sign in and provide cookies: HTTP Error 403"

        assertNull(
            HttpRetryPolicy.nextRetry(
                output = output,
                completedTransientRetries = 0,
                completedExpiredMediaUrlRetries = 0,
            ),
        )
    }

    @Test
    fun `uses the last status when output contains earlier retry noise`() {
        val output = "HTTP Error 503 while retrying\nFinal error: HTTP Error 404"

        assertEquals(404, HttpRetryPolicy.statusCode(output))
        assertTrue(
            HttpRetryPolicy.nextRetry(
                output = output,
                completedTransientRetries = 0,
                completedExpiredMediaUrlRetries = 0,
            ) == null,
        )
    }
}
