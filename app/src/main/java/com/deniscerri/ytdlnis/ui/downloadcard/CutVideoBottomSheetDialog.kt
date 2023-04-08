package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem.fromUri
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.properties.Delegates


class CutVideoBottomSheetDialog(private val item: DownloadItem, private val listener: VideoCutListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil
    private lateinit var infoUtil: InfoUtil
    private lateinit var uiUtil: UiUtil
    private lateinit var player: Player
    
    private lateinit var cutSection : ConstraintLayout
    private lateinit var progress : ProgressBar
    private lateinit var rangeSlider : RangeSlider
    private lateinit var fromTextInput : TextInputLayout
    private lateinit var toTextInput : TextInputLayout
    private lateinit var cancelBtn : Button
    private lateinit var okBtn : Button
    
    private lateinit var cutListSection : LinearLayout
    private lateinit var newCutBtn : Button
    private lateinit var chipGroup : ChipGroup

    private var timeSeconds by Delegates.notNull<Int>()


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

        player = ExoPlayer.Builder(requireContext()).build()
        val frame = view.findViewById<MaterialCardView>(R.id.frame_layout)
        val videoView = view.findViewById<PlayerView>(R.id.video_view)
        videoView.player = player
        timeSeconds = convertStringToTimestamp(item.duration)

        //cut section
        cutSection = view.findViewById(R.id.cut_section)
        progress = view.findViewById(R.id.progress)
        rangeSlider = view.findViewById(R.id.rangeSlider)
        fromTextInput = view.findViewById(R.id.from_textinput)
        toTextInput = view.findViewById(R.id.to_textinput)
        cancelBtn = view.findViewById(R.id.cancelButton)
        okBtn = view.findViewById(R.id.okButton)
        initCutSection()

        //cut list section
        cutListSection = view.findViewById(R.id.list_section)
        newCutBtn = view.findViewById(R.id.new_cut)
        chipGroup = view.findViewById(R.id.cut_list_chip_group)
        initCutListSection()

        if (item.downloadSections.isBlank()) cutSection.visibility = View.VISIBLE
        else cutListSection.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO){
                    infoUtil.getStreamingUrl(item.url)
                }

                if (url.isBlank()) throw Exception("No Streaming URL found!")

                val urls = url.split("\n")
                if (urls.size == 2){
                    val audioSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(urls[0])))
                    val videoSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(fromUri(Uri.parse(urls[1])))
                    (player as ExoPlayer).setMediaSource(MergingMediaSource(videoSource, audioSource))
                }else{
                    player.addMediaItem(fromUri(Uri.parse(urls[0])))
                }

                progress.visibility = View.GONE

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
                val startTimestamp = (rangeSlider.valueFrom.toInt() * timeSeconds) / 100
                val endTimestamp = (rangeSlider.valueTo.toInt() * timeSeconds) / 100
                if (it >= endTimestamp){
                    player.seekTo((startTimestamp * 1000).toLong())
                }
            }
        }

        videoView.setOnClickListener {
            if (player.isPlaying) player.stop()
            else {
                player.prepare()
                player.play()
            }
        }

    }

    private fun initCutSection(){
        if (item.downloadSections.isEmpty()){
            fromTextInput.editText!!.setText("0:00")
            toTextInput.editText!!.setText(item.duration)
        }else{
            val stamps = item.downloadSections.split("-")
            fromTextInput.editText!!.setText(stamps[0])
            toTextInput.editText!!.setText(stamps[1].replace(";", ""))

            val startSeconds = convertStringToTimestamp(stamps[0])
            val endSeconds = convertStringToTimestamp(stamps[1].replace(";", ""))

            val startValue = (startSeconds.toFloat() / timeSeconds) * 100
            val endValue = (endSeconds.toFloat() / timeSeconds) * 100

            rangeSlider.setValues(startValue, endValue)
        }


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
            cutSection.visibility = View.GONE
            cutListSection.visibility = View.VISIBLE
        }

        okBtn.isEnabled = false
        okBtn.setOnClickListener {
            val chip = createChip("${fromTextInput.editText!!.text}-${toTextInput.editText!!.text}")
            listener.onChangeCut(chipGroup.children.map { c -> (c as Chip).text.toString() })
            chip.performClick()
            player.seekTo((((rangeSlider.valueFrom.toInt() * timeSeconds) / 100) * 1000).toLong())
            player.play()

            cutSection.visibility = View.GONE
            cutListSection.visibility = View.VISIBLE
        }
    }

    private fun initCutListSection() {
        newCutBtn.setOnClickListener {
            cutSection.visibility = View.VISIBLE
            cutListSection.visibility = View.GONE
            rangeSlider.setValues(0F, 100F)
            player.seekTo(0)
        }

        if (item.downloadSections.isNotBlank()){
            chipGroup.removeAllViews()
            item.downloadSections.split(";").forEachIndexed { index, it ->
                if (it.isBlank()) return
                val startingValue = ((convertStringToTimestamp(it.split("-")[0]).toFloat() / timeSeconds) * 100).toInt()
                val endingValue = ((convertStringToTimestamp(it.split("-")[1].replace(";", "")).toFloat() / timeSeconds) * 100).toInt()

                createChip(it.replace(";", ""))
                if (index == 0) rangeSlider.setValues(startingValue.toFloat(), endingValue.toFloat())
            }
        }
    }

    private fun createChip(timestamp: String) : Chip {
        val startingValue = ((convertStringToTimestamp(timestamp.split("-")[0]).toFloat() / timeSeconds) * 100).toInt()
        val endingValue = ((convertStringToTimestamp(timestamp.split("-")[1].replace(";", "")).toFloat() / timeSeconds) * 100).toInt()

        val chip = layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
        chip.text = timestamp
        chip.isCheckedIconVisible = false
        chipGroup.addView(chip)
        listener.onChangeCut(chipGroup.children.map { c -> (c as Chip).text.toString() })

        chip.setOnClickListener {
            if (chip.isChecked) {
                rangeSlider.setValues(startingValue.toFloat(), endingValue.toFloat())
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
                listener.onChangeCut(chipGroup.children.map { c -> (c as Chip).text.toString() })
            }
            deleteDialog.show()
            true
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
            Log.e("aa", timeArray.toString())
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
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("cutVideoSheet")!!).commit()
    }
}

interface VideoCutListener{
    fun onChangeCut(list: Sequence<String>)
}
