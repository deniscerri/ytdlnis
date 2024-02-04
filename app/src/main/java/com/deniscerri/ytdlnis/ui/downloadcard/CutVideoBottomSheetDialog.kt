package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem.fromUri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ChapterItem
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.Extensions.setTextAndRecalculateWidth
import com.deniscerri.ytdlnis.util.Extensions.toStringDuration
import com.deniscerri.ytdlnis.util.VideoPlayerUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.util.*
import kotlin.properties.Delegates


class CutVideoBottomSheetDialog(private val item: DownloadItem, private val urls : String?, private var chapters: List<ChapterItem>?, private val listener: VideoCutListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var infoUtil: InfoUtil
    private lateinit var player: ExoPlayer
    
    private lateinit var cutSection : ConstraintLayout
    private lateinit var durationText: TextView
    private lateinit var progress : ProgressBar
    private lateinit var pauseBtn : MaterialButton
    private lateinit var rewindBtn : MaterialButton
    private lateinit var muteBtn : MaterialButton
    private lateinit var rangeSlider : RangeSlider
    private lateinit var fromTextInput : TextInputLayout
    private lateinit var toTextInput : TextInputLayout
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

    private var timeSeconds by Delegates.notNull<Int>()
    private lateinit var selectedCuts: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity().applicationContext)
    }


    @SuppressLint("RestrictedApi", "SetTextI18n")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.cut_video_sheet, null)
        dialog.setContentView(view)

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
        timeSeconds = convertStringToTimestamp(item.duration)
        if (chapters == null) chapters = emptyList()

        //cut section
        cutSection = view.findViewById(R.id.cut_section)
        durationText = view.findViewById(R.id.durationText)
        durationText.text = ""
        progress = view.findViewById(R.id.progress)
        pauseBtn = view.findViewById(R.id.pause)
        rewindBtn = view.findViewById(R.id.rewind)
        muteBtn = view.findViewById(R.id.mute)
        rangeSlider = view.findViewById(R.id.rangeSlider)
        fromTextInput = view.findViewById(R.id.from_textinput)
        toTextInput = view.findViewById(R.id.to_textinput)
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
                val data : MutableList<String?>  = withContext(Dispatchers.IO){
                    if (urls.isNullOrEmpty()) {
                        infoUtil.getStreamingUrlAndChapters(item.url)
                    }else {
                        urls.split("\n").toMutableList()
                    }
                }

                if (data.isEmpty()) throw Exception("No Data found!")
                if (chapters!!.isEmpty() && urls!!.isBlank()){
                    try{
                        val listType: Type = object : TypeToken<List<ChapterItem>>() {}.type
                        chapters = Gson().fromJson(data.first().toString(), listType)
                        data.removeFirst()
                    }catch (ignored: Exception) {
                        data.removeFirst()
                    }
                }

                if (data.isEmpty()) throw Exception("No Streaming URL found!")
                if (data.size == 2){
                    val audioSource : MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(data[0])))
                    val videoSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(data[1])))
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                }else{
                    player.addMediaItem(fromUri(Uri.parse(data[0])))
                }
                player.addMediaItem(fromUri(Uri.parse(data[0])))

                progress.visibility = View.GONE
                populateSuggestedChapters()

                player.prepare()
                player.play()
            }catch (e: Exception){
                progress.visibility = View.GONE
                frame.visibility = View.GONE
                e.printStackTrace()
            }
        }
        //poll video progress
        lifecycleScope.launch {
            videoProgress(player).collect { p ->
                val currentTime = p.toStringDuration(Locale.US)
                durationText.text = "$currentTime / ${item.duration}"
                val startTimestamp = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                if (toTextInput.editText!!.text.isNotBlank()){
                    val endTimestamp = convertStringToTimestamp(toTextInput.editText!!.text.toString())
                    if (p >= endTimestamp){
                        player.prepare()
                        player.seekTo((startTimestamp * 1000).toLong())
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
                val seconds = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                val endTimestamp = (rangeSlider.valueTo.toInt() * timeSeconds) / 100
                val startValue = (seconds.toFloat() / endTimestamp) * 100
                player.seekTo((((startValue * timeSeconds) / 100) * 1000).toLong())
                player.play()
            }catch (ignored: Exception) {}
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()

        forceKeyframes.isChecked = prefs.getBoolean("force_keyframes", false)
        forceKeyframes.setOnCheckedChangeListener { compoundButton, b ->
            editor.putBoolean("force_keyframes", forceKeyframes.isChecked)
            editor.apply()
        }

    }

    @SuppressLint("SetTextI18n")
    private fun initCutSection(){
        fromTextInput.editText!!.setTextAndRecalculateWidth("0:00")
        toTextInput.editText!!.setTextAndRecalculateWidth(item.duration)

        rangeSlider.setOnTouchListener { v, event -> // Handle touch events here
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
        rangeSlider.performClick()

        rangeSlider.setOnDragListener { view, dragEvent ->
            updateFromSlider()
            false
        }

        fromTextInput.editText!!.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if ((event!!.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {

                    var startTimestamp = (rangeSlider.valueFrom.toInt() * timeSeconds) / 100
                    val endTimestamp = (rangeSlider.valueTo.toInt() * timeSeconds) / 100

                    fromTextInput.editText!!.clearFocus()
                    var seconds = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                    val endSeconds = convertStringToTimestamp(toTextInput.editText!!.text.toString())

                    var startValue = (seconds.toFloat() / endTimestamp) * 100
                    val endValue = (endSeconds.toFloat() / endTimestamp) * 100

                    if (seconds == 0) {
                        fromTextInput.editText!!.setTextAndRecalculateWidth(startTimestamp.toStringDuration(Locale.US))
                    }else if (startValue > 100){
                        startTimestamp = 0
                        seconds = 0
                        fromTextInput.editText!!.setTextAndRecalculateWidth(startTimestamp.toStringDuration(Locale.US))
                        startValue = 0F
                    }

                    fromTextInput.editText!!.setTextAndRecalculateWidth(fromTextInput.editText!!.text.toString())

                    rangeSlider.setValues(startValue, endValue)
                    okBtn.isEnabled = startValue != 0F || endValue != 100F
                    try {
                        player.seekTo(seconds.toLong() * 1000)
                        player.play()
                    }catch (ignored: Exception) {}

                    return true
                }
                return false
            }
        })


        toTextInput.editText!!.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if ((event!!.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {

                    var endTimestamp = (rangeSlider.valueTo.toInt() * timeSeconds) / 100

                    toTextInput.editText!!.clearFocus()
                    val startSeconds = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                    val seconds = convertStringToTimestamp(toTextInput.editText!!.text.toString())

                    val startValue = (startSeconds.toFloat() / endTimestamp) * 100
                    var endValue = (seconds.toFloat() / endTimestamp) * 100
                    if (endValue > 100F){
                        endTimestamp = timeSeconds
                        toTextInput.editText!!.setTextAndRecalculateWidth(endTimestamp.toStringDuration(Locale.US))
                        endValue = 100F
                    }

                    if (seconds == 0) {
                        toTextInput.editText!!.setTextAndRecalculateWidth(endTimestamp.toStringDuration(Locale.US))
                    }else if (endValue <= rangeSlider.valueFrom.toInt()){
                        toTextInput.editText!!.setTextAndRecalculateWidth(endTimestamp.toStringDuration(Locale.US))
                    }

                    toTextInput.editText!!.setTextAndRecalculateWidth(toTextInput.editText!!.text.toString())

                    rangeSlider.setValues(startValue, endValue)
                    okBtn.isEnabled = startValue != 0F || endValue != 100F
                    try {
                        player.seekTo((seconds.toLong() - 4L) * 1000)
                        player.play()
                    }catch (ignored: Exception) {}

                    return true
                }
                return false
            }
        })

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
            val chip = createChip("${fromTextInput.editText!!.text}-${toTextInput.editText!!.text}")
            chip.performClick()
            cutSection.visibility = View.GONE
            cutListSection.visibility = View.VISIBLE
        }

        populateSuggestedChapters()
    }

    private fun updateFromSlider(){
        val values = rangeSlider.values
        val startTimestamp = (values[0].toInt() * timeSeconds) / 100
        val endTimestamp = (values[1].toInt() * timeSeconds) / 100

        val startTimestampString = startTimestamp.toStringDuration(Locale.US)
        val endTimestampString = endTimestamp.toStringDuration(Locale.US)

        fromTextInput.editText!!.setTextAndRecalculateWidth(startTimestampString)
        toTextInput.editText!!.setTextAndRecalculateWidth(endTimestampString)


        okBtn.isEnabled = values[0] != 0F || values[1] != 100F

        try {
            player.seekTo((startTimestamp * 1000).toLong())
            player.play()
        }catch (ignored: Exception) {}
    }

    private fun populateSuggestedChapters(){
        if (chapters!!.isEmpty()) suggestedChapters.visibility = View.GONE
        else {
            suggestedChapters.visibility = View.VISIBLE
            suggestedChips.removeAllViews()
            chapters!!.forEach {
                val chip = layoutInflater.inflate(R.layout.suggestion_chip, chipGroup, false) as Chip
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
                if (selectedCuts.contains(it.title)){
                    val idx = selectedCuts.indexOf(it.title)
                    val chipForDeletion = chipGroup.children.firstOrNull { cc -> (cc as Chip).text == it.title }
                    chipGroup.removeView(chipForDeletion)
                    createChapterChip(it, idx)
                }
            }
        }
    }

    private fun initCutListSection() {
        newCutBtn.setOnClickListener {
            cutSection.visibility = View.VISIBLE
            cutListSection.visibility = View.GONE
            rangeSlider.setValues(0F, 100F)
            player.seekTo(0)
            suggestedChips.children.apply {
                this.forEach {
                    it.isVisible = ! selectedCuts.contains((it as Chip).text)
                }
                suggestedLabel.isVisible = this.any { it.isVisible }
            }
        }

        resetBtn.setOnClickListener {
            chipGroup.removeAllViews()
            listener.onChangeCut(emptyList())
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
        val startTimestamp = convertStringToTimestamp(timestamp.split("-")[0].replace(";", ""))
        val endTimestamp = convertStringToTimestamp(timestamp.split("-")[1].replace(";", ""))

        val startingValue = ((startTimestamp.toFloat() / timeSeconds) * 100).toInt()
        val endingValue = ((endTimestamp.toFloat() / timeSeconds) * 100).toInt()

        val chip = layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
        chip.text = timestamp
        chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
        chip.isCheckedIconVisible = false
        chipGroup.addView(chip)
        selectedCuts.add(chip.text.toString())
        listener.onChangeCut(selectedCuts)

        chip.setOnClickListener {
            if (chip.isChecked) {
                rangeSlider.setValues(startingValue.toFloat(), endingValue.toFloat())
                player.prepare()
                player.seekTo((startTimestamp * 1000).toLong())
                player.play()
            }else {
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
                listener.onChangeCut(selectedCuts)
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
        chip.text = chapter.title
        chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
        chip.isCheckedIconVisible = false

        if (position != null) chipGroup.addView(chip, position)
        else chipGroup.addView(chip)

        if (! selectedCuts.contains(chapter.title))
            selectedCuts.add(chip.text.toString())

        listener.onChangeCut(selectedCuts)
        if (chapter.start_time == 0L && chapter.end_time == 0L) {
            chip.isEnabled = false
        }else{
            val startTimestamp = chapter.start_time.toInt()
            val endTimestamp =  chapter.end_time.toInt()

            val startingValue = ((startTimestamp.toFloat() / timeSeconds) * 100).toInt()
            val endingValue = ((endTimestamp.toFloat() / timeSeconds) * 100).toInt()

            chip.setOnClickListener {
                if (chip.isChecked) {
                    rangeSlider.setValues(startingValue.toFloat(), endingValue.toFloat())
                    player.prepare()
                    player.seekTo((((startingValue * timeSeconds) / 100) * 1000).toLong())
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
                    listener.onChangeCut(selectedCuts)
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


    private fun videoProgress(player: ExoPlayer?) = flow {
        while (true) {
            emit((player!!.currentPosition / 1000).toInt())
            delay(1000)
        }
    }.flowOn(Dispatchers.Main)

    private fun convertStringToTimestamp(duration: String): Int {
        return try {
            val timeArray = duration.split(":")
            var timeSeconds = timeArray[timeArray.lastIndex].toInt()
            var times = 60
            for (i in timeArray.lastIndex - 1 downTo 0) {
                timeSeconds += timeArray[i].toInt() * times
                times *= 60
            }
            timeSeconds
        }catch (e: Exception){
            e.printStackTrace()
            0
        }
    }

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