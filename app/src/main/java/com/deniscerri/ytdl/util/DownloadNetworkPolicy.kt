package com.deniscerri.ytdl.util

import kotlin.math.min

/**
 * Keeps item-level and fragment-level concurrency inside one shared request budget.
 * This prevents their settings from multiplying into an unexpectedly large number
 * of simultaneous connections.
 */
object DownloadNetworkPolicy {
    const val DEFAULT_CONCURRENT_DOWNLOADS = 2
    const val DEFAULT_CONCURRENT_FRAGMENTS = 4
    const val DEFAULT_MAX_PARALLEL_REQUESTS = 8

    fun effectiveDownloadLimit(
        requestedDownloads: Int,
        maxParallelRequests: Int,
        budgetingEnabled: Boolean,
    ): Int {
        val downloads = requestedDownloads.coerceAtLeast(1)
        if (!budgetingEnabled) return downloads
        return min(downloads, maxParallelRequests.coerceAtLeast(1))
    }

    fun effectiveFragmentLimit(
        requestedFragments: Int,
        requestedDownloads: Int,
        maxParallelRequests: Int,
        budgetingEnabled: Boolean,
    ): Int {
        val fragments = requestedFragments.coerceAtLeast(1)
        if (!budgetingEnabled) return fragments

        val downloads = effectiveDownloadLimit(
            requestedDownloads = requestedDownloads,
            maxParallelRequests = maxParallelRequests,
            budgetingEnabled = true,
        )
        val fragmentsPerDownload = maxParallelRequests.coerceAtLeast(1) / downloads
        return min(fragments, fragmentsPerDownload.coerceAtLeast(1))
    }
}
