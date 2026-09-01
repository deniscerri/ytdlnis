package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStoragePolicyTest {
    @Test
    fun estimateIncludesInputsOutputAndReserve() {
        val gibibyte = 1024L * 1024L * 1024L
        val estimate = DownloadStoragePolicy.estimateRequiredBytes(gibibyte, gibibyte / 2)
        assertEquals(
            (gibibyte + gibibyte / 2) * 22 / 10 +
                DownloadStoragePolicy.MINIMUM_WORKING_RESERVE_BYTES,
            estimate,
        )
    }

    @Test
    fun warnsOnlyWhenKnownCapacityIsTooSmall() {
        assertTrue(DownloadStoragePolicy.shouldWarn(1_000, 999))
        assertFalse(DownloadStoragePolicy.shouldWarn(1_000, 1_000))
        assertFalse(DownloadStoragePolicy.shouldWarn(1_000, 0))
    }
}
