package com.deniscerri.ytdl.util

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlin.math.max

object VideoPlayerUtil {
    private const val MINIMUM_BUFFER_DURATION = 1000 * 5 //exo default is 50s

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun buildPlayer(context: Context) : ExoPlayer {
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            // cache the last three minutes
            .setBackBuffer(1000 * 60 * 3, true)
            .setBufferDurationsMs(
                MINIMUM_BUFFER_DURATION,
                max(50 * 1000, MINIMUM_BUFFER_DURATION),
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        return ExoPlayer.Builder(context)
            .setUsePlatformDiagnostics(false)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(DefaultTrackSelector(context))
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .build()
            .apply {
                skipSilenceEnabled = false
                playbackParameters = PlaybackParameters(1f, 1.0f)
            }
    }
}