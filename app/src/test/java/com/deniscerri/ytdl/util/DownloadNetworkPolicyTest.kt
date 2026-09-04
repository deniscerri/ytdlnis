package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNetworkPolicyTest {
    @Test
    fun `shared budget caps fragments across downloads`() {
        assertEquals(
            2,
            DownloadNetworkPolicy.effectiveFragmentLimit(
                requestedFragments = 8,
                requestedDownloads = 3,
                maxParallelRequests = 8,
                budgetingEnabled = true,
            ),
        )
    }

    @Test
    fun `disabled budgeting preserves requested concurrency`() {
        assertEquals(
            12,
            DownloadNetworkPolicy.effectiveFragmentLimit(
                requestedFragments = 12,
                requestedDownloads = 4,
                maxParallelRequests = 8,
                budgetingEnabled = false,
            ),
        )
    }

    @Test
    fun `download limit cannot exceed request budget`() {
        assertEquals(
            4,
            DownloadNetworkPolicy.effectiveDownloadLimit(
                requestedDownloads = 10,
                maxParallelRequests = 4,
                budgetingEnabled = true,
            ),
        )
    }

    @Test
    fun `limits remain positive for invalid stored values`() {
        assertEquals(
            1,
            DownloadNetworkPolicy.effectiveFragmentLimit(
                requestedFragments = 0,
                requestedDownloads = 0,
                maxParallelRequests = 0,
                budgetingEnabled = true,
            ),
        )
    }
}
