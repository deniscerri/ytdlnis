package com.deniscerri.ytdl.ui.more

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.PlaybackService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.common.util.concurrent.ListenableFuture

class PlayerBottomSheetDialog : BottomSheetDialogFragment() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var filePath: String? = null
    private var playerView: PlayerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player_sheet, container, false)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val path = arguments?.getString("path")
        if (path.isNullOrBlank()) {
            dismiss()
            return
        }
        filePath = path

        // Don't let an accidental tap outside (or a stray swipe) close the player. It closes
        // only via the explicit close button or the system back gesture.
        (dialog as? BottomSheetDialog)?.apply {
            setCanceledOnTouchOutside(false)
            behavior.isDraggable = false
        }
        view.findViewById<View>(R.id.close_player).setOnClickListener { dismiss() }

        playerView = view.findViewById(R.id.player_view)

        // Connect to the playback service so audio keeps playing when the app is minimized.
        val token = SessionToken(
            requireContext().applicationContext,
            ComponentName(requireContext(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(requireContext().applicationContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!isAdded) return@addListener
            // future.get() throws if the bind failed (ExecutionException) or was cancelled while
            // connecting (CancellationException); don't let either crash the player.
            val mediaController = try {
                future.get()
            } catch (e: Exception) {
                return@addListener
            }
            controller = mediaController
            playerView?.player = mediaController

            // If the service is already playing this exact track (e.g. reopened after minimizing),
            // just attach to it; otherwise start it from the last saved position.
            if (mediaController.currentMediaItem?.mediaId != path) {
                val item = MediaItem.Builder()
                    .setMediaId(path)
                    .setUri(FileUtil.playableUriForPath(path))
                    .setMediaMetadata(buildMetadata())
                    .build()
                val savedPosition = positionPrefs()?.getLong(path, 0L) ?: 0L
                mediaController.setMediaItem(item, savedPosition)
                mediaController.prepare()
                mediaController.play()
            }
            // Run on the main thread: MediaController methods must be called on its application
            // thread, and directExecutor() would run this on whatever thread completes the future.
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Closing the player (close button / back) saves the spot and stops playback. Minimizing
        // the app does not dismiss the sheet, so playback keeps going in the service.
        savePosition()
        controller?.run {
            stop()
            clearMediaItems()
        }
        playerView?.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    private fun savePosition() {
        val path = filePath ?: return
        val c = controller ?: return
        val prefs = positionPrefs() ?: return
        val position = c.currentPosition
        val duration = c.duration
        // Forget the position once the track has effectively finished, so it restarts next time.
        val finished = c.playbackState == Player.STATE_ENDED ||
            (duration > 0 && position >= duration - 1500)
        prefs.edit().apply {
            if (finished || position <= 0) remove(path) else putLong(path, position)
        }.apply()
    }

    private fun positionPrefs() =
        context?.getSharedPreferences("channel_player_positions", Context.MODE_PRIVATE)

    /**
     * Title / channel / thumbnail shown on the lock screen and notification-shade media card
     * (and mirrored to external controllers such as Bluetooth, Android Auto and Wear). Media3's
     * default notification provider reads these straight off the MediaItem's metadata; the
     * artwork URI is fetched by the session's bitmap loader, so a remote thumbnail URL is fine.
     */
    private fun buildMetadata(): MediaMetadata {
        val args = arguments
        val title = args?.getString("title")
        val artist = args?.getString("artist")
        val thumb = args?.getString("thumb")
        return MediaMetadata.Builder().apply {
            if (!title.isNullOrBlank()) setTitle(title)
            if (!artist.isNullOrBlank()) setArtist(artist)
            if (!thumb.isNullOrBlank()) setArtworkUri(Uri.parse(thumb))
        }.build()
    }

    companion object {
        fun newInstance(
            path: String,
            title: String? = null,
            artist: String? = null,
            thumb: String? = null
        ): PlayerBottomSheetDialog {
            return PlayerBottomSheetDialog().apply {
                arguments = Bundle().apply {
                    putString("path", path)
                    putString("title", title)
                    putString("artist", artist)
                    putString("thumb", thumb)
                }
            }
        }
    }
}
