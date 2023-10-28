package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class FormatSelectionBottomSheetDialog(private val items: List<DownloadItem?>, private var formats: List<List<Format>>, private val listener: OnFormatClickListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var infoUtil: InfoUtil
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var formatCollection: MutableList<List<Format>>
    private lateinit var chosenFormats: List<Format>
    private lateinit var selectedVideo : Format
    private lateinit var selectedAudios : MutableList<Format>

    private lateinit var videoFormatList : LinearLayout
    private lateinit var audioFormatList : LinearLayout
    private lateinit var okBtn : Button
    private lateinit var videoTitle : TextView
    private lateinit var audioTitle : TextView

    private lateinit var sortBy : FormatSorting

    private lateinit var continueInBackgroundSnackBar : Snackbar
    private lateinit var view: View

    enum class FormatSorting {
        filesize, container, codec, id
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        formatCollection = mutableListOf()
        chosenFormats = listOf()
        selectedAudios = mutableListOf()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        view = LayoutInflater.from(context).inflate(R.layout.format_select_bottom_sheet, null)
        dialog.setContentView(view)

        sortBy = FormatSorting.valueOf(sharedPreferences.getString("format_order", "filesize")!!)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        val formatListLinearLayout = view.findViewById<LinearLayout>(R.id.format_list_linear_layout)
        val shimmers = view.findViewById<ShimmerFrameLayout>(R.id.format_list_shimmer)

        videoFormatList = view.findViewById(R.id.video_linear_layout)
        audioFormatList = view.findViewById(R.id.audio_linear_layout)
        videoTitle = view.findViewById(R.id.video_title)
        audioTitle = view.findViewById(R.id.audio_title)
        okBtn = view.findViewById(R.id.format_ok)

        shimmers.visibility = View.GONE
        val hasGenericFormats = formats.first().isEmpty() || formats.last().any { it.format_id == "best" }
        if (items.size > 1){

            if (!hasGenericFormats){
                formatCollection.addAll(formats)
                val flattenFormats = formats.flatten()
                val commonFormats = flattenFormats.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flattenFormats.first { f -> f.format_id == it.key } }.map { it.value }
                chosenFormats = commonFormats.mapTo(mutableListOf()) {it.copy()}
                chosenFormats = when(items.first()?.type){
                    Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                    else -> chosenFormats
                }
                chosenFormats.forEach {
                    it.filesize =
                        flattenFormats.filter { f -> f.format_id == it.format_id }
                            .sumOf { itt -> itt.filesize }
                }
            }else{
                chosenFormats = formats.flatten()
            }
            addFormatsToView()
        }else{
            chosenFormats = formats.flatten()
            if(!hasGenericFormats){
                if(items.first()?.type == Type.audio){
                    chosenFormats =  chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                }
            }
            addFormatsToView()
        }

        val refreshBtn = view.findViewById<Button>(R.id.format_refresh)
        if (!hasGenericFormats || items.isEmpty()) refreshBtn.visibility = View.GONE


        refreshBtn.setOnClickListener {
           lifecycleScope.launch {
               if (items.size > 10){
                   continueInBackgroundSnackBar = Snackbar.make(view, R.string.update_formats_background, Snackbar.LENGTH_LONG)
                   continueInBackgroundSnackBar.setAction(R.string.ok) {
                       listener.onContinueOnBackground()
                       this@FormatSelectionBottomSheetDialog.dismiss()
                   }
                   continueInBackgroundSnackBar.show()
               }


               chosenFormats = emptyList()
               try {
                   refreshBtn.isEnabled = false
                   formatListLinearLayout.visibility = View.GONE
                   shimmers.visibility = View.VISIBLE
                   shimmers.startShimmer()

                   //simple download
                   if (items.size == 1){
                       val res = withContext(Dispatchers.IO){
                           infoUtil.getFormats(items.first()!!.url)
                       }
                       res.filter { it.format_note != "storyboard" }
                       chosenFormats = if(items.first()?.type == Type.audio){
                           res.filter { it.format_note.contains("audio", ignoreCase = true) }
                       }else{
                           res
                       }
                       if (chosenFormats.isEmpty()) throw Exception()

                       formats = listOf(res)

                   //list format filtering
                   }else{
                       var progress = "0/${items.size}"
                       formatCollection.clear()
                       refreshBtn.text = progress
                       withContext(Dispatchers.IO){
                           infoUtil.getFormatsMultiple(items.map { it!!.url }) {
                               lifecycleScope.launch(Dispatchers.Main){
                                   progress = "${formatCollection.size}/${items.size}"
                                   refreshBtn.text = progress
                                   formatCollection.add(it)
                               }
                           }
                       }
                       formats = formatCollection
                       val flatFormatCollection = formatCollection.flatten()
                       val commonFormats = flatFormatCollection.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }.map { it.value }
                       chosenFormats = commonFormats.filter { it.filesize != 0L }.mapTo(mutableListOf()) {it.copy()}
                       chosenFormats = when(items.first()?.type){
                           Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                           else -> chosenFormats
                       }
                       if (chosenFormats.isEmpty()) throw Exception()
                       chosenFormats.forEach {
                           it.filesize =
                               flatFormatCollection.filter { f -> f.format_id == it.format_id }
                                   .sumOf { itt -> itt.filesize }
                       }
                   }
                   shimmers.visibility = View.GONE
                   shimmers.stopShimmer()
                   addFormatsToView()
                   refreshBtn.visibility = View.GONE
                   formatListLinearLayout.visibility = View.VISIBLE
               }catch (e: Exception){
                   runCatching {
                       refreshBtn.isEnabled = true
                       refreshBtn.text = getString(R.string.update_formats)
                       formatListLinearLayout.visibility = View.VISIBLE
                       shimmers.visibility = View.GONE
                       shimmers.stopShimmer()

                       e.printStackTrace()
                       Toast.makeText(context, getString(R.string.error_updating_formats), Toast.LENGTH_SHORT).show()
                   }
               }
           }
        }

        okBtn.setOnClickListener {
            if (!::selectedVideo.isInitialized) {
                selectedVideo =
                    chosenFormats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.maxByOrNull { it.filesize }!!
            }
            returnFormats()
            dismiss()
        }

//        if (sharedPreferences.getBoolean("update_formats", false) && refreshBtn.isVisible && items.size == 1){
//            refreshBtn.performClick()
//        }
    }

    private fun returnFormats(){
        //simple video format selection
        if (items.size == 1){
            listener.onFormatClick(formats, listOf(FormatTuple(selectedVideo, selectedAudios)))
        }else{
            //playlist format selection
            val selectedFormats = mutableListOf<Format>()
            formatCollection.forEach {
                selectedFormats.add(it.first{ f -> f.format_id == selectedVideo.format_id})
            }
            if (selectedFormats.isEmpty()) {
                items.forEach { _ ->
                    selectedFormats.add(selectedVideo)
                }
            }
            listener.onFormatClick(formats, selectedFormats.map { FormatTuple(it, selectedAudios) })
        }

    }

    private fun addFormatsToView(){
        //sort
        var finalFormats: List<Format> = when(sortBy){
            FormatSorting.container -> chosenFormats.groupBy { it.container }.flatMap { it.value }
            FormatSorting.id -> chosenFormats.sortedBy { it.format_id }
            FormatSorting.codec -> {
                val codecOrder = resources.getStringArray(R.array.video_codec_values).toMutableList()
                codecOrder.removeFirst()
                chosenFormats.groupBy { format -> codecOrder.indexOfFirst { format.vcodec.startsWith(it) } }
                    .flatMap {
                        it.value.sortedBy { l -> l.filesize }
                    }
            }
            FormatSorting.filesize -> chosenFormats
        }

        val canMultiSelectAudio = items.first()?.type == Type.video && finalFormats.find { it.format_note.contains("audio", ignoreCase = true) } != null
        videoFormatList.removeAllViews()
        audioFormatList.removeAllViews()

        if (!canMultiSelectAudio) {
            audioFormatList.visibility = View.GONE
            videoTitle.visibility = View.GONE
            audioTitle.visibility = View.GONE
            okBtn.visibility = View.GONE
        }else{
            if (finalFormats.count { it.vcodec.isBlank() || it.vcodec == "none" } == 0){
                audioFormatList.visibility = View.GONE
                audioTitle.visibility = View.GONE
                videoTitle.visibility = View.GONE
                okBtn.visibility = View.GONE
            }else{
                audioFormatList.visibility = View.VISIBLE
                audioTitle.visibility = View.VISIBLE
                videoTitle.visibility = View.VISIBLE
                okBtn.visibility = View.VISIBLE
            }
        }

        if (finalFormats.isEmpty()){
            finalFormats = if (items.first()?.type == Type.audio){
                infoUtil.getGenericAudioFormats(requireContext().resources)
            }else{
                infoUtil.getGenericVideoFormats(requireContext().resources)
            }
        }

        for (i in 0.. finalFormats.lastIndex){
            val format = finalFormats[i]
            val formatItem = LayoutInflater.from(context).inflate(R.layout.format_item, null)
            formatItem.tag = "${format.format_id}${format.format_note}"
            UiUtil.populateFormatCard(requireContext(), formatItem as MaterialCardView, format, null)
            formatItem.setOnClickListener{ clickedformat ->
                //if the context is behind a video or playlist, allow the ability to multiselect audio formats
                if (canMultiSelectAudio){
                    val clickedCard = (clickedformat as MaterialCardView)
                    if (format.vcodec.isNotBlank() && format.vcodec != "none") {
                        if (clickedCard.isChecked) {
                            returnFormats()
                            dismiss()
                        }
                        videoFormatList.forEach { (it as MaterialCardView).isChecked = false }
                        selectedVideo = format
                        clickedCard.isChecked = true
                    }else{
                        if(selectedAudios.contains(format)) {
                            selectedAudios.remove(format)
                        } else {
                            selectedAudios.add(format)
                        }
                    }
                    audioFormatList.forEach { (it as MaterialCardView).isChecked = false }
                    audioFormatList.forEach {
                        (it as MaterialCardView).isChecked = selectedAudios.map { a -> "${a.format_id}${a.format_note}" }.contains(it.tag)
                    }
                }else{
                    if (items.size == 1){
                        listener.onFormatClick(formats, listOf(FormatTuple(format, null)))
                    }else{
                        val selectedFormats = mutableListOf<Format>()
                        formatCollection.forEach {
                            selectedFormats.add(it.first{ f -> f.format_id == format.format_id})
                        }
                        if (selectedFormats.isEmpty()) {
                            items.forEach { _ ->
                                selectedFormats.add(format)
                            }
                        }
                        listener.onFormatClick(formats, selectedFormats.map { FormatTuple(it, null) })
                    }
                    dismiss()
                }
            }
            formatItem.setOnLongClickListener {
                UiUtil.showFormatDetails(format, requireActivity())
                true
            }

            if (canMultiSelectAudio){
                if (format.vcodec.isNotBlank() && format.vcodec != "none") videoFormatList.addView(formatItem)
                else audioFormatList.addView(formatItem)
            }else{
                videoFormatList.addView(formatItem)
            }
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
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("formatSheet")!!).commit()
        }
    }
}

interface OnFormatClickListener{
    fun onFormatClick(allFormats: List<List<Format>>, item: List<FormatTuple>)
    fun onContinueOnBackground() {}
}

class FormatTuple internal constructor(
    var format: Format,
    var audioFormats: List<Format>?
)