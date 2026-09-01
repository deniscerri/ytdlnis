package com.deniscerri.ytdl.util

import kotlin.math.min

/** Separates network transfer slots from bounded local post-processing work. */
object DownloadPipelinePolicy {
    private val postProcessingPrefixes = listOf(
        "[Merger]",
        "[ExtractAudio]",
        "[VideoConvertor]",
        "[VideoRemuxer]",
        "[Fixup",
        "[EmbedSubtitle]",
        "[Metadata]",
        "[ModifyChapters]",
        "[SplitChapters]",
        "[ThumbnailsConvertor]",
    )

    fun isPostProcessingOutput(line: String): Boolean {
        val trimmed = line.trimStart()
        return postProcessingPrefixes.any(trimmed::startsWith)
    }

    /**
     * Post-processing does not consume a network slot, but the queue may launch at
     * most one process beyond the network limit to avoid an unbounded FFmpeg pile-up.
     */
    fun availableNetworkSlots(
        networkLimit: Int,
        runningIds: Collection<Long>,
        postProcessingIds: Set<Long>,
        pipelineEnabled: Boolean,
    ): Int {
        val safeNetworkLimit = networkLimit.coerceAtLeast(1)
        val networkProcesses = runningIds.count { it !in postProcessingIds }
        val availableNetwork = (safeNetworkLimit - networkProcesses).coerceAtLeast(0)
        if (!pipelineEnabled) {
            return min(
                availableNetwork,
                (safeNetworkLimit - runningIds.size).coerceAtLeast(0),
            )
        }

        val totalProcessLimit = safeNetworkLimit + 1
        val availableProcesses = (totalProcessLimit - runningIds.size).coerceAtLeast(0)
        return min(availableNetwork, availableProcesses)
    }
}
