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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.FormatViewModel
import com.deniscerri.ytdl.ui.adapter.FormatAdapter
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch


class FormatSelectionBottomSheetDialog(
    private val _listener: OnFormatClickListener? = null,
    private val _multipleFormatsListener: OnMultipleFormatClickListener? = null
) : BottomSheetDialogFragment(), FormatAdapter.OnItemClickListener {

    private lateinit var formatViewModel: FormatViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FormatAdapter
    private lateinit var genericAudioFormats : List<Format>
    private lateinit var genericVideoFormats : List<Format>
    private var formats: List<Format> = listOf()

    private var canMultiSelectAudio: Boolean = false

    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var formatUtil: FormatUtil
    private lateinit var view: View
    private lateinit var continueInBackgroundSnackBar : Snackbar
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var okBtn : Button
    private lateinit var refreshBtn: Button
    private lateinit var filterBtn : Button
    private lateinit var tabLayout: TabLayout

    private var videoTabFormats: List<Format> = listOf()
    private var audioTabFormats: List<Format> = listOf()
    private var preselected = false

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
        tabLayout = view.findViewById(R.id.format_tablayout)
        tabLayout.addTab(tabLayout.newTab().setText(R.string.video))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.audio))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showSelectedTab()
                recyclerView.scrollToPosition(0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                recyclerView.scrollToPosition(0)
            }
        })

        lifecycleScope.launch {
            formatViewModel.formats.collectLatest {
                if (it.isEmpty()) {
                    this@FormatSelectionBottomSheetDialog.dismiss()
                }
                val splitByType = formatViewModel.canMultiSelectAudio.value
                adapter.setCanMultiSelectAudio(splitByType)
                formats = it
                tabLayout.isVisible = splitByType
                if (splitByType) {
                    videoTabFormats = it.filter { f -> f.isVideo() }
                    audioTabFormats = it.filter { f -> !f.isVideo() }
                    if (!preselected) {
                        preselectCurrentFormats()
                        preselected = true
                    }
                    showSelectedTab()
                    updateAudioTabLabel()
                }else {
                    adapter.submitList(it)
                }
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

        fun refreshBtnClicked(longPressed : Boolean) {
            val distinctItems = formatViewModel.selectedItems.value.distinctBy { it.url }
            // with several items, fetch only the ones still missing formats
            val itemsToUpdate = if (distinctItems.size > 1) {
                distinctItems.filter { it.allFormats.isEmpty() }.ifEmpty { distinctItems }
            }else distinctItems

            if (itemsToUpdate.size > 10 && _multipleFormatsListener != null){
                continueInBackgroundSnackBar = Snackbar.make(view, R.string.update_formats_background, Snackbar.LENGTH_LONG)
                continueInBackgroundSnackBar.setAction(R.string.ok) {
                    // the background worker takes over
                    formatViewModel.cancelUpdateFormats()
                    _multipleFormatsListener.onContinueOnBackground()
                    this@FormatSelectionBottomSheetDialog.dismiss()
                }
                continueInBackgroundSnackBar.show()
            }

            formatViewModel.updateFormats(itemsToUpdate.map { it.url }, currentFormatSource, longPressed)
        }

        lifecycleScope.launch {
            combine(formatViewModel.updatingFormatsUrls, formatViewModel.selectedItems) { urls, items ->
                items.any { urls.contains(it.url) }
            }.distinctUntilChanged().collectLatest { updating ->
                refreshBtn.isEnabled = !updating
                filterBtn.isEnabled = !updating
                okBtn.isEnabled = !updating
                if (updating) {
                    // the current selection points at formats about to be replaced, redo it from the refreshed item
                    preselected = false
                    adapter.selectedVideoFormat = null
                    adapter.selectedAudioFormats.clear()
                    refreshBtn.isVisible = true
                    okBtn.isVisible = false
                    recyclerView.isVisible = false
                    shimmers.isVisible = true
                    shimmers.startShimmer()
                }else {
                    okBtn.isVisible = canMultiSelectAudio
                    refreshBtn.text = getString(R.string.update)
                    recyclerView.isVisible = true
                    shimmers.visibility = View.GONE
                    shimmers.stopShimmer()
                }
            }
        }

        lifecycleScope.launch {
            formatViewModel.updateFormatsProgress.collectLatest { progress ->
                if (progress != null) refreshBtn.text = "${progress.first}/${progress.second}"
            }
        }

        lifecycleScope.launch {
            formatViewModel.formatUpdates.collect { event ->
                when (event) {
                    is FormatViewModel.FormatUpdateEvent.Unavailable -> {
                        Snackbar.make(view, event.message, Snackbar.LENGTH_SHORT).show()
                    }
                    is FormatViewModel.FormatUpdateEvent.Error -> {
                        UiUtil.handleNoResults(requireActivity(), event.message, null, false, continued = {}, closed = {}, cookieFetch = {})
                    }
                    else -> {}
                }
            }
        }

        refreshBtn.setOnClickListener {
            refreshBtnClicked(false)
        }
        refreshBtn.setOnLongClickListener {
            refreshBtnClicked(true)
            true
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
                        refreshBtn.performLongClick()
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
                listOf(downloadViewModel.getFormat(formats, DownloadType.audio))
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
            formatViewModel.filterBy.value = FormatCategory.ALL
            // a running format update keeps going, the download card behind still wants the result
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

    override fun onSelectionChanged(item: Format) {
        updateAudioTabLabel()
        // a video without its own audio needs one, so lead the user there unless they already picked some
        if (item.isVideo() && !item.hasAudio() && adapter.selectedAudioFormats.isEmpty()) {
            tabLayout.getTabAt(1)?.select()
        }
    }

    private fun showSelectedTab() {
        val list = if (tabLayout.selectedTabPosition == 1) audioTabFormats else videoTabFormats
        adapter.submitList(list)
    }

    // check the formats the item already has, so the sheet starts from what the download card shows
    private fun preselectCurrentFormats() {
        val items = formatViewModel.selectedItems.value
        if (items.isEmpty()) return
        // with several items only preselect when they all agree
        if (items.map { it.format.format_id }.distinct().size != 1) return
        if (items.map { it.videoPreferences.audioFormatIDs.toSet() }.distinct().size != 1) return

        val first = items.first()
        adapter.selectedVideoFormat = videoTabFormats.firstOrNull { it.format_id == first.format.format_id }
        adapter.selectedAudioFormats.clear()
        adapter.selectedAudioFormats.addAll(first.videoPreferences.audioFormatIDs.mapNotNull { id ->
            audioTabFormats.firstOrNull { it.format_id == id }
        })
    }

    private fun updateAudioTabLabel() {
        if (!tabLayout.isVisible) return
        val video = adapter.selectedVideoFormat
        val audio = adapter.selectedAudioFormats
        tabLayout.getTabAt(1)?.text = when {
            audio.isNotEmpty() -> "${getString(R.string.audio)} (${audio.size})"
            video?.hasAudio() == true -> "${getString(R.string.audio)} (${getString(R.string.optional)})"
            else -> getString(R.string.audio)
        }
    }


    private fun Format.isVideo() : Boolean {
        return this.vcodec.isNotBlank() && this.vcodec != "none"
    }

    private fun Format.hasAudio() : Boolean {
        return this.acodec.isNotBlank() && this.acodec != "none"
    }
}

interface OnFormatClickListener{
    fun onFormatClick(formatTuple: FormatTuple)
}

// format updates are not reported here, observe FormatViewModel.formatUpdates instead
interface OnMultipleFormatClickListener{
    fun onFormatClick(formatTuple: List<MultipleItemFormatTuple>)
    fun onContinueOnBackground() {}
}

class MultipleItemFormatTuple internal constructor(
    var url: String,
    var formatTuple: FormatTuple
)

class FormatTuple internal constructor(
    var format: Format?,
    var audioFormats: List<Format>?
)