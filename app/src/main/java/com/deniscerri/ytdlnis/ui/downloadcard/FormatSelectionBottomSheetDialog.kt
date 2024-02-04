package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.*
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class FormatSelectionBottomSheetDialog(private val items: List<DownloadItem?>, private var formats: List<List<Format>>, private val listener: OnFormatClickListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var infoUtil: InfoUtil
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var formatCollection: MutableList<List<Format>>
    private lateinit var chosenFormats: List<Format>
    private var selectedVideo : Format? = null
    private lateinit var selectedAudios : MutableList<Format>

    private lateinit var videoFormatList : LinearLayout
    private lateinit var audioFormatList : LinearLayout
    private lateinit var okBtn : Button
    private lateinit var videoTitle : TextView
    private lateinit var audioTitle : TextView

    private lateinit var sortBy : FormatSorting
    private lateinit var filterBy : FormatCategory
    private lateinit var filterBtn : Button

    private lateinit var continueInBackgroundSnackBar : Snackbar
    private lateinit var view: View
    private var updateFormatsJob: Job? = null

    private var hasGenericFormats: Boolean = false

    enum class FormatSorting {
        filesize, container, codec, id
    }

    enum class FormatCategory {
        ALL, SUGGESTED, SMALLEST, GENERIC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        formatCollection = mutableListOf()
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
        hasGenericFormats = formats.first().isEmpty() || formats.last().any { it.format_id == "best" || it.format_id == "ba" }
        filterBtn.isVisible = !hasGenericFormats

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
        if (!hasGenericFormats || items.isEmpty() || items.first()?.url?.isEmpty() == true) refreshBtn.visibility = View.GONE


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
               refreshBtn.isEnabled = false
               formatListLinearLayout.visibility = View.GONE
               shimmers.visibility = View.VISIBLE
               shimmers.startShimmer()
               updateFormatsJob = launch {
                   try{
                       //simple download
                       if (items.size == 1) {
                           kotlin.runCatching {
                               val res = withContext(Dispatchers.IO){
                                   infoUtil.getFormats(items.first()!!.url)
                               }
                               res.filter { it.format_note != "storyboard" }
                               chosenFormats = if (items.first()?.type == Type.audio) {
                                   res.filter { it.format_note.contains("audio", ignoreCase = true) }
                               } else {
                                   res
                               }
                               if (chosenFormats.isEmpty()) throw Exception()

                               formats = listOf(res)
                               withContext(Dispatchers.Main){
                                   listener.onFormatsUpdated(formats)
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
                           var progress = "0/${items.size}"
                           formatCollection.clear()
                           withContext(Dispatchers.Main) {
                               refreshBtn.text = progress
                           }
                           withContext(Dispatchers.IO){
                               infoUtil.getFormatsMultiple(items.map { it!!.url }) {
                                   if (isActive) {
                                       lifecycleScope.launch(Dispatchers.Main) {
                                           formatCollection.add(it)
                                           progress = "${formatCollection.size}/${items.size}"
                                           refreshBtn.text = progress
                                       }
                                   }
                               }
                           }
                           formats = formatCollection
                           withContext(Dispatchers.Main){
                               listener.onFormatsUpdated(formats)
                           }

                           val flatFormatCollection = formatCollection.flatten()
                           val commonFormats =
                               flatFormatCollection.groupingBy { it.format_id }.eachCount()
                                   .filter { it.value == items.size }
                                   .mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }
                                   .map { it.value }
                           chosenFormats = commonFormats.filter { it.filesize != 0L }
                               .mapTo(mutableListOf()) { it.copy() }
                           chosenFormats = when (items.first()?.type) {
                               Type.audio -> chosenFormats.filter {
                                   it.format_note.contains(
                                       "audio",
                                       ignoreCase = true
                                   )
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
                       hasGenericFormats = false
                       withContext(Dispatchers.Main){
                           shimmers.visibility = View.GONE
                           shimmers.stopShimmer()
                           filterBtn.isVisible = true
                           addFormatsToView()
                           refreshBtn.visibility = View.GONE
                           formatListLinearLayout.visibility = View.VISIBLE
                       }
                   }catch (e: Exception){
                       withContext(Dispatchers.Main) {
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
               updateFormatsJob?.start()
           }
        }

        okBtn.setOnClickListener {
            if (selectedVideo == null) {
                selectedVideo =
                    chosenFormats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.maxByOrNull { it.filesize }!!
            }
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
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            filterSheet.behavior.peekHeight = displayMetrics.heightPixels
            filterSheet.show()
        }
    }

    private fun returnFormats(){
        //simple video format selection
        if (items.size == 1){
            listener.onFormatClick(listOf(FormatTuple(selectedVideo!!, selectedAudios.ifEmpty { listOf(downloadViewModel.getFormat(chosenFormats, Type.audio)) })))
        }else{
            //playlist format selection
            val selectedFormats = mutableListOf<Format>()
            formatCollection.forEach {
                selectedFormats.add(it.first{ f -> f.format_id == selectedVideo!!.format_id})
            }
            if (selectedFormats.isEmpty()) {
                items.forEach { _ ->
                    selectedFormats.add(selectedVideo!!)
                }
            }
            listener.onFormatClick(selectedFormats.map { FormatTuple(it, selectedAudios) })
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

        //filter category
        when(filterBy){
            FormatCategory.ALL -> {}
            FormatCategory.SUGGESTED -> {
                finalFormats = if (items.first()?.type == Type.audio){
                    val req = downloadViewModel.getPreferredAudioRequirements()
                    finalFormats
                        .filter { f -> req.count{req -> req(f)} >= 1 }
                        .sortedByDescending { f -> req.count{req -> req(f)} }
                        .ifEmpty { listOf(downloadViewModel.getFormat(finalFormats, Type.audio)) }
                }else{
                    val req = downloadViewModel.getPreferredVideoRequirements()
                    req.addAll(downloadViewModel.getPreferredAudioRequirements())
                    finalFormats
                        .filter { f -> req.count{req -> req(f)} >= 1 }
                        .sortedByDescending { f -> req.count{req -> req(f)} }
                        .ifEmpty { listOf(downloadViewModel.getFormat(finalFormats, Type.video)) }
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


        videoFormatList.removeAllViews()
        audioFormatList.removeAllViews()

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
                        listener.onFormatClick(listOf(FormatTuple(format, null)))
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
                        listener.onFormatClick(selectedFormats.map { FormatTuple(it, null) })
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
        super.onDismiss(dialog)
        cleanUp()
    }


    private fun cleanUp(){
        kotlin.runCatching {
            updateFormatsJob?.cancel()
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("formatSheet")!!).commit()
        }
    }
}

interface OnFormatClickListener{
    fun onFormatClick(selectedFormats: List<FormatTuple>)
    fun onContinueOnBackground() {}
    fun onFormatsUpdated(allFormats: List<List<Format>>)
}

class FormatTuple internal constructor(
    var format: Format,
    var audioFormats: List<Format>?
)