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
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ChapterItem
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem.fromUri
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import java.io.File
import java.lang.reflect.Type
import java.util.*
import kotlin.properties.Delegates


class CutVideoBottomSheetDialog(private val item: DownloadItem, private val listener: VideoCutListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil
    private lateinit var infoUtil: InfoUtil
    private lateinit var uiUtil: UiUtil
    private lateinit var player: Player
    
    private lateinit var cutSection : ConstraintLayout
    private lateinit var durationText: TextView
    private lateinit var progress : ProgressBar
    private lateinit var rangeSlider : RangeSlider
    private lateinit var fromTextInput : TextInputLayout
    private lateinit var toTextInput : TextInputLayout
    private lateinit var cancelBtn : Button
    private lateinit var okBtn : Button
    private lateinit var suggestedChips: ChipGroup
    private lateinit var suggestedChapters : LinearLayout
    
    private lateinit var cutListSection : LinearLayout
    private lateinit var newCutBtn : Button
    private lateinit var resetBtn : Button
    private lateinit var chipGroup : ChipGroup

    private var timeSeconds by Delegates.notNull<Int>()
    private lateinit var chapters: List<ChapterItem>
    private lateinit var selectedCuts: MutableList<String>

    private lateinit var cache: SimpleCache


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        infoUtil = InfoUtil(requireActivity().applicationContext)
    }


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.cut_video_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        val renderersFactory = DefaultRenderersFactory(requireContext().applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                var decoderInfos: MutableList<MediaCodecInfo> =
                    MediaCodecSelector.DEFAULT
                        .getDecoderInfos(
                            mimeType,
                            requiresSecureDecoder,
                            requiresTunnelingDecoder
                        )
                if (MimeTypes.VIDEO_H264 == mimeType) {
                    // copy the list because MediaCodecSelector.DEFAULT returns an unmodifiable list
                    decoderInfos = ArrayList(decoderInfos)
                    decoderInfos.reverse()
                }
                decoderInfos
            }

        val trackSelector = DefaultTrackSelector()
        val loadControl = DefaultLoadControl()

// Specify cache folder, my cache folder named media which is inside getCacheDir.
        val cacheFolder = File(requireContext().cacheDir, "media")

// Specify cache size and removing policies
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(1 * 1024 * 1024) // My cache size will be 1MB and it will automatically remove least recently used files if the size is reached out.

// Build cache
        val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(requireContext())
        cache = SimpleCache(cacheFolder, cacheEvictor, databaseProvider)

// Build data source factory with cache enabled, if data is available in cache it will return immediately, otherwise it will open a new connection to get the data.
        val defaultDataSourceFactory = DefaultDataSourceFactory(
            requireContext(),
            Util.getUserAgent(requireContext(), requireContext().getString(R.string.app_name))
        )

        val cacheDataSourceFactory =  CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)

        player = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
        val frame = view.findViewById<MaterialCardView>(R.id.frame_layout)
        val videoView = view.findViewById<PlayerView>(R.id.video_view)
        videoView.player = player
        timeSeconds = convertStringToTimestamp(item.duration)
        chapters = emptyList()

        //cut section
        cutSection = view.findViewById(R.id.cut_section)
        durationText = view.findViewById(R.id.durationText)
        durationText.text = ""
        progress = view.findViewById(R.id.progress)
        rangeSlider = view.findViewById(R.id.rangeSlider)
        fromTextInput = view.findViewById(R.id.from_textinput)
        toTextInput = view.findViewById(R.id.to_textinput)
        cancelBtn = view.findViewById(R.id.cancelButton)
        okBtn = view.findViewById(R.id.okButton)
        suggestedChips = view.findViewById(R.id.chapters)
        suggestedChapters = view.findViewById(R.id.suggested_cuts)
        initCutSection()

        //cut list section
        cutListSection = view.findViewById(R.id.list_section)
        newCutBtn = view.findViewById(R.id.new_cut)
        resetBtn = view.findViewById(R.id.reset_all)
        chipGroup = view.findViewById(R.id.cut_list_chip_group)

        selectedCuts = if (chipGroup.childCount == 0){
            mutableListOf()
        }else {
            chipGroup.children.forEach { c ->
                if ( ! (c as Chip).text.contains(":")) c.isEnabled = false
            }
            chipGroup.children.map { (it as Chip).text.toString() }.toMutableList()
        }

        initCutListSection()

        if (item.downloadSections.isBlank()) cutSection.visibility = View.VISIBLE
        else cutListSection.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO){
                    infoUtil.getStreamingUrlAndChapters(item.url)
                }
                if (data.isEmpty()) throw Exception("No Data found!")
                try{
                    val listType: Type = object : TypeToken<List<ChapterItem>>() {}.type
                    chapters = Gson().fromJson(data.first().toString(), listType)
                }catch (ignored: Exception) {}
                data.removeFirst()


                if (data.isEmpty()) throw Exception("No Streaming URL found!")
                if (data.size == 2){
                    val audioSource : MediaSource =
                        ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                            .createMediaSource(fromUri(Uri.parse(data[0])))
                    val videoSource: MediaSource =
                        ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                            .createMediaSource(fromUri(Uri.parse(data[1])))
                    (player as ExoPlayer).setMediaSource(MergingMediaSource(videoSource, audioSource))
                }else{
                    player.addMediaItem(fromUri(Uri.parse(data[0])))
                }
                player.addMediaItem(fromUri(Uri.parse(data[0])))

                progress.visibility = View.GONE
                populateSuggestedChapters()

                player.prepare()
                player.seekTo((((rangeSlider.valueFrom.toInt() * timeSeconds) / 100) * 1000).toLong())
                player.play()
            }catch (e: Exception){
                progress.visibility = View.GONE
                frame.visibility = View.GONE
                e.printStackTrace()
            }
        }
        //poll video progress
        lifecycleScope.launch {
            videoProgress(player).collect {
                val currentTime = infoUtil.formatIntegerDuration(it, Locale.US)
                durationText.text = "$currentTime / ${item.duration}"
                val startTimestamp = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                val endTimestamp = convertStringToTimestamp(toTextInput.editText!!.text.toString())
                if (it >= endTimestamp){
                    player.prepare()
                    player.seekTo((startTimestamp * 1000).toLong())
                }
            }
        }

        videoView.setOnClickListener {
            if (player.isPlaying) player.pause()
            else {
                player.play()
            }
        }

    }

    private fun initCutSection(){
        fromTextInput.editText!!.setText("0:00")
        toTextInput.editText!!.setText(item.duration)

        rangeSlider.addOnChangeListener { rangeSlider, value, fromUser ->
            val values = rangeSlider.values
            val startTimestamp = (values[0].toInt() * timeSeconds) / 100
            val endTimestamp = (values[1].toInt() * timeSeconds) / 100

            val startTimestampString = infoUtil.formatIntegerDuration(startTimestamp, Locale.US)
            val endTimestampString = infoUtil.formatIntegerDuration(endTimestamp, Locale.US)

            fromTextInput.editText!!.setText(startTimestampString)
            toTextInput.editText!!.setText(endTimestampString)


            okBtn.isEnabled = !(values[0] == 0F && values[1] == 100F)

            try {
                player.seekTo((startTimestamp * 1000).toLong())
                player.play()
            }catch (ignored: Exception) {}

        }

        fromTextInput.editText!!.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if ((event!!.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {

                    val startTimestamp = (rangeSlider.valueFrom.toInt() * timeSeconds) / 100
                    val endTimestamp = (rangeSlider.valueTo.toInt() * timeSeconds) / 100

                    fromTextInput.editText!!.clearFocus()
                    val seconds = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                    if (seconds == 0) {
                        fromTextInput.editText!!.setText(infoUtil.formatIntegerDuration(startTimestamp, Locale.US))
                        return true
                    }

                    val startValue = (seconds.toFloat() / endTimestamp) * 100
                    if (startValue > 100){
                        fromTextInput.editText!!.setText(infoUtil.formatIntegerDuration(startTimestamp, Locale.US))
                        return true
                    }

                    rangeSlider.setValues(startValue, rangeSlider.valueTo)
                    val startValueTimeStampSeconds = (startValue.toInt() * timeSeconds) / 100
                    fromTextInput.editText!!.setText(infoUtil.formatIntegerDuration(startValueTimeStampSeconds, Locale.US))

                    return true;
                }
                return false;
            }
        })


        toTextInput.editText!!.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if ((event!!.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {

                    val endTimestamp = (rangeSlider.valueTo.toInt() * timeSeconds) / 100

                    toTextInput.editText!!.clearFocus()
                    val seconds = convertStringToTimestamp(toTextInput.editText!!.text.toString())
                    if (seconds == 0) {
                        toTextInput.editText!!.setText(infoUtil.formatIntegerDuration(endTimestamp, Locale.US))
                        return true
                    }

                    val endValue = (seconds.toFloat() / endTimestamp) * 100
                    if (endValue > 100 || endValue <= rangeSlider.valueFrom.toInt()){
                        toTextInput.editText!!.setText(infoUtil.formatIntegerDuration(endTimestamp, Locale.US))
                        return true
                    }

                    rangeSlider.setValues(rangeSlider.valueFrom, endValue)
                    val endValueTimeStampSeconds = (endValue.toInt() * timeSeconds) / 100
                    toTextInput.editText!!.setText(infoUtil.formatIntegerDuration(endValueTimeStampSeconds, Locale.US))

                    return true;
                }
                return false;
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
            val chip = createChip("${fromTextInput.editText!!.text}-${toTextInput.editText!!.text}")
            chip.performClick()
            cutSection.visibility = View.GONE
            cutListSection.visibility = View.VISIBLE
        }

        populateSuggestedChapters()
    }

    private fun populateSuggestedChapters(){
        if (chapters.isEmpty()) suggestedChapters.visibility = View.GONE
        else {
            suggestedChapters.visibility = View.VISIBLE
            suggestedChips.removeAllViews()
            chapters.forEach {
                val chip = layoutInflater.inflate(R.layout.suggestion_chip, chipGroup, false) as Chip
                chip.text = it.title
                chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
                chip.isCheckedIconVisible = false
                suggestedChips.addView(chip)
                chip.setOnClickListener { c ->
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
        }

        resetBtn.setOnClickListener {
            chipGroup.removeAllViews()
            listener.onChangeCut(emptyList())
            player.stop()
            dismiss()
        }

        if (item.downloadSections.isNotBlank()){
            chipGroup.removeAllViews()
            item.downloadSections.split(";").forEachIndexed { index, it ->
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


    private fun videoProgress(player: Player?) = flow {
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
        player.stop()
        cache.release()
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("cutVideoSheet")!!).commit()
    }
}

interface VideoCutListener{
    fun onChangeCut(list: List<String>)
}
