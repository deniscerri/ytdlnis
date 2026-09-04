package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleLanguagePolicyTest {
    @Test
    fun `migrates legacy translation wildcard`() {
        assertEquals(
            SubtitleLanguagePolicy.SAFE_DEFAULT,
            SubtitleLanguagePolicy.normalize(SubtitleLanguagePolicy.LEGACY_DEFAULT),
        )
    }

    @Test
    fun `preserves explicit user selection`() {
        assertEquals("en,fr", SubtitleLanguagePolicy.normalize("en,fr"))
    }

    @Test
    fun `detects optional subtitle failure`() {
        assertTrue(
            SubtitleLanguagePolicy.isDownloadFailure(
                "ERROR: Unable to download video subtitles for 'en-ar': HTTP Error 429",
            ),
        )
    }

    @Test
    fun `does not classify media failure as subtitle failure`() {
        assertFalse(
            SubtitleLanguagePolicy.isDownloadFailure(
                "ERROR: Unable to download video data: HTTP Error 503",
            ),
        )
    }
}
