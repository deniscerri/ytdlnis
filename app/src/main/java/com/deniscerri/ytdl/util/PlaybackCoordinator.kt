package com.deniscerri.ytdl.util

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackUiState(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val thumb: String? = null,
    val mediaId: String? = null,
    val playlistId: Long = 0L,
    val playlistIndex: Int = 0
)

data class PlaylistTransition(
    val playlistId: Long,
    val index: Int,
    val url: String?
)

class PlaybackCoordinator(private val application: Application) : AndroidViewModel(application) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pendingControllerActions = mutableListOf<(MediaController) -> Unit>()
    private var playerListener: Player.Listener? = null
    private var queueEntries: MutableList<PlaybackQueueEntry> = mutableListOf()
    private var singlePath: String? = null
    private var activePlaylistId: Long = 0L

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _playlistTransitions = MutableSharedFlow<PlaylistTransition>(extraBufferCapacity = 16)
    val playlistTransitions = _playlistTransitions.asSharedFlow()

    init {
        connectController()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun connectController() {
        val token = SessionToken(
            application.applicationContext,
            ComponentName(application.applicationContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(application.applicationContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val mediaController = try {
                future.get()
            } catch (e: Exception) {
                return@addListener
            }
            attachListener(mediaController)
            _controller.value = mediaController
            updateState(mediaController)
            val pending = pendingControllerActions.toList()
            pendingControllerActions.clear()
            pending.forEach { it(mediaController) }
        }, ContextCompat.getMainExecutor(application.applicationContext))
    }

    fun startSingle(path: String, title: String? = null, artist: String? = null, thumb: String? = null) {
        withController { mediaController ->
            savePosition()
            queueEntries.clear()
            activePlaylistId = 0L
            singlePath = path

            if (mediaController.currentMediaItem?.mediaId == path && mediaController.mediaItemCount == 1) {
                mediaController.play()
                updateState(mediaController)
                return@withController
            }

            val item = MediaItem.Builder()
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
            val savedPosition = positionPrefs().getLong(path, 0L)
            mediaController.setMediaItem(item, savedPosition)
            mediaController.prepare()
            mediaController.play()
            updateState(mediaController)
        }
    }

    fun startPlaylist(entries: List<PlaybackQueueEntry>, startIndex: Int, playlistId: Long) {
        if (entries.isEmpty()) return
        withController { mediaController ->
            savePosition()
            queueEntries = entries.toMutableList()
            activePlaylistId = playlistId
            singlePath = null

            val savedAbsoluteIndex = positionPrefs().getInt(playlistIndexKey(playlistId), Int.MIN_VALUE)
            val restoredIndex = queueEntries.indexOfFirst { it.index == savedAbsoluteIndex }
            val mediaIndex = when {
                restoredIndex >= 0 -> restoredIndex
                startIndex in queueEntries.indices -> startIndex
                else -> 0
            }
            val savedPosition = if (restoredIndex >= 0) {
                positionPrefs().getLong(playlistPositionKey(playlistId), 0L)
            } else {
                0L
            }

            mediaController.setMediaItems(queueEntries.map { it.toMediaItem() }, mediaIndex, savedPosition)
            mediaController.prepare()
            mediaController.play()
            updateState(mediaController)
        }
    }

    fun appendQueueEntries(entries: List<PlaybackQueueEntry>) {
        if (entries.isEmpty()) return
        withController { mediaController ->
            if (activePlaylistId == 0L) return@withController
            queueEntries.addAll(entries)
            entries.map { it.toMediaItem() }.forEach { mediaController.addMediaItem(it) }
            if (mediaController.playbackState == Player.STATE_ENDED || !mediaController.isPlaying) {
                mediaController.prepare()
                mediaController.play()
            }
            updateState(mediaController)
        }
    }

    fun playPause() {
        withController { mediaController ->
            if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            updateState(mediaController)
        }
    }

    fun stopAndClear() {
        withController { mediaController ->
            savePosition()
            mediaController.stop()
            mediaController.clearMediaItems()
            queueEntries.clear()
            singlePath = null
            activePlaylistId = 0L
            updateState(mediaController)
        }
    }

    fun savePosition() {
        val mediaController = _controller.value ?: return
        val prefs = positionPrefs()
        val position = mediaController.currentPosition
        val duration = mediaController.duration
        val finished = mediaController.playbackState == Player.STATE_ENDED ||
            (duration > 0 && position >= duration - 1500)

        prefs.edit().apply {
            if (activePlaylistId != 0L && queueEntries.isNotEmpty()) {
                if (finished || position <= 0) {
                    remove(playlistIndexKey(activePlaylistId))
                    remove(playlistPositionKey(activePlaylistId))
                } else {
                    val absoluteIndex = queueEntries.getOrNull(mediaController.currentMediaItemIndex)?.index
                        ?: mediaController.currentMediaItemIndex
                    putInt(playlistIndexKey(activePlaylistId), absoluteIndex)
                    putLong(playlistPositionKey(activePlaylistId), position)
                }
            } else {
                val path = singlePath ?: mediaController.currentMediaItem?.mediaId ?: return
                if (finished || position <= 0) remove(path) else putLong(path, position)
            }
        }.apply()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val mediaController = _controller.value
        if (mediaController != null) {
            action(mediaController)
        } else {
            pendingControllerActions.add(action)
        }
    }

    private fun attachListener(mediaController: MediaController) {
        playerListener?.let { mediaController.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState(mediaController)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState(mediaController)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateState(mediaController)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (activePlaylistId != 0L && queueEntries.isNotEmpty()) {
                    val index = mediaController.currentMediaItemIndex
                    val entry = queueEntries.getOrNull(index)
                    val cursor = entry?.index ?: index
                    application.getSharedPreferences("playlist_playback_cursor", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("playlist_${activePlaylistId}_cursor", cursor)
                        .apply()
                    _playlistTransitions.tryEmit(
                        PlaylistTransition(
                            playlistId = activePlaylistId,
                            index = cursor,
                            url = entry?.url
                        )
                    )
                }
                updateState(mediaController)
            }
        }
        playerListener = listener
        mediaController.addListener(listener)
    }

    private fun updateState(mediaController: MediaController) {
        val mediaItem = mediaController.currentMediaItem
        val metadata = mediaItem?.mediaMetadata
        val thumb = metadata?.artworkUri?.toString()
        val absolutePlaylistIndex = if (activePlaylistId != 0L) {
            queueEntries.getOrNull(mediaController.currentMediaItemIndex)?.index
                ?: mediaController.currentMediaItemIndex
        } else {
            0
        }
        _state.value = PlaybackUiState(
            hasMedia = mediaController.mediaItemCount > 0,
            isPlaying = mediaController.isPlaying,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            thumb = thumb,
            mediaId = mediaItem?.mediaId,
            playlistId = activePlaylistId,
            playlistIndex = absolutePlaylistIndex
        )
    }

    private fun positionPrefs() =
        application.getSharedPreferences("channel_player_positions", Context.MODE_PRIVATE)

    private fun playlistIndexKey(playlistId: Long) = "playlist_${playlistId}_index"
    private fun playlistPositionKey(playlistId: Long) = "playlist_${playlistId}_position"

    override fun onCleared() {
        _controller.value?.let { controller ->
            playerListener?.let { controller.removeListener(it) }
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _controller.value = null
        super.onCleared()
    }
}
