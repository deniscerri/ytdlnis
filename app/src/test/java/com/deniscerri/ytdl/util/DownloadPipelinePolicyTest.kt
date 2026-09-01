package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPipelinePolicyTest {
    @Test
    fun recognizesFfmpegPostProcessingStages() {
        assertTrue(DownloadPipelinePolicy.isPostProcessingOutput("[Merger] Merging formats"))
        assertTrue(DownloadPipelinePolicy.isPostProcessingOutput("[ExtractAudio] Destination: song.mp3"))
        assertFalse(DownloadPipelinePolicy.isPostProcessingOutput("[download] 100% of 10MiB"))
        assertFalse(DownloadPipelinePolicy.isPostProcessingOutput("[SponsorBlock] Fetching segments"))
    }

    @Test
    fun postProcessingReleasesOneNetworkSlot() {
        assertEquals(
            1,
            DownloadPipelinePolicy.availableNetworkSlots(
                networkLimit = 2,
                runningIds = listOf(1, 2),
                postProcessingIds = setOf(1),
                pipelineEnabled = true,
            ),
        )
    }

    @Test
    fun pipelineAllowsOnlyOneOverflowProcess() {
        assertEquals(
            1,
            DownloadPipelinePolicy.availableNetworkSlots(
                networkLimit = 2,
                runningIds = listOf(1, 2),
                postProcessingIds = setOf(1, 2),
                pipelineEnabled = true,
            ),
        )
    }
}
