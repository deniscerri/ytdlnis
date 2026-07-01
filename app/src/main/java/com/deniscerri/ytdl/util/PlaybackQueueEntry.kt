package com.deniscerri.ytdl.util

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class PlaybackQueueEntry(
    val path: String,
    val title: String? = null,
    val artist: String? = null,
    val thumb: String? = null,
    val url: String? = null,
    val index: Int = 0
) {
    fun toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(path)
            .setUri(FileUtil.playableUriForPath(path))
            .setMediaMetadata(
                MediaMetadata.Builder().apply {
                    if (!title.isNullOrBlank()) setTitle(title)
                    if (!artist.isNullOrBlank()) setArtist(artist)
                    if (!thumb.isNullOrBlank()) setArtworkUri(Uri.parse(thumb))
                }.build()
            )
            .build()
    }
}
