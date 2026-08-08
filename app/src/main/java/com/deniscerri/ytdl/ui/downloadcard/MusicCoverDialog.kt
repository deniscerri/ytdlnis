package com.deniscerri.ytdl.ui.downloadcard

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.MusicCoverUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shows the album cover at a size worth looking at, and lets the user replace it with a
 * picture from the device.
 *
 * Picking only previews: the choice is not the users until they confirm it, so the cover on
 * the card stays untouched while they look at the alternative and change their mind.
 *
 * Deliberately a plain dialog owned by the audio card rather than a fragment of its own. The
 * card already owns the file picker and the card is what the result is for, so keeping both
 * in one place removes the hand off between fragments entirely: nothing here has a lifecycle
 * that can be stopped, capped or rebuilt while the picker is in front.
 */
class MusicCoverDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPickRequested: () -> Unit,
    private val onCoverConfirmed: (cover: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val view = LayoutInflater.from(context).inflate(R.layout.sheet_music_cover, null)

    private val preview: ShapeableImageView = view.findViewById(R.id.cover_preview)
    private val shimmer: ShimmerFrameLayout = view.findViewById(R.id.cover_shimmer)
    private val confirm: MaterialButton = view.findViewById(R.id.cover_confirm)

    /** The previewed image, still uncommitted until [confirm] is pressed. */
    private var picked: Uri? = null

    /** Renders the previewed cover, replaced whenever the user points at another one. */
    private var renderJob: Job? = null

    init {
        dialog.setContentView(view)
        view.findViewById<MaterialButton>(R.id.cover_choose).setOnClickListener { onPickRequested() }
        confirm.setOnClickListener {
            val uri = picked ?: return@setOnClickListener
            MusicCoverUtil.store(context, uri)?.let(onCoverConfirmed)
            dialog.dismiss()
        }
    }

    /** Opens on [cover], the artwork the card is currently carrying. */
    fun show(cover: String) {
        picked = null
        confirm.isEnabled = false
        render(cover)
        dialog.show()
    }

    /**
     * Shows an image the card picked up from the device. Reopens the sheet when the picker
     * closed it on the way, so a chosen image always lands somewhere the user can confirm it.
     */
    fun preview(uri: Uri) {
        picked = uri
        confirm.isEnabled = true
        render(uri.toString())
        if (!dialog.isShowing) dialog.show()
    }

    /**
     * Decoding is off the main thread, so a slow source never holds the sheet open empty. The
     * shimmer covers that gap, and covers it again whenever the user points at another image.
     */
    private fun render(cover: String) {
        renderJob?.cancel()
        shimmer.showShimmer(true)
        renderJob = scope.launch {
            val bitmap = MusicCoverUtil.preview(context, cover, PREVIEW_MAX_SIZE)
            if (bitmap == null) preview.setImageResource(R.drawable.ic_music)
            else preview.setImageBitmap(bitmap)
            shimmer.hideShimmer()
        }
    }

    companion object {
        /** Longest edge the preview decodes to, comfortably above any screen it is shown on. */
        private const val PREVIEW_MAX_SIZE = 1080
    }
}
