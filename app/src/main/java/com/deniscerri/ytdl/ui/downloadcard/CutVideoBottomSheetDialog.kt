package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem.fromUri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import com.deniscerri.ytdl.util.Extensions.setTextAndRecalculateWidth
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.Extensions.tryConvertToTimestamp
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.VideoPlayerUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max


class CutVideoBottomSheetDialog(private val _item: DownloadItem? = null, private val urls : String? = null, private var chapters: List<ChapterItem>? = null, private val listener: VideoCutListener? = null) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var player: ExoPlayer
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var cutSection : ConstraintLayout
    private lateinit var durationText: TextView
    private lateinit var progress : ProgressBar
    private lateinit var pauseBtn : MaterialButton
    private lateinit var rewindBtn : MaterialButton
    private lateinit var forwardBtn : MaterialButton
    private lateinit var muteBtn : MaterialButton
    private lateinit var rangeSlider : RangeSlider
    private lateinit var startTextInput : EditText
    private lateinit var endTextInput : EditText
    private lateinit var cancelBtn : Button
    private lateinit var okBtn : Button
    private lateinit var forceKeyframes: MaterialSwitch
    private lateinit var suggestedChips: ChipGroup
    private lateinit var suggestedChapters : LinearLayout
    
    private lateinit var cutListSection : LinearLayout
    private lateinit var newCutBtn : Button
    private lateinit var resetBtn : Button
    private lateinit var chipGroup : ChipGroup
    private lateinit var suggestedLabel : View
    private lateinit var item: DownloadItem

    /**
     * The duration of the video in milliseconds.
     */
    private var itemDurationTimestamp = 0L
    private lateinit var selectedCuts: MutableList<String>

    private var startTimestamp = 0L
    private var endTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        super.onCreate(savedInstanceState)
    }


    @SuppressLint("RestrictedApi", "SetTextI18n")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.cut_video_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        if (_item == null){
            this.dismiss()
            return
        }

        item = _item

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

        //cut section
        cutSection = view.findViewById(R.id.cut_section)
        durationText = view.findViewById(R.id.durationText)
        durationText.text = ""
        progress = view.findViewById(R.id.progress)
        pauseBtn = view.findViewById(R.id.pause)
        rewindBtn = view.findViewById(R.id.rewind)
        forwardBtn = view.findViewById(R.id.forward)
        muteBtn = view.findViewById(R.id.mute)
        rangeSlider = view.findViewById(R.id.rangeSlider)

        startTextInput = view.findViewById(R.id.from_textinput_edittext)
        startTextInput.keyListener = DigitsKeyListener.getInstance("0123456789:.")
        startTextInput.imeOptions = EditorInfo.IME_ACTION_DONE
        startTextInput.inputType = EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
        startTextInput.maxLines = 1
        endTextInput = view.findViewById(R.id.to_textinput_edittext)
        endTextInput.keyListener = DigitsKeyListener.getInstance("0123456789:.")
        endTextInput.imeOptions = EditorInfo.IME_ACTION_DONE
        endTextInput.inputType = EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
        endTextInput.maxLines = 1

        cancelBtn = view.findViewById(R.id.cancelButton)
        okBtn = view.findViewById(R.id.okButton)
        suggestedChips = view.findViewById(R.id.chapters)
        suggestedChapters = view.findViewById(R.id.suggested_cuts)
        suggestedLabel = view.findViewById(R.id.suggestedLabel)


        //cut list section
        cutListSection = view.findViewById(R.id.list_section)
        newCutBtn = view.findViewById(R.id.new_cut)
        resetBtn = view.findViewById(R.id.reset_all)
        chipGroup = view.findViewById(R.id.cut_list_chip_group)
        forceKeyframes = view.findViewById(R.id.force_keyframes)


        selectedCuts = if (chipGroup.childCount == 0){
            mutableListOf()
        }else {
            chipGroup.children.forEach { c ->
                if ( ! (c as Chip).text.contains(":")) c.isEnabled = false
            }
            chipGroup.children.map { (it as Chip).text.toString() }.toMutableList()
        }

        initCutSection()
        initCutListSection()

        if (item.downloadSections.isBlank()) cutSection.visibility = View.VISIBLE
        else cutListSection.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    if (urls.isNullOrEmpty()) {
                        resultViewModel.getStreamingUrlAndChapters(item.url)
                    }else{
                        Pair(urls.split("\n"), chapters)
                    }
                }

                if (data.first.isEmpty()) throw Exception("No Data found!")

                if (chapters!!.isEmpty() && urls!!.isBlank()){
                    chapters = data.second
                }

                val urls = data.first
                if (urls.size == 2){
                    val audioSource : MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(urls[0])))
                    val videoSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(urls[1])))
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                }else{
                    player.addMediaItem(fromUri(Uri.parse(urls[0])))
                }

                progress.visibility = View.GONE
                populateSuggestedChapters()

                player.prepare()
                player.play()
            }catch (e: Exception){
                progress.visibility = View.GONE
                frame.visibility = View.GONE
                videoView.visibility = View.GONE
                e.printStackTrace()
            }
        }
        //poll video progress
        val pollProgressInterval = 200L
        lifecycleScope.launch {
            videoProgress(player, pollProgressInterval).collect { currentTime ->
                durationText.text = "${currentTime.toStringTimeStamp()} / ${item.duration}"
                if (endTextInput.text.isNotBlank()) {
                    if (currentTime >= endTimestamp || (!player.isPlaying && currentTime >= endTimestamp - pollProgressInterval)) {
                        player.prepare()
                        player.seekTo(startTimestamp)
                    }
                }

            }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying){
                    pauseBtn.visibility = View.GONE
                }else{
                    pauseBtn.visibility = View.VISIBLE
                }
                super.onIsPlayingChanged(isPlaying)
            }
        })

        videoView.setOnClickListener {
            if (player.isPlaying){
                player.pause()
            }
            else {
                player.play()
            }
        }

        muteBtn.setOnClickListener {
            if (player.volume > 0F) {
                muteBtn.setIconResource(R.drawable.baseline_music_off_24)
                player.volume = 0F
            }else {
                muteBtn.setIconResource(R.drawable.ic_music)
                player.volume = 1F
            }
        }

        rewindBtn.setOnClickListener {
            try {
                val stmp = startTextInput.text.toString().convertToTimestamp()
                player.seekTo(stmp)
                player.play()
            }catch (ignored: Exception) {}
        }

        forwardBtn.setOnClickListener {
            kotlin.runCatching {
                player.seekTo(max(startTimestamp, endTimestamp - 1500))
                player.play()
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()

        forceKeyframes.isChecked = prefs.getBoolean("force_keyframes", false)
        forceKeyframes.setOnCheckedChangeListener { compoundButton, b ->
            editor.putBoolean("force_keyframes", forceKeyframes.isChecked)
            editor.apply()
        }

    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun initCutSection() {
        rangeSlider.valueFrom = 0F
        rangeSlider.valueTo = itemDurationTimestamp.toFloat()
        resetCutTimestamps()
        rangeSlider.setOnTouchListener { _, event -> // Handle touch events here
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    updateFromSlider()
                }

                MotionEvent.ACTION_UP -> {
                    updateFromSlider()
                }
            }
            // Return 'false' to allow the event to continue propagating or 'true' to consume it
            false
        }

//        fromTextInput.isFocusable = false
//        fromTextInput.isClickable = true
//        fromTextInput.setOnClickListener {
//            val currentMilliseconds = (it as EditText).text.toString().convertToTimestamp()
//            showTimestampBottomSheet(true, currentMilliseconds) { new ->
//                var newTimestamp = new
//                val endTimestamp = toTextInput.text.toString().convertToTimestamp()
//                fromTextInput.setTextAndRecalculateWidth(newTimestamp.toStringTimeStamp())
//
//                if (newTimestamp > itemDurationTimestamp) {
//                    newTimestamp = itemDurationTimestamp
//                }
//
//                rangeSlider.setValues((newTimestamp / 1000).toFloat(), (endTimestamp / 1000).toFloat())
//                okBtn.isEnabled = newTimestamp != 0L || endTimestamp != itemDurationTimestamp
//                try {
//                    player.seekTo(newTimestamp)
//                    player.play()
//                }catch (ignored: Exception) {}
//            }
//        }
//
//        toTextInput.isFocusable = false
//        toTextInput.isClickable = true
//        toTextInput.setOnClickListener {
//            val currentMilliseconds = (it as EditText).text.toString().convertToTimestamp()
//            showTimestampBottomSheet(false, currentMilliseconds) { new ->
//                var newTimestamp = new
//                val startTimestamp = fromTextInput.text.toString().convertToTimestamp()
//
//                if (newTimestamp > itemDurationTimestamp) {
//                    newTimestamp = itemDurationTimestamp
//                }
//
//                toTextInput.setTextAndRecalculateWidth(newTimestamp.toStringTimeStamp())
//                rangeSlider.setValues((startTimestamp / 1000).toFloat(), (newTimestamp / 1000).toFloat())
//                okBtn.isEnabled = startTimestamp != 0L || newTimestamp != itemDurationTimestamp
//                try {
//                    player.seekTo(newTimestamp - 1500)
//                    player.play()
//                }catch (ignored: Exception) {}
//            }
//        }

        startTextInput.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                updateFromStartTextInput(startTextInput.text.toString())
            }
        }
        startTextInput.setOnEditorActionListener { view, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event != null &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                updateFromStartTextInput(startTextInput.text.toString())
                startTextInput.clearFocus()
                false // close keyboard
            } else {
                false
            }
        }
        endTextInput.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                updateFromEndTextInput(endTextInput.text.toString())
            }
        }
        endTextInput.setOnEditorActionListener { view, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event != null &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                updateFromEndTextInput(endTextInput.text.toString())
                endTextInput.clearFocus()
                false // close keyboard
            } else {
                false
            }
        }

        cancelBtn.setOnClickListener {
            if (chipGroup.childCount == 0){
                player.stop()
                dismiss()
            }else{
                cutSection.visibility = View.GONE
                cutListSection.visibility = View.VISIBLE
            }
        }

        okBtn.isEnabled = false
        okBtn.setOnClickListener {
            forceKeyframes.isVisible = true
            updateFromStartTextInput(startTextInput.text.toString())
            updateFromEndTextInput(endTextInput.text.toString())
            val chip = createChip(
                startTimestamp.toStringTimeStamp(showMillisIfNonZero = true) +
                    "-${endTimestamp.toStringTimeStamp(showMillisIfNonZero = true)}")
            chip.performClick()
            cutSection.visibility = View.GONE
            cutListSection.visibility = View.VISIBLE
        }

        populateSuggestedChapters()
    }

    private fun setStartTimestamp(
        millis: Long,
        updateTextInput: Boolean = true,
        updateSlider: Boolean = true
    ) {
        startTimestamp = millis
        if (updateSlider) rangeSlider.setValues(millis.toFloat(), endTimestamp.toFloat())
        if (updateTextInput) {
            startTextInput.setTextAndRecalculateWidth(millis.toStringTimeStamp(forceMillis = true))
        }
        okBtn.isEnabled = startTimestamp != 0L || endTimestamp != itemDurationTimestamp
    }

    private fun setEndTimestamp(
        millis: Long,
        updateTextInput: Boolean = true,
        updateSlider: Boolean = true
    ) {
        endTimestamp = millis
        if (updateSlider) rangeSlider.setValues(startTimestamp.toFloat(), millis.toFloat())
        if (updateTextInput) {
            endTextInput.setTextAndRecalculateWidth(millis.toStringTimeStamp(forceMillis = true))
        }
        okBtn.isEnabled = startTimestamp != 0L || endTimestamp != itemDurationTimestamp
    }

    private fun setCutTimestamps(startTimestamp: Long, endTimestamp: Long) {
        setStartTimestamp(startTimestamp)
        setEndTimestamp(endTimestamp)
    }

    private fun resetCutTimestamps() = setCutTimestamps(0L, itemDurationTimestamp)


    private fun isValidTimestamp(millis: Long): Boolean =
        0L <= millis && millis <= itemDurationTimestamp

    private fun isValidStartTimeStamp(millis: Long):Boolean =
        isValidTimestamp(millis) && millis < endTimestamp

    private fun isValidEndTimeStamp(millis: Long):Boolean =
        isValidTimestamp(millis) && millis > startTimestamp

    private fun updateFromStartTextInput(text: String) {
        val timestamp = text.tryConvertToTimestamp()
        if (timestamp == null || timestamp == startTimestamp || !isValidStartTimeStamp(timestamp)) {
            startTextInput.setTextAndRecalculateWidth(startTimestamp.toStringTimeStamp(forceMillis = true))
            return
        }

        setStartTimestamp(timestamp, updateTextInput = true) // also update text input to normalize
        player.seekTo(timestamp)
        player.play()
    }

    private fun updateFromEndTextInput(text: String) {
        val timestamp = text.tryConvertToTimestamp()
        if (timestamp == null || timestamp == endTimestamp || !isValidEndTimeStamp(timestamp)) {
            endTextInput.setTextAndRecalculateWidth(endTimestamp.toStringTimeStamp(forceMillis = true))
            return
        }

        setEndTimestamp(timestamp, updateTextInput = true) // also update text input to normalize
        player.seekTo(timestamp - 1500)
        player.play()

    }

    private fun updateFromSlider() {
        val draggedFromBeginning = rangeSlider.focusedThumbIndex != 1

        if (draggedFromBeginning) {
            setStartTimestamp(rangeSlider.values[0].toLong(), updateSlider = false)
        } else {
            setEndTimestamp(rangeSlider.values[1].toLong(), updateSlider = false)
        }

        try {
            if (draggedFromBeginning) {
                player.seekTo(startTimestamp)
            } else {
                player.seekTo(max(startTimestamp, endTimestamp - 1500))
            }
            player.play()
        }catch (ignored: Exception) {}
    }

    private fun populateSuggestedChapters(){
        if (chapters!!.isEmpty()) suggestedChapters.visibility = View.GONE
        else {
            suggestedChapters.visibility = View.VISIBLE
            suggestedChips.removeAllViews()
            chapters!!.forEach {
                val chip = layoutInflater.inflate(R.layout.suggestion_chip, suggestedChips, false) as Chip
                chip.text = it.title
                chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
                chip.isCheckedIconVisible = false
                suggestedChips.addView(chip)
                chip.setOnClickListener { c ->
                    forceKeyframes.isVisible = true
                    val createdChip = createChapterChip(it, null)
                    createdChip.performClick()
                    cutSection.visibility = View.GONE
                    cutListSection.visibility = View.VISIBLE
                }

                //replace existing chip to enable click events
                val idx = selectedCuts.indexOfFirst { c -> c.contains(it.title) }
                if (idx > -1){
                    val chipForDeletion = chipGroup.children.firstOrNull { cc -> (cc as Chip).text.contains(it.title) }
                    chipForDeletion?.apply {
                        chipGroup.removeView(chipForDeletion)
                        createChapterChip(it, idx)
                    }

                }
            }
        }
    }

    private fun initCutListSection() {
        newCutBtn.setOnClickListener {
            cutSection.visibility = View.VISIBLE
            cutListSection.visibility = View.GONE
            resetCutTimestamps()
            player.seekTo(0)
            suggestedChips.children.apply {
                this.forEach {
                    it.isVisible = ! selectedCuts.any { c -> c.contains((it as Chip).text) }
                }
                suggestedLabel.isVisible = this.any { it.isVisible }
            }
        }

        resetBtn.setOnClickListener {
            chipGroup.removeAllViews()
            listener?.onChangeCut(emptyList())
            player.stop()
            dismiss()
        }

        if (item.downloadSections.isNotBlank()){
            forceKeyframes.isVisible = true
            chipGroup.removeAllViews()
            item.downloadSections.split(";").forEachIndexed { _, it ->
                if (it.isBlank()) return
                if (it.contains(":")) createChip(it.replace(";", ""))
                else createChapterChip(ChapterItem(0, 0, it), null)
            }
        }
    }

    private fun createChip(timestamp: String) : Chip {
        val startTimestamp = timestamp.split("-")[0].replace(";", "").convertToTimestamp()
        val endTimestamp = timestamp.split("-")[1].replace(";", "").convertToTimestamp()

        val chip = layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
        chip.text = timestamp
        chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
        chip.isCheckedIconVisible = false
        chipGroup.addView(chip)
        selectedCuts.add(chip.text.toString())
        listener?.onChangeCut(selectedCuts)

        chip.setOnClickListener {
            if (chip.isChecked) {
                setCutTimestamps(startTimestamp, endTimestamp)
                player.prepare()
                player.seekTo(startTimestamp)
                player.play()
            }else {
                // TODO reset timestamps?
                player.seekTo(0)
                player.pause()
            }
        }

        chip.setOnLongClickListener {
            UiUtil.showGenericDeleteDialog(requireContext(), chip.text.toString(), accepted = {
                player.seekTo(0)
                player.pause()
                chipGroup.removeView(chip)
                selectedCuts.remove(chip.text.toString())
                listener?.onChangeCut(selectedCuts)
                if (selectedCuts.isEmpty()){
                    player.stop()
                    dismiss()
                }
            })
            true
        }

        return chip
    }

    private fun createChapterChip(chapter: ChapterItem, position: Int?) : Chip {
        val chip = layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
        val timestamp = "${chapter.start_time.toInt().toStringDuration(Locale.US)}-${chapter.end_time.toInt().toStringDuration(Locale.US)} [${chapter.title}]"
        chip.text = timestamp
        chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
        chip.isCheckedIconVisible = false

        if (position != null) chipGroup.addView(chip, position)
        else chipGroup.addView(chip)


        if (! selectedCuts.contains(timestamp))
            selectedCuts.add(timestamp)

        listener?.onChangeCut(selectedCuts)
        if (chapter.start_time == 0L && chapter.end_time == 0L) {
            chip.isEnabled = false
        }else{
            val startTimestamp = chapter.start_time.toInt()
            val endTimestamp =  chapter.end_time.toInt()

            chip.setOnClickListener {
                if (chip.isChecked) {
                    rangeSlider.setValues(startTimestamp.toFloat(), endTimestamp.toFloat())
                    player.prepare()
                    player.seekTo(((startTimestamp) * 1000).toLong())
                    player.play()
                }else {
                    player.seekTo(0)
                    player.pause()
                }
            }

            chip.setOnLongClickListener {
                val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + chip.text + "\"!")
                deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                    player.seekTo(0)
                    player.pause()
                    chipGroup.removeView(chip)
                    selectedCuts.remove(chip.text.toString())
                    listener?.onChangeCut(selectedCuts)
                    if (selectedCuts.isEmpty()){
                        player.stop()
                        dismiss()
                    }
                }
                deleteDialog.show()
                true
            }
        }

        return chip
    }

    /**
     * Emits current video timestamp (in milliseconds) every `interval` milliseconds.
     */
    private fun videoProgress(player: ExoPlayer?, interval: Long = 200) = flow {
        while (true) {
            emit(player!!.currentPosition)
            delay(interval)
        }
    }.flowOn(Dispatchers.Main)

//    private fun showTimestampBottomSheet(startTimestamp: Boolean, currentTimestamp: Long, onChange: (value: Long) -> Unit){
//        val bottomSheet = BottomSheetDialog(requireContext())
//        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
//        bottomSheet.setContentView(R.layout.adjust_cut_timestamp)
//
//        val hours = bottomSheet.findViewById<NumberPicker>(R.id.hours)!!
//        val minutes = bottomSheet.findViewById<NumberPicker>(R.id.minutes)!!
//        val seconds = bottomSheet.findViewById<NumberPicker>(R.id.seconds)!!
//        val milliseconds = bottomSheet.findViewById<NumberPicker>(R.id.milliseconds)!!
//
//        val current = currentTimestamp.toTimePeriodsArray()
//
//        val setLimits = fun() {
//            val fromPeriods = fromTextInput.text.toString().convertToTimestamp().toTimePeriodsArray()
//            val toPeriods = toTextInput.text.toString().convertToTimestamp().toTimePeriodsArray()
//            val totalPeriods = itemDurationTimestamp.toTimePeriodsArray()
//
//            if (startTimestamp){
//                hours.minValue = 0
//                hours.maxValue = toPeriods[Extensions.Period.HOUR]!!
//
//                minutes.minValue = 0
//                if (hours.maxValue > 0){
//                    minutes.maxValue = 59
//                }else{
//                    minutes.maxValue = toPeriods[Extensions.Period.MINUTE]!!
//                }
//
//                seconds.minValue = 0
//                if (minutes.maxValue > 0){
//                    seconds.maxValue = 59
//                }else{
//                    seconds.maxValue = toPeriods[Extensions.Period.SECOND]!!
//                }
//            }else{
//                hours.minValue = fromPeriods[Extensions.Period.HOUR]!!
//                hours.maxValue = totalPeriods[Extensions.Period.HOUR]!!
//
//                if (hours.minValue < 1){
//                    minutes.minValue = fromPeriods[Extensions.Period.MINUTE]!!
//                    minutes.maxValue = totalPeriods[Extensions.Period.MINUTE]!!
//                }else{
//                    minutes.minValue = 0
//                    minutes.maxValue = 59
//                }
//
//                if (minutes.maxValue < 1){
//                    seconds.minValue = fromPeriods[Extensions.Period.SECOND]!!
//                    seconds.maxValue = totalPeriods[Extensions.Period.MINUTE]!!
//                }else{
//                    seconds.minValue = 0
//                    seconds.maxValue = 59
//                }
//
//            }
//        }
//
//        setLimits()
//        val timeSeconds = (itemDurationTimestamp / 1000).toInt()
//        hours.value = current[Extensions.Period.HOUR]!!
//        bottomSheet.findViewById<LinearLayout>(R.id.hours_container)?.isVisible = timeSeconds >= 3600
//
//        minutes.value = current[Extensions.Period.MINUTE]!!
//        bottomSheet.findViewById<LinearLayout>(R.id.minutes_container)?.isVisible = timeSeconds >= 60
//
//        seconds.value = current[Extensions.Period.SECOND]!!
//
//        milliseconds.minValue = 0
//        milliseconds.maxValue = 9
//        milliseconds.value = current[Extensions.Period.MILLISECOND]!!
//
//        val getTimeStamp = fun() : Long {
//            return "${hours.value}:${minutes.value}:${seconds.value}.${milliseconds.value}".convertToTimestamp()
//        }
//
//        val items = listOf(
//            hours, minutes, seconds, milliseconds
//        )
//
//        items.forEach {
//            it.setOnValueChangedListener { _, _, _ ->
//                setLimits()
//                onChange(getTimeStamp())
//            }
//        }
//
//        bottomSheet.show()
//        bottomSheet.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }


    private fun cleanUp(){
        kotlin.runCatching {
            player.stop()
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("cutVideoSheet")!!).commit()
        }
    }
}

interface VideoCutListener{
    fun onChangeCut(list: List<String>)
}