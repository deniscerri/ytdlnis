package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.database.models.DownloadItem
import java.io.File

/** Conservatively estimates temporary input, FFmpeg output, and reserve space. */
object DownloadStoragePolicy {
    const val MINIMUM_WORKING_RESERVE_BYTES = 256L * 1024L * 1024L

    fun estimateRequiredBytes(item: DownloadItem): Long {
        val primary = item.format.filesize.coerceAtLeast(0L)
        val additionalAudio = item.videoPreferences.audioFormatIDs
            .distinct()
            .mapNotNull { id -> item.allFormats.firstOrNull { it.format_id == id } }
            .sumOf { it.filesize.coerceAtLeast(0L) }
        return estimateRequiredBytes(primary, additionalAudio)
    }

    fun estimateRequiredBytes(primaryBytes: Long, additionalBytes: Long): Long {
        val inputBytes = safeAdd(
            primaryBytes.coerceAtLeast(0L),
            additionalBytes.coerceAtLeast(0L),
        )
        if (inputBytes <= 1L) return MINIMUM_WORKING_RESERVE_BYTES

        // 2.2x covers input streams plus a concurrent merged/converted output.
        val temporaryAndOutput = if (inputBytes > Long.MAX_VALUE / 22L) {
            Long.MAX_VALUE
        } else {
            inputBytes * 22L / 10L
        }
        return safeAdd(temporaryAndOutput, MINIMUM_WORKING_RESERVE_BYTES)
    }

    fun availableBytes(directory: File): Long {
        directory.mkdirs()
        return directory.usableSpace.coerceAtLeast(0L)
    }

    fun shouldWarn(requiredBytes: Long, availableBytes: Long): Boolean {
        // Zero means Android could not resolve the capacity, commonly for a SAF URI.
        return availableBytes > 0L && requiredBytes > availableBytes
    }

    private fun safeAdd(left: Long, right: Long): Long {
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
