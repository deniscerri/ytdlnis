package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class YTQualityOption(
    val id: String,
    val title: String,
    val details: String,
    val estimatedSize: String,
    val isHd: Boolean,
    val downloadType: DownloadType,
    val selectedFormat: Format?,
    val isAudioOnly: Boolean = false
)

class YouTubeDownloadBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var dialogView: View

    private lateinit var dialogTitleText: TextView
    private lateinit var qualityRecyclerView: RecyclerView
    private lateinit var downloadButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var subtitlesButton: MaterialButton
    private lateinit var tabLayout: TabLayout
    private lateinit var loadingSpinner: ProgressBar

    private lateinit var result: ResultItem
    private var currentDownloadItem: DownloadItem? = null
    private var embedSubtitles: Boolean = true
    private var ignoreDuplicates: Boolean = false

    private val allVideoOptions = mutableListOf<YTQualityOption>()
    private val allAudioOptions = mutableListOf<YTQualityOption>()
    private var currentDisplayedOptions = mutableListOf<YTQualityOption>()
    private lateinit var adapter: YTQualityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val res = downloadCardViewModel.resultItem
        val dwl = downloadCardViewModel.downloadItem

        if (res == null) {
            dismiss()
            return
        }
        result = res
        currentDownloadItem = dwl
        embedSubtitles = sharedPreferences.getBoolean("embed_subtitles", true)
        ignoreDuplicates = arguments?.getBoolean("ignore_duplicates") == true
    }

    @SuppressLint("RestrictedApi", "InflateParams")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        dialogView = LayoutInflater.from(context).inflate(R.layout.yt_download_bottom_sheet, null)
        dialog.setContentView(dialogView)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(dialogView.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        initViews(dialogView)
        buildQualityOptions()
        setupListeners()
        fetchRealFormatsIfNeeded()
    }

    private fun initViews(v: View) {
        dialogTitleText = v.findViewById(R.id.yt_dialog_title)
        qualityRecyclerView = v.findViewById(R.id.yt_quality_recycler_view)
        downloadButton = v.findViewById(R.id.yt_download_button)
        cancelButton = v.findViewById(R.id.yt_cancel_button)
        subtitlesButton = v.findViewById(R.id.yt_subtitles_button)
        tabLayout = v.findViewById(R.id.yt_tab_layout)
        loadingSpinner = v.findViewById(R.id.yt_loading_spinner)

        updateSubtitleButtonUI()

        qualityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = YTQualityAdapter(currentDisplayedOptions) { position ->
            adapter.setSelectedIndex(position)
        }
        qualityRecyclerView.adapter = adapter
    }

    private fun updateSubtitleButtonUI() {
        subtitlesButton.alpha = if (embedSubtitles) 1f else 0.5f
        subtitlesButton.text = if (embedSubtitles) "${getString(R.string.subtitles)}: On" else "${getString(R.string.subtitles)}: Off"
    }

    private fun fetchRealFormatsIfNeeded() {
        val usingGenericFormatsOrEmpty = result.formats.isEmpty() || result.formats.any { it.format_note.contains("ytdlnisgeneric") || it.format_id.contains("ytdlnisgeneric") }
        
        if (usingGenericFormatsOrEmpty) {
            loadingSpinner.isVisible = true
            qualityRecyclerView.isVisible = false
            lifecycleScope.launch(Dispatchers.IO) {
                resultViewModel.updateFormatItemData(result)
            }
        } else {
            loadingSpinner.isVisible = false
            qualityRecyclerView.isVisible = true
        }

        lifecycleScope.launch {
            resultViewModel.updateFormatsResultData.collectLatest { updatedFormats ->
                if (!updatedFormats.isNullOrEmpty()) {
                    loadingSpinner.isVisible = false
                    qualityRecyclerView.isVisible = true
                    result.formats = updatedFormats
                    buildQualityOptions()
                }
            }
        }
    }

    private fun buildQualityOptions() {
        allVideoOptions.clear()
        allAudioOptions.clear()
        val formats = result.formats

        val extractedVideoFormats = formats.filter { 
            !it.format_note.contains("ytdlnisgeneric") && !it.format_id.contains("ytdlnisgeneric") && (it.height ?: 0) > 0 
        }.sortedByDescending { it.height }

        val extractedAudioFormats = formats.filter { 
            !it.format_note.contains("ytdlnisgeneric") && !it.format_id.contains("ytdlnisgeneric") && (it.height ?: 0) == 0 && (it.vcodec == "none" || it.vcodec.isEmpty()) 
        }.sortedByDescending { f ->
            f.tbr?.toDoubleOrNull() ?: f.filesize.toDouble()
        }

        // 1. VIDEO OPTIONS
        if (extractedVideoFormats.isNotEmpty()) {
            val addedHeights = mutableSetOf<Int>()
            extractedVideoFormats.forEach { f ->
                val h = f.height ?: 0
                val resolutionGroup = when {
                    h >= 2160 -> 2160
                    h >= 1440 -> 1440
                    h >= 1080 -> 1080
                    h >= 720 -> 720
                    h >= 480 -> 480
                    h >= 360 -> 360
                    h >= 240 -> 240
                    else -> 144
                }

                if (!addedHeights.contains(resolutionGroup)) {
                    addedHeights.add(resolutionGroup)
                    val label = when (resolutionGroup) {
                        2160 -> "2160p (4K)"
                        1440 -> "1440p (2K)"
                        1080 -> "1080p (Full HD)"
                        720 -> "720p (HD)"
                        480 -> "480p (SD)"
                        360 -> "360p (Low)"
                        240 -> "240p (Low)"
                        else -> "144p (Low)"
                    }
                    val sizeStr = if (f.filesize > 0) FileUtil.convertFileSize(f.filesize) else ""

                    allVideoOptions.add(
                        YTQualityOption(
                            id = "video_$resolutionGroup",
                            title = label,
                            details = "",
                            estimatedSize = sizeStr,
                            isHd = resolutionGroup >= 720,
                            downloadType = DownloadType.video,
                            selectedFormat = f
                        )
                    )
                }
            }
        }

        // 2. AUDIO OPTIONS
        if (extractedAudioFormats.isNotEmpty()) {
            val addedBitrates = mutableSetOf<String>()
            extractedAudioFormats.forEach { f ->
                val bitrate = f.tbr?.takeIf { it.isNotBlank() } ?: "best"
                val label = buildString {
                    append(f.container.uppercase().ifEmpty { "M4A" })
                    if (bitrate.isNotBlank()) append(" (~").append(bitrate).append(" kbps)")
                }
                val key = "${f.container}_$bitrate"
                if (!addedBitrates.contains(key)) {
                    addedBitrates.add(key)
                    val sizeStr = if (f.filesize > 0) FileUtil.convertFileSize(f.filesize) else ""
                    allAudioOptions.add(
                        YTQualityOption("audio_${f.format_id}", label, "", sizeStr, false, DownloadType.audio, f, true)
                    )
                }
            }
        }

        val currentTab = tabLayout.selectedTabPosition
        showTabOptions(if (currentTab >= 0) currentTab else 0)
    }

    private fun showTabOptions(tabPosition: Int) {
        if (tabPosition == 0) {
            dialogTitleText.text = "Download video"
            currentDisplayedOptions.clear()
            currentDisplayedOptions.addAll(allVideoOptions)
        } else {
            dialogTitleText.text = "Download audio"
            currentDisplayedOptions.clear()
            currentDisplayedOptions.addAll(allAudioOptions)
        }
        adapter.setItems(currentDisplayedOptions)
    }

    private fun setupListeners() {
        cancelButton.setOnClickListener {
            dismiss()
        }

        subtitlesButton.setOnClickListener {
            embedSubtitles = !embedSubtitles
            updateSubtitleButtonUI()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showTabOptions(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        downloadButton.setOnClickListener {
            startDownload(null)
        }

        downloadButton.setOnLongClickListener {
            val dd = MaterialAlertDialogBuilder(requireContext())
            dd.setTitle(getString(R.string.save_for_later))
            dd.setNegativeButton(getString(R.string.cancel)) { d: DialogInterface, _ -> d.cancel() }
            dd.setPositiveButton(getString(R.string.ok)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    downloadViewModel.putToSaved(getDownloadItem(null))
                    dismiss()
                }
            }
            dd.show()
            true
        }
    }

    private fun startDownload(scheduledTime: Long?) {
        downloadButton.isEnabled = false

        val item = getDownloadItem(scheduledTime)

        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                downloadViewModel.queueDownloads(listOf(item), ignoreDuplicates)
            }
            if (res.message.isNotBlank()) {
                Toast.makeText(requireContext(), res.message, Toast.LENGTH_LONG).show()
            }
            dismiss()
        }
    }

    private fun getDownloadItem(scheduledTime: Long?): DownloadItem {
        val selectedOption = adapter.getSelectedOption()
        val type = selectedOption?.downloadType ?: if (tabLayout.selectedTabPosition == 1) DownloadType.audio else DownloadType.video

        val baseItem = currentDownloadItem ?: downloadViewModel.createDownloadItemFromResult(
            result = result,
            url = result.url,
            givenType = type
        )

        baseItem.type = type

        baseItem.videoPreferences.embedSubs = embedSubtitles
        baseItem.videoPreferences.writeSubs = embedSubtitles
        baseItem.videoPreferences.writeAutoSubs = embedSubtitles
        if (baseItem.videoPreferences.subsLanguages.isEmpty()) {
            baseItem.videoPreferences.subsLanguages = "en.*,.*-orig"
        }

        if (selectedOption?.selectedFormat != null) {
            baseItem.format = selectedOption.selectedFormat
        }
        return baseItem
    }
}

class YTQualityAdapter(
    private var items: List<YTQualityOption>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<YTQualityAdapter.ViewHolder>() {

    private var selectedIndex: Int = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.card_quality_option)
        val radio: RadioButton = view.findViewById(R.id.radio_quality)
        val title: TextView = view.findViewById(R.id.text_quality_title)
        val size: TextView = view.findViewById(R.id.text_estimated_size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_yt_quality_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.size.text = item.estimatedSize
        holder.size.isVisible = item.estimatedSize.isNotBlank()

        val isSelected = position == selectedIndex
        holder.radio.isChecked = isSelected

        holder.container.setOnClickListener {
            setSelectedIndex(position)
            onItemClick(position)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<YTQualityOption>) {
        items = newItems
        selectedIndex = 0
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        val previousIndex = selectedIndex
        selectedIndex = index
        notifyItemChanged(previousIndex)
        notifyItemChanged(selectedIndex)
    }

    fun getSelectedOption(): YTQualityOption? {
        return if (selectedIndex in items.indices) items[selectedIndex] else null
    }
}
