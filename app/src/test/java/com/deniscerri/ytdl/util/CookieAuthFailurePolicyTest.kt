package com.deniscerri.ytdl.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieAuthFailurePolicyTest {
    @Test
    fun promptsForYoutubeExpiredCookieErrorsOnce() {
        val output = "ERROR: Sign in to confirm you’re not a bot. Use fresh cookies."
        assertTrue(
            CookieAuthFailurePolicy.shouldRequestYoutubeLogin(
                "https://www.youtube.com/watch?v=test",
                output,
                completedPrompts = 0,
            ),
        )
        assertFalse(
            CookieAuthFailurePolicy.shouldRequestYoutubeLogin(
                "https://www.youtube.com/watch?v=test",
                output,
                completedPrompts = 1,
            ),
        )
    }

    @Test
    fun ignoresCookieErrorsFromOtherSites() {
        assertFalse(
            CookieAuthFailurePolicy.shouldRequestYoutubeLogin(
                "https://example.com/video",
                "ERROR: cookies expired",
                completedPrompts = 0,
            ),
        )
    }
}
