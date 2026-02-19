package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.FormatRecyclerView
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.FormatViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.FormatAdapter
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FormatSelectionBottomSheetDialog(
    private val _listener: OnFormatClickListener? = null,
    private val _multipleFormatsListener: OnMultipleFormatClickListener? = null
) : BottomSheetDialogFragment(), FormatAdapter.OnItemClickListener {

    private lateinit var formatViewModel: FormatViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FormatAdapter
    private lateinit var genericAudioFormats : List<Format>
    private lateinit var genericVideoFormats : List<Format>
    private var formats: List<FormatRecyclerView> = listOf()

    private var canMultiSelectAudio: Boolean = false

    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var formatUtil: FormatUtil
    private lateinit var view: View
    private lateinit var continueInBackgroundSnackBar : Snackbar
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var okBtn : Button
    private lateinit var refreshBtn: Button
    private lateinit var filterBtn : Button

    private var updateFormatsJob: Job? = null

    private lateinit var listener: OnFormatClickListener
    private lateinit var multipleFormatsListener: OnMultipleFormatClickListener

    private var currentFormatSource : String? = null

    enum class FormatSorting {
        filesize, container, codec, id
    }

    enum class FormatCategory {
        ALL, SUGGESTED, SMALLEST, GENERIC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        formatViewModel = ViewModelProvider(requireActivity())[FormatViewModel::class.java]
        formatUtil = FormatUtil(requireContext())
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
    }


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        view = LayoutInflater.from(context).inflate(R.layout.format_select_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        view.findViewById<TextView>(R.id.bottom_sheet_title).setOnClickListener {
            recyclerView.scrollTo(0,0)
        }

        genericAudioFormats = formatUtil.getGenericAudioFormats(resources)
        genericVideoFormats = formatUtil.getGenericVideoFormats(resources)

        adapter =
            FormatAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 1)
        recyclerView.adapter = adapter

        refreshBtn = view.findViewById(R.id.format_refresh)
        okBtn = view.findViewById(R.id.format_ok)
        filterBtn = view.findViewById(R.id.format_filter)
        val shimmers = view.findViewById<ShimmerFrameLayout>(R.id.format_list_shimmer)

        lifecycleScope.launch {
            formatViewModel.formats.collectLatest {
                if (it.isEmpty()) {
                    this@FormatSelectionBottomSheetDialog.dismiss()
                }
                adapter.setCanMultiSelectAudio(formatViewModel.canMultiSelectAudio.value)
                formats = it
                adapter.submitList(it.toMutableList())
                shimmers.visibility = View.GONE
                shimmers.stopShimmer()
                recyclerView.isVisible = true
            }
        }


        lifecycleScope.launch {
            formatViewModel.showFilterBtn.collectLatest {
                filterBtn.isVisible = it
            }
        }

        lifecycleScope.launch {
            formatViewModel.showRefreshBtn.collectLatest {
                refreshBtn.isVisible = it
            }
        }

        lifecycleScope.launch {
            formatViewModel.canMultiSelectAudio.collectLatest {
                okBtn.isVisible = it
                canMultiSelectAudio = it
            }
        }

        _listener?.apply {
            listener = this
        }

        _multipleFormatsListener?.apply {
            multipleFormatsListener = this
        }

        refreshBtn.setOnClickListener {
            lifecycleScope.launch {
                val items = formatViewModel.selectedItems.value.toMutableList()
                val distinctItems = items.distinctBy { it.url }

                val itemsThatHaveFormats = distinctItems.filter { it.allFormats.isNotEmpty() }
                val itemsWithMissingFormats = distinctItems.filter { it.allFormats.isEmpty() }.ifEmpty { distinctItems }

                if (itemsWithMissingFormats.size > 10){
                    continueInBackgroundSnackBar = Snackbar.make(view, R.string.update_formats_background, Snackbar.LENGTH_LONG)
                    continueInBackgroundSnackBar.setAction(R.string.ok) {
                        _multipleFormatsListener!!.onContinueOnBackground()
                        this@FormatSelectionBottomSheetDialog.dismiss()
                    }
                    continueInBackgroundSnackBar.show()
                }


                refreshBtn.isEnabled = false
                refreshBtn.isVisible = true
                okBtn.isVisible = false
                okBtn.isEnabled = false
                filterBtn.isEnabled = false
                recyclerView.isVisible = false
                shimmers.isVisible = true
                shimmers.startShimmer()

                updateFormatsJob = launch(Dispatchers.IO) {
                    try{
                        //simple download
                        if (items.size == 1) {
                            kotlin.runCatching {
                                val res = resultViewModel.getFormats(items.first().url, currentFormatSource)
                                if (!isActive) return@launch
                                res.filter { it.format_note != "storyboard" }
                                val chosenFormats = if (items.first().type == DownloadType.audio) {
                                    res.filter { it.format_note.contains("audio", ignoreCase = true) }
                                } else {
                                    res
                                }
                                if (chosenFormats.isEmpty()) throw Exception()

                                items.first().allFormats.clear()
                                items.first().allFormats.addAll(chosenFormats)

                                withContext(Dispatchers.Main){
                                    formatViewModel.setItem(items.first(), false)
                                    listener.onFormatsUpdated(res)
                                }
                            }.onFailure { err ->
                                withContext(Dispatchers.Main){
                                    UiUtil.handleNoResults(requireActivity(), err.message.toString(), null, false, continued = {}, closed = {}, cookieFetch = {})
                                }
                            }

                        //list format filtering
                        }else{
                            var progressInt = 0
                            val formatCollection = itemsThatHaveFormats.map { it.allFormats }.toMutableList()

                            var progress = "0/${itemsWithMissingFormats.size}"
                            withContext(Dispatchers.Main) {
                                refreshBtn.text = progress
                            }

                            val res = resultViewModel.getFormatsMultiple(itemsWithMissingFormats.map { it.url }, currentFormatSource) {
                                if (!isActive) return@getFormatsMultiple

                                if (it.unavailable) {
                                    lifecycleScope.launch {
                                        multipleFormatsListener.onItemUnavailable(it.url)
                                        items.removeAt(items.indexOfFirst { item -> item.url == it.url })
                                        withContext(Dispatchers.Main) {
                                            Snackbar.make(view, it.unavailableMessage, Snackbar.LENGTH_SHORT).show()
                                        }
                                    }
                                }else{
                                    multipleFormatsListener.onFormatUpdated(it.url, it.formats)
                                    items.filter { item -> item.url == it.url }.forEach { d ->
                                        d.allFormats.clear()
                                        d.allFormats.addAll(it.formats)
                                    }
                                    progressInt++
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        progress = "${progressInt}/${itemsWithMissingFormats.size}"
                                        refreshBtn.text = progress
                                    }
                                }
                            }

                            formatViewModel.setItems(items, false)
                        }

                        withContext(Dispatchers.Main){
                            filterBtn.isEnabled = true
                            okBtn.isEnabled = true
                            refreshBtn.isEnabled = true
                        }
                    }catch (e: Exception){
                        withContext(Dispatchers.Main) {
                            refreshBtn.isEnabled = true
                            filterBtn.isEnabled = true
                            okBtn.isEnabled = true
                            refreshBtn.text = getString(R.string.update)
                            recyclerView.isVisible = true
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
            val isMissingFormats = formatViewModel.isMissingFormats.value
            filterSheet.findViewById<LinearLayout>(R.id.format_filter_linear)?.isVisible = !isMissingFormats
            if (!isMissingFormats) {
                val all = filterSheet.findViewById<TextView>(R.id.all)
                val suggested = filterSheet.findViewById<TextView>(R.id.suggested)
                val smallest = filterSheet.findViewById<TextView>(R.id.smallest)
                val generic = filterSheet.findViewById<TextView>(R.id.generic)

                val filterOptions = listOf(all!!, suggested!!,smallest!!, generic!!)
                filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                when(formatViewModel.filterBy.value) {
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
                    all.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    filterSheet.dismiss()
                    formatViewModel.filterBy.value = FormatCategory.ALL
                }
                suggested.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    suggested.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    filterSheet.dismiss()
                    formatViewModel.filterBy.value = FormatCategory.SUGGESTED
                }
                smallest.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    smallest.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    filterSheet.dismiss()
                    formatViewModel.filterBy.value = FormatCategory.SMALLEST
                }
                generic.setOnClickListener {
                    filterOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                    generic.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0,0,0)
                    filterSheet.dismiss()
                    formatViewModel.filterBy.value = FormatCategory.GENERIC
                }
            }

            //format source
            val formatSourceLinear = filterSheet.findViewById<LinearLayout>(R.id.format_source_linear)!!
            val items = formatViewModel.selectedItems.value
            val canSwitch = items.all { it.url.isYoutubeURL() }
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
                        formatViewModel.filterBy.value = FormatCategory.ALL
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
        if (_listener != null){
            //simple video format selection
            listener.onFormatClick(FormatTuple(adapter.selectedVideoFormat, adapter.selectedAudioFormats.ifEmpty {
                listOf(downloadViewModel.getFormat(formats.filter { it.label == null }.map { it.format!! }, DownloadType.audio))
            }))
        }else{
            val res = formatViewModel.getFormatsForItemsBasedOnFormat(adapter.selectedVideoFormat, adapter.selectedAudioFormats)
            multipleFormatsListener.onFormatClick(res)
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

    override fun onItemSelect(item: Format, audioFormats: List<Format>?) {
        if (_listener != null) {
            listener.onFormatClick(FormatTuple(item, audioFormats))
        }else{
            val formatsToReturn = formatViewModel.getFormatsForItemsBasedOnFormat(item)
            multipleFormatsListener.onFormatClick(formatsToReturn)
        }
        dismiss()
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