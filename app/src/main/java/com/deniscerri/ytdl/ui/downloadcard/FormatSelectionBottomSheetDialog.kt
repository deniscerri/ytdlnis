package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.FormatSorter
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.format
import java.util.regex.Pattern


class FormatSelectionBottomSheetDialog(
    private val _items: List<DownloadItem?>? = null,
    private val _listener: OnFormatClickListener? = null,
    private val _multipleFormatsListener: OnMultipleFormatClickListener? = null
) : BottomSheetDialogFragment() {

    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var infoUtil: InfoUtil
    private lateinit var view: View
    private lateinit var continueInBackgroundSnackBar : Snackbar
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var videoFormatList : LinearLayout
    private lateinit var audioFormatList : LinearLayout
    private lateinit var okBtn : Button
    private lateinit var refreshBtn: Button
    private lateinit var videoTitle : TextView
    private lateinit var audioTitle : TextView

    private lateinit var chosenFormats: List<Format>
    private var selectedVideo : Format? = null
    private lateinit var selectedAudios : MutableList<Format>

    private lateinit var sortBy : FormatSorting
    private lateinit var filterBy : FormatCategory
    private lateinit var filterBtn : Button

    private var updateFormatsJob: Job? = null
    private var isMissingFormats: Boolean = false

    private lateinit var items: MutableList<DownloadItem?>
    private lateinit var formats: MutableList<Format>
    private lateinit var listener: OnFormatClickListener
    private lateinit var multipleFormatsListener: OnMultipleFormatClickListener

    private var currentFormatSource : String? = null

    private lateinit var genericAudioFormats : List<Format>
    private lateinit var genericVideoFormats : List<Format>

    enum class FormatSorting {
        filesize, container, codec, id
    }

    enum class FormatCategory {
        ALL, SUGGESTED, SMALLEST, GENERIC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        chosenFormats = listOf()
        selectedAudios = mutableListOf()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
    }


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        view = LayoutInflater.from(context).inflate(R.layout.format_select_bottom_sheet, null)
        dialog.setContentView(view)

        if (_items == null){
            this.dismiss()
            return
        }

        items = _items.distinctBy { it!!.url }.toMutableList()
        if (items.size == 1) {
            formats = items.first()!!.allFormats
        }else{
            val flatFormatCollection = items.map { it!!.allFormats }.flatten()
            formats = flatFormatCollection.groupingBy { it.format_id }.eachCount()
                .filter { it.value == items.size }
                .mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }
                .map { it.value }.toMutableList()
        }

        _listener?.apply {
            listener = this
        }

        _multipleFormatsListener?.apply {
            multipleFormatsListener = this
        }

        genericAudioFormats = infoUtil.getGenericAudioFormats(requireContext().resources)
        genericVideoFormats = infoUtil.getGenericVideoFormats(requireContext().resources)

        sortBy = FormatSorting.valueOf(sharedPreferences.getString("format_order", "filesize")!!)
        filterBy = FormatCategory.valueOf(sharedPreferences.getString("format_filter", "ALL")!!)
        filterBtn = view.findViewById(R.id.format_filter)

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
        isMissingFormats = formats.isEmpty() && items.any { it!!.allFormats.isEmpty() }

        if (items.size > 1){
            if (!isMissingFormats){
                chosenFormats = formats.mapTo(mutableListOf()) {it.copy()}
                chosenFormats = when(items.first()?.type){
                    Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                    else -> chosenFormats
                }
                chosenFormats.forEach {
                    it.filesize = items.map { itm -> itm!!.allFormats }.flatten().filter { f -> f.format_id == it.format_id }.sumOf { itt -> itt.filesize }
                }
            }else{
                chosenFormats = formats
            }
            addFormatsToView()
        }else{
            chosenFormats = formats
            if(!isMissingFormats){
                if(items.first()?.type == Type.audio){
                    chosenFormats =  chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                }
            }
            addFormatsToView()
        }

        refreshBtn = view.findViewById(R.id.format_refresh)
        filterBtn.isVisible = chosenFormats.isNotEmpty() || items.all { it!!.url.isYoutubeURL() }
        if (!isMissingFormats || items.isEmpty() || items.first()?.url?.isEmpty() == true) {
            refreshBtn.visibility = View.GONE
        }


        refreshBtn.setOnClickListener {
           lifecycleScope.launch {
               val itemsThatHaveFormats = items.filter { it!!.allFormats.isNotEmpty() }
               val itemsWithMissingFormats = items.filter { it!!.allFormats.isEmpty() }.ifEmpty { items }

               if (itemsWithMissingFormats.size > 10){
                   continueInBackgroundSnackBar = Snackbar.make(view, R.string.update_formats_background, Snackbar.LENGTH_LONG)
                   continueInBackgroundSnackBar.setAction(R.string.ok) {
                       _multipleFormatsListener!!.onContinueOnBackground()
                       this@FormatSelectionBottomSheetDialog.dismiss()
                   }
                   continueInBackgroundSnackBar.show()
               }


               chosenFormats = emptyList()
               refreshBtn.isEnabled = false
               refreshBtn.isVisible = true
               okBtn.isVisible = false
               okBtn.isEnabled = false
               filterBtn.isEnabled = false
               formatListLinearLayout.visibility = View.GONE
               shimmers.visibility = View.VISIBLE
               shimmers.startShimmer()
               updateFormatsJob = launch(Dispatchers.IO) {
                   try{
                       //simple download
                       if (items.size == 1) {
                           kotlin.runCatching {
                               val res = infoUtil.getFormats(items.first()!!.url, currentFormatSource)
                               if (!isActive) return@launch
                               res.filter { it.format_note != "storyboard" }
                               chosenFormats = if (items.first()?.type == Type.audio) {
                                   res.filter { it.format_note.contains("audio", ignoreCase = true) }
                               } else {
                                   res
                               }
                               if (chosenFormats.isEmpty()) throw Exception()

                               formats.clear()
                               formats.addAll(res)


                               withContext(Dispatchers.Main){
                                   listener.onFormatsUpdated(res)
                               }
                           }.onFailure { err ->
                               withContext(Dispatchers.Main){
                                   UiUtil.handleResultResponse(requireActivity(), ResultViewModel.ResultsUiState(
                                       false,
                                       Pair(R.string.no_results, err.message.toString()),
                                       mutableListOf(Pair(R.string.copy_log, ResultViewModel.ResultAction.COPY_LOG))
                                   ), closed = {})
                               }
                           }

                           //list format filtering
                       }else{
                           formats.clear()
                           var progressInt = 0
                           val formatCollection = itemsThatHaveFormats.map { it!!.allFormats }.toMutableList()

                           var progress = "0/${itemsWithMissingFormats.size}"
                           withContext(Dispatchers.Main) {
                               refreshBtn.text = progress
                           }

                           val res = infoUtil.getFormatsMultiple(itemsWithMissingFormats.map { it!!.url }, currentFormatSource) {
                               if (!isActive) return@getFormatsMultiple

                               if (it.unavailable) {
                                   lifecycleScope.launch {
                                       multipleFormatsListener.onItemUnavailable(it.url)
                                       items.removeAt(items.indexOfFirst { item -> item!!.url == it.url })
                                       withContext(Dispatchers.Main) {
                                           Snackbar.make(view, it.unavailableMessage, Snackbar.LENGTH_SHORT).show()
                                       }
                                   }
                               }else{
                                   multipleFormatsListener.onFormatUpdated(it.url, it.formats)
                                   items.firstOrNull { item -> item!!.url == it.url }?.apply {
                                       allFormats.clear()
                                       allFormats.addAll(it.formats)
                                   }
                                   progressInt++
                                   lifecycleScope.launch(Dispatchers.Main) {
                                       progress = "${progressInt}/${itemsWithMissingFormats.size}"
                                       refreshBtn.text = progress
                                   }
                               }

                           }

                           formatCollection.addAll(res)

                           if (!isActive) return@launch

                           val flatFormatCollection = formatCollection.flatten()
                           val commonFormats =
                               flatFormatCollection.groupingBy { it.format_id }.eachCount()
                                   .filter { it.value == items.size }
                                   .mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }
                                   .map { it.value }
                           formats.addAll(commonFormats)

                           chosenFormats = commonFormats.filter { it.filesize != 0L }
                               .mapTo(mutableListOf()) { it.copy() }
                           chosenFormats = when (items.first()?.type) {
                               Type.audio -> chosenFormats.filter {
                                   it.vcodec.isBlank() || it.vcodec == "none"
                               }

                               else -> chosenFormats
                           }
                           if (chosenFormats.isEmpty()) throw Exception()
                           chosenFormats.forEach {
                               it.filesize =
                                   flatFormatCollection.filter { f -> f.format_id == it.format_id }
                                       .sumOf { itt -> itt.filesize }
                           }
                       }
                       isMissingFormats = formats.isEmpty()
                       withContext(Dispatchers.Main){
                           shimmers.visibility = View.GONE
                           shimmers.stopShimmer()
                           addFormatsToView()
                           refreshBtn.isVisible = isMissingFormats
                           refreshBtn.isEnabled = isMissingFormats
                           filterBtn.isEnabled = true
                           okBtn.isEnabled = true
                           formatListLinearLayout.visibility = View.VISIBLE
                       }
                   }catch (e: Exception){
                       withContext(Dispatchers.Main) {
                           refreshBtn.isEnabled = true
                           filterBtn.isEnabled = true
                           okBtn.isEnabled = true
                           refreshBtn.text = getString(R.string.update)
                           formatListLinearLayout.visibility = View.VISIBLE
                           shimmers.visibility = View.GONE
                           shimmers.stopShimmer()

                           e.printStackTrace()
                           Toast.makeText(context, getString(R.string.error_updating_formats), Toast.LENGTH_SHORT).show()
                       }
                   }
               }
               updateFormatsJob?.start()
           }
        }

        okBtn.setOnClickListener {
            returnFormats()
            dismiss()
        }

//        if (sharedPreferences.getBoolean("update_formats", false) && refreshBtn.isVisible && items.size == 1){
//            refreshBtn.performClick()
//        }

        initFilter()
    }


    private fun initFilter(){
        filterBtn.setOnClickListener {
            val filterSheet = BottomSheetDialog(requireContext())
            filterSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            filterSheet.setContentView(R.layout.format_category_sheet)

            //format filter
            filterSheet.findViewById<LinearLayout>(R.id.format_filter_linear)?.isVisible = !isMissingFormats
            if (!isMissingFormats) {
                val all = filterSheet.findViewById<TextView>(R.id.all)
                val suggested = filterSheet.findViewById<TextView>(R.id.suggested)
                val smallest = filterSheet.findViewById<TextView>(R.id.smallest)
                val generic = filterSheet.findViewById<TextView>(R.id.generic)

                val filterOptions = listOf(all!!, suggested!!,smallest!!, generic!!)
                filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                when(filterBy) {
                    FormatCategory.ALL -> all.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    FormatCategory.SUGGESTED -> {
                        suggested.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    }
                    FormatCategory.SMALLEST -> {
                        smallest.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    }
                    FormatCategory.GENERIC -> {
                        generic.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    }
                }

                all.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    filterBy = FormatCategory.ALL
                    all.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    addFormatsToView()
                    filterSheet.dismiss()
                }
                suggested.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    filterBy = FormatCategory.SUGGESTED
                    suggested.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    addFormatsToView()
                    filterSheet.dismiss()
                }
                smallest.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    filterBy = FormatCategory.SMALLEST
                    smallest.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    addFormatsToView()
                    filterSheet.dismiss()
                }
                generic.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    filterBy = FormatCategory.GENERIC
                    generic.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    addFormatsToView()
                    filterSheet.dismiss()
                }
            }

            //format source
            val formatSourceLinear = filterSheet.findViewById<LinearLayout>(R.id.format_source_linear)!!
            val canSwitch = items.all { it!!.url.isYoutubeURL() }
            formatSourceLinear.isVisible = canSwitch
            if (canSwitch) {
                val formatSourceOptions = mutableListOf<TextView>()

                val availableSources = resources.getStringArray(R.array.formats_source)
                val availableSourcesValues = resources.getStringArray(R.array.formats_source_values)
                val currentSource = currentFormatSource ?: sharedPreferences.getString("formats_source", "yt-dlp")
                formatSourceLinear.isVisible = true
                availableSources.forEachIndexed { idx, it ->
                    val txt = requireActivity().layoutInflater.inflate(R.layout.selectable_textview_filter, null) as TextView
                    val tag = availableSourcesValues[idx]

                    txt.text = it
                    txt.tag = tag
                    txt.setOnClickListener {
                        currentFormatSource = it.tag.toString()
                        refreshBtn.performClick()
                        filterSheet.dismiss()
                    }
                    formatSourceOptions.add(txt)
                    formatSourceLinear.addView(txt)
                }
                formatSourceOptions.firstOrNull { it.tag == currentSource }?.apply {
                    setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                }
            }




            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            filterSheet.behavior.peekHeight = displayMetrics.heightPixels
            filterSheet.show()
        }
    }

    private fun returnFormats(){
        if (items.size == 1){
            //simple video format selection
            listener.onFormatClick(FormatTuple(selectedVideo, selectedAudios.ifEmpty { listOf(downloadViewModel.getFormat(chosenFormats, Type.audio)) }))
        }else{
            //playlist format selection
            val formatsToReturn = mutableListOf<MultipleItemFormatTuple>()
            items.forEach {
                formatsToReturn.add(
                    MultipleItemFormatTuple(
                        it!!.url,
                        FormatTuple(
                            it.allFormats.firstOrNull { f -> f.format_id == selectedVideo?.format_id },
                            selectedAudios.map { sa ->
                                it.allFormats.first { a -> a.format_id == sa.format_id }
                            }.ifEmpty { null }
                        )
                    )
                )
            }
            multipleFormatsListener.onFormatClick(formatsToReturn)
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
                chosenFormats.groupBy { format -> codecOrder.indexOfFirst { format.vcodec.matches("^(${it})(.+)?$".toRegex()) } }

                    .flatMap {
                        it.value.sortedByDescending { l -> l.filesize }
                    }
            }
            FormatSorting.filesize -> chosenFormats
        }

        val formatSorter = FormatSorter(requireContext())

        //filter category
        when(filterBy){
            FormatCategory.ALL -> {}
            FormatCategory.SUGGESTED -> {
                finalFormats = if (items.first()?.type == Type.audio){
                    formatSorter.sortAudioFormats(finalFormats)
                }else{
                    val audioFormats = finalFormats.filter {  it.vcodec.isBlank() || it.vcodec == "none" }
                    val videoFormats = finalFormats.filter {  it.vcodec.isNotBlank() && it.vcodec != "none" }

                    formatSorter.sortVideoFormats(videoFormats) + formatSorter.sortAudioFormats(audioFormats)
                }
            }
            FormatCategory.SMALLEST -> {
                val tmpFormats = finalFormats
                    .asSequence()
                    .map { it.copy() }
                    .filter { it.filesize > 0 }
                    .onEach {
                        var tmp = it.format_note
                        //formats that end like 1080P
                        if (tmp.endsWith("060")){
                            tmp = tmp.removeSuffix("60")
                        }
                        tmp = tmp.removeSuffix("p")
                        //formats that are written like 1920X1080
                        val split = tmp.split("x")
                        if (split.size > 1){
                            tmp = split[1]
                        }
                        it.format_note = tmp
                    }
                    .groupBy { it.format_note  }
                    .map { it.value.minBy { it2 -> it2.filesize } }.toList()
                finalFormats = finalFormats.filter { tmpFormats.map { it2 -> it2.format_id }.contains(it.format_id) }
            }
            FormatCategory.GENERIC -> {
                finalFormats = listOf()
            }
        }



        val canMultiSelectAudio = items.first()?.type == Type.video && finalFormats.find { it.vcodec.isBlank() || it.vcodec == "none" } != null
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


        videoFormatList.removeAllViews()
        audioFormatList.removeAllViews()

        if (finalFormats.isEmpty()){
            finalFormats = if (items.first()?.type == Type.audio){
                genericAudioFormats
            }else{
                genericVideoFormats
            }
        }

        for (i in 0.. finalFormats.lastIndex){
            val format = finalFormats[i]
            val formatItem = LayoutInflater.from(context).inflate(R.layout.format_item, null)
            formatItem.tag = "${format.format_id}${format.format_note}"
            UiUtil.populateFormatCard(requireContext(), formatItem as MaterialCardView, format, null)
            if (selectedVideo == format) formatItem.isChecked = true
            if (selectedAudios.any { it == format }) formatItem.isChecked = true
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
                        listener.onFormatClick(FormatTuple(format, null))
                    }else{
                        val formatsToReturn = mutableListOf<MultipleItemFormatTuple>()
                        val f = if (genericAudioFormats.contains(format) || genericVideoFormats.contains(format)) format else null
                        items.forEach {
                            formatsToReturn.add(
                                MultipleItemFormatTuple(
                                    it!!.url,
                                    FormatTuple(
                                        f ?: it.allFormats.firstOrNull { af -> af.format_id == format.format_id },
                                        null
                                    )
                                )
                            )
                        }
                        multipleFormatsListener.onFormatClick(formatsToReturn)
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

        if (items.first()?.type == Type.video){
            selectedVideo = null
            run breaking@{
                videoFormatList.children.forEach {
                    val card = it as MaterialCardView
                    if (card.isChecked){
                        selectedVideo = finalFormats.first { format -> "${format.format_id}${format.format_note}" == card.tag }
                        return@breaking
                    }
                }
            }
        }else{
            selectedAudios = mutableListOf()
            run breaking@{
                audioFormatList.children.forEach {
                    val card = it as MaterialCardView
                    if (card.isChecked){
                        selectedAudios.add(finalFormats.first { format -> "${format.format_id}${format.format_note}" == card.tag })
                    }
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        cleanUp()
        super.onDismiss(dialog)
    }


    private fun cleanUp(){
        kotlin.runCatching {
            updateFormatsJob?.cancel(CancellationException())
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("formatSheet")!!).commit()
        }
    }
}

interface OnFormatClickListener{
    fun onFormatClick(formatTuple: FormatTuple)
    fun onFormatsUpdated(allFormats: List<Format>)
}

interface OnMultipleFormatClickListener{
    fun onFormatClick(formatTuple: List<MultipleItemFormatTuple>)
    fun onContinueOnBackground() {}
    fun onFormatUpdated(url: String, formats: List<Format>)
    fun onItemUnavailable(url: String)
}

class MultipleItemFormatTuple internal constructor(
    var url: String,
    var formatTuple: FormatTuple
)

class FormatTuple internal constructor(
    var format: Format?,
    var audioFormats: List<Format>?
)