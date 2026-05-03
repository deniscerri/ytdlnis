package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem.fromUri
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.VideoPlayerUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class CropVideoBottomSheetDialog(
    private val _item: DownloadItem? = null,
    private val urls: String? = null,
    private var chapters: List<ChapterItem>? = null,
    private val listener: VideoCropListener? = null
) : BottomSheetDialogFragment() {

    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var player: ExoPlayer
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var cropValuesSection: LinearLayout
    private lateinit var durationText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var pauseBtn: MaterialButton
    private lateinit var rewindBtn: MaterialButton
    private lateinit var forwardBtn: MaterialButton
    private lateinit var muteBtn: MaterialButton
    private lateinit var cancelBtn: Button
    private lateinit var resetBtn: Button
    private lateinit var okBtn: Button
    private lateinit var xEditText: EditText
    private lateinit var yEditText: EditText
    private lateinit var wEditText: EditText
    private lateinit var hEditText: EditText
    private lateinit var xInputLayout: TextInputLayout
    private lateinit var yInputLayout: TextInputLayout
    private lateinit var wInputLayout: TextInputLayout
    private lateinit var hInputLayout: TextInputLayout
    private lateinit var ratioLockSwitch: MaterialSwitch
    private lateinit var ratioWEditText: EditText
    private lateinit var ratioHEditText: EditText
    private lateinit var item: DownloadItem

    private var videoWidth = 0
    private var videoHeight = 0
    private var itemDurationTimestamp = 0L
    private var updatingFromTextInput = false
    private var enforcingRatio = false
    private var batchUpdating = 0
    private var cutStartTimestamp = 0L
    private var cutEndTimestamp = 0L
    private var hasCutRange = false

    private fun scaleX(): Float {
        val vw = cropOverlay.width
        return if (videoWidth > 0 && vw > 0) vw.toFloat() / videoWidth else 1f
    }

    private fun scaleY(): Float {
        val vh = cropOverlay.height
        return if (videoHeight > 0 && vh > 0) vh.toFloat() / videoHeight else 1f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.crop_video_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        if (_item == null) {
            this.dismiss()
            return
        }

        item = _item
        parseCutRange()

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels
        }

        player = VideoPlayerUtil.buildPlayer(requireContext())

        val frame = view.findViewById<MaterialCardView>(R.id.frame_layout)
        val videoView = view.findViewById<PlayerView>(R.id.video_view)
        videoView.player = player
        itemDurationTimestamp = item.duration.convertToTimestamp()
        if (chapters == null) chapters = emptyList()

        cropOverlay = view.findViewById(R.id.crop_overlay)
        cropOverlay.visibility = View.INVISIBLE
        cropValuesSection = view.findViewById(R.id.crop_values_section)
        cropValuesSection.visibility = View.GONE

        durationText = view.findViewById(R.id.durationText)
        durationText.text = ""
        progress = view.findViewById(R.id.progress)
        pauseBtn = view.findViewById(R.id.pause)
        rewindBtn = view.findViewById(R.id.rewind)
        forwardBtn = view.findViewById(R.id.forward)
        muteBtn = view.findViewById(R.id.mute)
        cancelBtn = view.findViewById(R.id.cancelButton)
        resetBtn = view.findViewById(R.id.resetButton)
        okBtn = view.findViewById(R.id.okButton)

        xEditText = view.findViewById(R.id.crop_x_edittext)
        yEditText = view.findViewById(R.id.crop_y_edittext)
        wEditText = view.findViewById(R.id.crop_w_edittext)
        hEditText = view.findViewById(R.id.crop_h_edittext)
        xInputLayout = view.findViewById(R.id.crop_x_input)
        yInputLayout = view.findViewById(R.id.crop_y_input)
        wInputLayout = view.findViewById(R.id.crop_w_input)
        hInputLayout = view.findViewById(R.id.crop_h_input)

        cropOverlay.onCropChanged = { updateTextInputsFromOverlay() }

        xEditText.doOnTextChanged { _, _, _, _ -> if (batchUpdating <= 0) { updateOverlayFromTextInputs(); validateCropInputs() } }
        yEditText.doOnTextChanged { _, _, _, _ -> if (batchUpdating <= 0) { updateOverlayFromTextInputs(); validateCropInputs() } }
        wEditText.doOnTextChanged { _, _, _, _ -> if (batchUpdating <= 0) { enforceRatioFromW(); updateOverlayFromTextInputs(); validateCropInputs() } }
        hEditText.doOnTextChanged { _, _, _, _ -> if (batchUpdating <= 0) { enforceRatioFromH(); updateOverlayFromTextInputs(); validateCropInputs() } }

        ratioLockSwitch = view.findViewById(R.id.ratio_lock_switch)
        ratioWEditText = view.findViewById(R.id.ratio_w_edittext)
        ratioHEditText = view.findViewById(R.id.ratio_h_edittext)
        ratioLockSwitch.isEnabled = false

        ratioLockSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                applyRatioLock()
                reshapeToRatio()
            } else {
                cropOverlay.aspectRatio = null
            }
        }

        val ratioTextWatcher = {
            val rw = ratioWEditText.text.toString().toIntOrNull()
            val rh = ratioHEditText.text.toString().toIntOrNull()
            val valid = rw != null && rh != null && rw > 0 && rh > 0
            ratioLockSwitch.isEnabled = valid
            if (!valid) {
                ratioLockSwitch.isChecked = false
                cropOverlay.aspectRatio = null
            } else if (ratioLockSwitch.isChecked) {
                applyRatioLock()
                reshapeToRatio()
            }
        }

        ratioWEditText.doOnTextChanged { _, _, _, _ -> ratioTextWatcher() }
        ratioHEditText.doOnTextChanged { _, _, _, _ -> ratioTextWatcher() }

        cancelBtn.setOnClickListener { dismiss() }

        resetBtn.setOnClickListener {
            listener?.onClearCrop()
            player.stop()
            dismiss()
        }
        resetBtn.isEnabled = item.cropValues.isNotBlank()

        okBtn.setOnClickListener {
            readTextFieldsToVideoCoords()?.let { (x, y, w, h) ->
                listener?.onChangeCrop(x, y, w, h)
            }
            player.stop()
            dismiss()
        }

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                if (videoWidth > 0 && videoHeight > 0) return  // already set from format
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                showOverlayAndLoad()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                pauseBtn.isVisible = !isPlaying
                super.onIsPlayingChanged(isPlaying)
            }
        })

        trySetResolutionFromFormat()

        videoView.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }

        muteBtn.setOnClickListener {
            if (player.volume > 0F) {
                muteBtn.setIconResource(R.drawable.baseline_music_off_24)
                player.volume = 0F
            } else {
                muteBtn.setIconResource(R.drawable.ic_music)
                player.volume = 1F
            }
        }

        rewindBtn.setOnClickListener {
            try {
                val seekTo = if (hasCutRange) cutStartTimestamp else 0
                player.seekTo(seekTo)
                player.play()
            } catch (ignored: Exception) {}
        }

        forwardBtn.setOnClickListener {
            kotlin.runCatching {
                val end = if (hasCutRange) cutEndTimestamp else itemDurationTimestamp
                player.seekTo(max(0, end - 1500))
                player.play()
            }
        }

        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    if (urls.isNullOrEmpty()) {
                        resultViewModel.getStreamingUrlAndChapters(item.url)
                    } else {
                        Pair(urls.split("\n"), chapters)
                    }
                }

                if (data.first.isEmpty()) throw Exception("No Data found!")

                val streamUrls = data.first
                if (streamUrls.size == 2) {
                    val audioSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(streamUrls[0])))
                    val videoSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(streamUrls[1])))
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                } else {
                    player.addMediaItem(fromUri(Uri.parse(streamUrls[0])))
                }

                progress.visibility = View.GONE
                player.prepare()
                player.play()
                if (hasCutRange) {
                    player.seekTo(cutStartTimestamp)
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                frame.visibility = View.GONE
                videoView.visibility = View.GONE
                e.printStackTrace()
            }
        }

        val pollProgressInterval = 200L
        lifecycleScope.launch {
            videoProgress(player, pollProgressInterval).collect { currentTime ->
                durationText.text = "${currentTime.toStringTimeStamp()} / ${item.duration}"
                if (hasCutRange && currentTime >= cutEndTimestamp) {
                    player.seekTo(cutStartTimestamp)
                }
            }
        }
    }

    private fun trySetResolutionFromFormat() {
        findVideoDimensions()?.let { (w, h) ->
            videoWidth = w
            videoHeight = h
            showOverlayAndLoad()
        }
    }

    private fun findVideoDimensions(): Pair<Int, Int>? {
        // Format width/height from JSON
        val fw = item.format.width ?: 0
        val fh = item.format.height ?: 0
        if (fw > 0 && fh > 0) return Pair(fw, fh)
        // Matching format_id in allFormats
        item.allFormats.find { it.format_id == item.format.format_id && (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
            ?.let { return Pair(it.width!!, it.height!!) }
        // Any format in allFormats
        item.allFormats.find { (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
            ?.let { return Pair(it.width!!, it.height!!) }
        // Parse format_note
        parseResolution(item.format.format_note)?.let { return it }
        // Parse any format_note in allFormats
        item.allFormats.firstNotNullOfOrNull { parseResolution(it.format_note) }?.let { return it }
        return null
    }

    private fun parseResolution(note: String): Pair<Int, Int>? {
        if (note.isBlank()) return null
        // "1920x1080" format
        Regex("""(\d+)x(\d+)""").find(note)?.let {
            return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        // "1080p", "1080p60", "1080p60 HDR" etc.
        Regex("""(\d+)p""").find(note)?.let {
            val h = it.groupValues[1].toInt()
            return Pair(h * 16 / 9, h)
        }
        // "hd1080", "hd720" etc.
        Regex("""hd(\d+)""", RegexOption.IGNORE_CASE).find(note)?.let {
            val h = it.groupValues[1].toInt()
            return Pair(h * 16 / 9, h)
        }
        return null
    }

    private fun showOverlayAndLoad() {
        cropOverlay.visibility = View.VISIBLE
        cropValuesSection.visibility = View.VISIBLE
        loadSavedCrop()
    }

    private fun applyRatioLock() {
        val rw = ratioWEditText.text.toString().toIntOrNull() ?: return
        val rh = ratioHEditText.text.toString().toIntOrNull() ?: return
        if (rw > 0 && rh > 0) {
            cropOverlay.aspectRatio = rw.toFloat() / rh
        }
    }

    private fun reshapeToRatio() {
        if (videoWidth <= 0 || videoHeight <= 0) return
        val ar = cropOverlay.aspectRatio ?: return
        val sx = scaleX()
        val sy = scaleY()
        if (sx <= 0 || sy <= 0) return

        val vx = (cropOverlay.cropLeftValue / sx).roundToInt()
        val vy = (cropOverlay.cropTopValue / sy).roundToInt()
        val vw = ((cropOverlay.cropRightValue - cropOverlay.cropLeftValue) / sx).roundToInt()
        val vh = ((cropOverlay.cropBottomValue - cropOverlay.cropTopValue) / sy).roundToInt()

        val cx = vx + vw / 2
        val cy = vy + vh / 2

        // Compute largest rectangle with this ratio that fits centered
        val maxW = minOf(cx, videoWidth - cx) * 2
        val maxH = minOf(cy, videoHeight - cy) * 2
        val newH = minOf(maxH, (maxW / ar).roundToInt())
        val newW = (newH * ar).roundToInt()
        if (newW <= 0 || newH <= 0) return

        val newX = (cx - newW / 2).coerceIn(0, videoWidth - newW)
        val newY = (cy - newH / 2).coerceIn(0, videoHeight - newH)

        applyVideoCoordsToOverlay(newX, newY, newW, newH)
    }

    private fun validateCropInputs(): Boolean {
        var valid = true

        fun error(til: TextInputLayout, msg: String?) {
            til.error = msg
            til.isErrorEnabled = msg != null
            if (msg != null) valid = false
        }

        // Clear all previous errors first
        error(xInputLayout, null)
        error(yInputLayout, null)
        error(wInputLayout, null)
        error(hInputLayout, null)

        val x = xEditText.text.toString().toIntOrNull()
        val y = yEditText.text.toString().toIntOrNull()
        val w = wEditText.text.toString().toIntOrNull()
        val h = hEditText.text.toString().toIntOrNull()

        if (x == null) { error(xInputLayout, context?.getString(R.string.crop_validation_required) ?: "Required"); return false }
        if (y == null) { error(yInputLayout, context?.getString(R.string.crop_validation_required) ?: "Required"); return false }
        if (w == null) { error(wInputLayout, context?.getString(R.string.crop_validation_required) ?: "Required"); return false }
        if (h == null) { error(hInputLayout, context?.getString(R.string.crop_validation_required) ?: "Required"); return false }

        if (w <= 0) error(wInputLayout, context?.getString(R.string.crop_validation_zero) ?: "Must be > 0")
        if (h <= 0) error(hInputLayout, context?.getString(R.string.crop_validation_zero) ?: "Must be > 0")

        if (videoWidth > 0 && x + w > videoWidth) {
            val msg = context?.getString(R.string.crop_validation_exceeds_w, videoWidth) ?: "X+W exceeds $videoWidth"
            error(xInputLayout, msg)
            error(wInputLayout, msg)
        }
        if (videoHeight > 0 && y + h > videoHeight) {
            val msg = context?.getString(R.string.crop_validation_exceeds_h, videoHeight) ?: "Y+H exceeds $videoHeight"
            error(yInputLayout, msg)
            error(hInputLayout, msg)
        }

        okBtn.isEnabled = valid
        return valid
    }

    private fun enforceRatioFromW() {
        if (enforcingRatio || !ratioLockSwitch.isChecked) return
        val ar = cropOverlay.aspectRatio ?: return
        val w = wEditText.text.toString().toIntOrNull() ?: return
        enforcingRatio = true
        hEditText.setText((w / ar).roundToInt().toString())
        enforcingRatio = false
    }

    private fun enforceRatioFromH() {
        if (enforcingRatio || !ratioLockSwitch.isChecked) return
        val ar = cropOverlay.aspectRatio ?: return
        val h = hEditText.text.toString().toIntOrNull() ?: return
        enforcingRatio = true
        wEditText.setText((h * ar).roundToInt().toString())
        enforcingRatio = false
    }

    private fun parseCutRange() {
        val sections = item.downloadSections
        if (sections.isBlank()) return
        val firstSection = sections.split(";").firstOrNull() ?: return
        val parts = firstSection.split(" ")
        val timeRange = parts[0].split("-")
        if (timeRange.size != 2) return
        kotlin.runCatching {
            cutStartTimestamp = timeRange[0].convertToTimestamp()
            cutEndTimestamp = timeRange[1].convertToTimestamp()
            if (cutStartTimestamp >= 0 && cutEndTimestamp > cutStartTimestamp) {
                hasCutRange = true
            }
        }
    }

    private fun loadSavedCrop() {
        cropOverlay.post {
            if (cropOverlay.width <= 0 || cropOverlay.height <= 0) return@post
            val existingCrop = item.cropValues
            if (existingCrop.isNotBlank()) {
                val parts = existingCrop.split(":")
                if (parts.size >= 4) {
                    val x = parts[0].toIntOrNull() ?: 0
                    val y = parts[1].toIntOrNull() ?: 0
                    val w = parts[2].toIntOrNull() ?: videoWidth
                    val h = parts[3].toIntOrNull() ?: videoHeight
                    applyVideoCoordsToOverlay(x, y, w, h)
                }
            } else {
                applyVideoCoordsToOverlay(0, 0, videoWidth, videoHeight)
            }
            validateCropInputs()
        }
    }

    private fun applyVideoCoordsToOverlay(vx: Int, vy: Int, vw: Int, vh: Int) {
        val sx = scaleX()
        val sy = scaleY()
        cropOverlay.setCrop(vx * sx, vy * sy, (vx + vw) * sx, (vy + vh) * sy)
        refreshTextInputs(vx, vy, vw, vh)
    }

    private fun updateTextInputsFromOverlay() {
        if (updatingFromTextInput) return
        val sx = scaleX()
        val sy = scaleY()
        if (sx <= 0 || sy <= 0) return
        val x = (cropOverlay.cropLeftValue / sx).roundToInt()
        val y = (cropOverlay.cropTopValue / sy).roundToInt()
        val w = ((cropOverlay.cropRightValue - cropOverlay.cropLeftValue) / sx).roundToInt()
        val h = ((cropOverlay.cropBottomValue - cropOverlay.cropTopValue) / sy).roundToInt()
        refreshTextInputs(x, y, w, h)
    }

    private fun refreshTextInputs(x: Int, y: Int, w: Int, h: Int) {
        batchUpdating++
        xEditText.setText(x.toString())
        yEditText.setText(y.toString())
        wEditText.setText(w.toString())
        hEditText.setText(h.toString())
        batchUpdating--
        validateCropInputs()
    }

    private fun updateOverlayFromTextInputs() {
        if (cropOverlay.width <= 0 || cropOverlay.height <= 0) return
        val x = xEditText.text.toString().toIntOrNull() ?: return
        val y = yEditText.text.toString().toIntOrNull() ?: return
        val w = wEditText.text.toString().toIntOrNull() ?: return
        val h = hEditText.text.toString().toIntOrNull() ?: return
        updatingFromTextInput = true
        val sx = scaleX()
        val sy = scaleY()
        cropOverlay.setCrop(x * sx, y * sy, (x + w) * sx, (y + h) * sy)
        updatingFromTextInput = false
    }

    private fun readTextFieldsToVideoCoords(): Quadruple? {
        val x = xEditText.text.toString().toIntOrNull() ?: return null
        val y = yEditText.text.toString().toIntOrNull() ?: return null
        val w = wEditText.text.toString().toIntOrNull() ?: return null
        val h = hEditText.text.toString().toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return Quadruple(x, y, w, h)
    }

    private data class Quadruple(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun videoProgress(player: ExoPlayer?, interval: Long = 200) = flow {
        while (true) {
            emit(player!!.currentPosition)
            delay(interval)
        }
    }.flowOn(Dispatchers.Main)

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }

    private fun cleanUp() {
        kotlin.runCatching {
            player.stop()
            parentFragmentManager.beginTransaction()
                .remove(parentFragmentManager.findFragmentByTag("cropVideoSheet")!!).commit()
        }
    }
}

interface VideoCropListener {
    fun onChangeCrop(x: Int, y: Int, w: Int, h: Int)
    fun onClearCrop()
}
