package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class CutVideoBottomSheetDialog(private val item: DownloadItem, private val listener: VideoCutListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil
    private lateinit var infoUtil: InfoUtil
    private lateinit var uiUtil: UiUtil
    private lateinit var player: Player


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
        val videoView = view.findViewById<PlayerView>(R.id.video_view)
        videoView.player = player

        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val rangeSlider = view.findViewById<RangeSlider>(R.id.rangeSlider)
        val fromTextInput = view.findViewById<TextInputLayout>(R.id.from_textinput)
        val toTextInput = view.findViewById<TextInputLayout>(R.id.to_textinput)

        val timeSeconds = convertStringToTimestamp(item.duration)

        if (item.downloadSections.isEmpty()){
            fromTextInput.editText!!.setText("00:00")
            toTextInput.editText!!.setText(item.duration)
        }else{
            val stamps = item.downloadSections.split("-")
            fromTextInput.editText!!.setText(stamps[0])
            toTextInput.editText!!.setText(stamps[1])

            val startSeconds = convertStringToTimestamp(stamps[0])
            val endSeconds = convertStringToTimestamp(stamps[1])

            val startValue = (startSeconds.toFloat() / timeSeconds) * 100
            val endValue = (endSeconds.toFloat() / timeSeconds) * 100

            rangeSlider.setValues(startValue, endValue)
        }

        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO){
                    infoUtil.getStreamingUrl(item.url)
                }
                progress.visibility = View.GONE
                player.addMediaItem(MediaItem.fromUri(Uri.parse(url)))
                player.prepare()
                player.seekTo((((rangeSlider.values[0].toInt() * timeSeconds) / 100) * 1000).toLong())
                player.play()
            }catch (e: Exception){
                progress.visibility = View.GONE
                videoView.visibility = View.GONE
                e.printStackTrace()
            }
        }

        //poll video progress
        lifecycleScope.launch {
            audioProgress(player).collect {
                val startTimestamp = (rangeSlider.values[0].toInt() * timeSeconds) / 100
                val endTimestamp = (rangeSlider.values[1].toInt() * timeSeconds) / 100
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


        rangeSlider.addOnChangeListener { rangeSlider, value, fromUser ->
            val values = rangeSlider.values
            val startTimestamp = (values[0].toInt() * timeSeconds) / 100
            val endTimestamp = (values[1].toInt() * timeSeconds) / 100

            val startTimestampString = infoUtil.formatIntegerDuration(startTimestamp)
            val endTimestampString = infoUtil.formatIntegerDuration(endTimestamp)

            fromTextInput.editText!!.setText(startTimestampString)
            toTextInput.editText!!.setText(endTimestampString)

            try {
                player.seekTo((startTimestamp * 1000).toLong())
            }catch (ignored: Exception) {}

            listener.onChangeCut(startTimestampString, endTimestampString)
        }

        fromTextInput.editText!!.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if ((event!!.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {

                    val startTimestamp = (rangeSlider.values[0].toInt() * timeSeconds) / 100
                    val endTimestamp = (rangeSlider.values[1].toInt() * timeSeconds) / 100

                    fromTextInput.editText!!.clearFocus()
                    val seconds = convertStringToTimestamp(fromTextInput.editText!!.text.toString())
                    if (seconds == 0) {
                        fromTextInput.editText!!.setText(infoUtil.formatIntegerDuration(startTimestamp))
                        return true
                    }

                    val startValue = (seconds.toFloat() / endTimestamp) * 100
                    if (startValue > 100){
                        fromTextInput.editText!!.setText(infoUtil.formatIntegerDuration(startTimestamp))
                        return true
                    }

                    rangeSlider.setValues(startValue, rangeSlider.values[1])
                    val startValueTimeStampSeconds = (startValue.toInt() * timeSeconds) / 100
                    fromTextInput.editText!!.setText(infoUtil.formatIntegerDuration(startValueTimeStampSeconds))

                    return true;
                }
                return false;
            }
        })


        toTextInput.editText!!.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if ((event!!.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {

                    val endTimestamp = (rangeSlider.values[1].toInt() * timeSeconds) / 100

                    toTextInput.editText!!.clearFocus()
                    val seconds = convertStringToTimestamp(toTextInput.editText!!.text.toString())
                    if (seconds == 0) {
                        toTextInput.editText!!.setText(infoUtil.formatIntegerDuration(endTimestamp))
                        return true
                    }

                    val endValue = (seconds.toFloat() / endTimestamp) * 100
                    if (endValue > 100 || endValue <= rangeSlider.values[0].toInt()){
                        toTextInput.editText!!.setText(infoUtil.formatIntegerDuration(endTimestamp))
                        return true
                    }

                    rangeSlider.setValues(rangeSlider.values[0], endValue)
                    val endValueTimeStampSeconds = (endValue.toInt() * timeSeconds) / 100
                    toTextInput.editText!!.setText(infoUtil.formatIntegerDuration(endValueTimeStampSeconds))

                    return true;
                }
                return false;
            }
        })

        val cancelBtn = view.findViewById<Button>(R.id.cancelButton)
        cancelBtn.setOnClickListener {
            listener.onCancelCut()
            dismiss()
        }
    }

    private fun audioProgress(player: Player?) = flow<Int> {
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
    fun onCancelCut()
    fun onChangeCut(from: String, to: String)
}
