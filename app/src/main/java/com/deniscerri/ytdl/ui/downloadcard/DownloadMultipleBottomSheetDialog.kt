package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.services.ProcessDownloadsInBackgroundService
import com.deniscerri.ytdl.ui.adapter.ConfigureMultipleDownloadsAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.function.Predicate

class DownloadMultipleBottomSheetDialog : BottomSheetDialogFragment(), ConfigureMultipleDownloadsAdapter.OnItemClickListener, View.OnClickListener,
    ConfigureDownloadBottomSheetDialog.OnDownloadItemUpdateListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var listAdapter : ConfigureMultipleDownloadsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var infoUtil: InfoUtil
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var filesize : TextView
    private lateinit var count : TextView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var shimmerTitle: ShimmerFrameLayout
    private lateinit var shimmerSubtitle: ShimmerFrameLayout
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var currentDownloadIDs: List<Long>
    private var processingItemsCount : Int = 0

    private lateinit var jobData: DownloadViewModel.ProcessingItemsJob

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        infoUtil = InfoUtil(requireContext())

        currentDownloadIDs = arguments?.getLongArray("currentDownloadIDs")?.toList() ?: listOf()
        processingItemsCount = currentDownloadIDs.size
    }

    @SuppressLint("RestrictedApi", "NotifyDataSetChanged")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_multiple_bottom_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        listAdapter =
            ConfigureMultipleDownloadsAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()


        view.findViewById<TextView>(R.id.bottom_sheet_title)?.apply {
            setOnClickListener {
                recyclerView.scrollToPosition(0)
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("multipledownloadcard")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        val scheduleBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_schedule_button)
        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
        bottomAppBar = view.findViewById(R.id.bottomAppBar)
        val preferredDownloadType = bottomAppBar.menu.findItem(R.id.preferred_download_type)

        filesize = view.findViewById(R.id.filesize)
        count = view.findViewById(R.id.count)

        val continueInBackgroundSnackBar = Snackbar.make(recyclerView, R.string.process_downloads_background, Snackbar.LENGTH_LONG)
        val snackbarView: View = continueInBackgroundSnackBar.view
        val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
        snackTextView.maxLines = 9999999

        title = view.findViewById(R.id.bottom_sheet_title)
        subtitle = view.findViewById(R.id.bottom_sheet_subtitle)
        shimmerTitle = view.findViewById(R.id.shimmer_loading_title)
        shimmerSubtitle = view.findViewById(R.id.shimmer_loading_subtitle)

        downloadViewModel.processingItemsJob.observe(this) {
            it?.apply {
                lifecycleScope.launch {
                    jobData = it
                    processingItemsCount = it.processingDownloadItemIDs.size
                    count.text = "${processingItemsCount} ${getString(R.string.selected)}"

                    val loading = it.originItemIDs.size != processingItemsCount
                    toggleLoadingShimmerTitle(loading)
                    bottomAppBar.menu.children.forEach { m -> m.isEnabled = !loading }
                    if (!loading){
                        continueInBackgroundSnackBar.dismiss()
                    }

                    downloadViewModel.processingDownloads.map { p -> p.filter { pd -> jobData.processingDownloadItemIDs.contains(pd.id) } }.collectLatest { items ->
                        listAdapter.submitList(items)
                        updateFileSize(items.map { it2 -> it2.format.filesize })

                        if (items.isNotEmpty()){
                            if (items.all { it2 -> it2.type == items[0].type }) {
                                bottomAppBar.menu[1].icon?.alpha = 255
                                if (items[0].type != DownloadViewModel.Type.command) {
                                    bottomAppBar.menu[3].icon?.alpha = 255
                                }
                            } else {
                                bottomAppBar.menu[1].icon?.alpha = 30
                                bottomAppBar.menu[3].icon?.alpha = 30
                            }

                            val type = items.first().type

                            when(type){
                                DownloadViewModel.Type.audio -> {
                                    preferredDownloadType.setIcon(R.drawable.baseline_audio_file_24)
                                }
                                DownloadViewModel.Type.video -> {
                                    preferredDownloadType.setIcon(R.drawable.baseline_video_file_24)

                                }
                                DownloadViewModel.Type.command -> {
                                    preferredDownloadType.setIcon(R.drawable.baseline_insert_drive_file_24)
                                }

                                else -> {}
                            }

                        }
                    }
                }
            }
        }

        scheduleBtn.setOnClickListener{
            fun initSchedule(processingFinished: Boolean) {
                UiUtil.showDatePicker(parentFragmentManager) { cal ->
                    scheduleBtn.isEnabled = false
                    download.isEnabled = false

                    lifecycleScope.launch {
                        withContext(Dispatchers.IO){
                            downloadViewModel.deleteAllWithID(currentDownloadIDs)
                        }

                        jobData.job?.cancel(CancellationException())
                        jobData.job = null
                        downloadProcessDownloadsInBackground(jobData, processingFinished)

                        val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(cal.timeInMillis)
                        Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + date, Toast.LENGTH_LONG).show()

                        dismiss()
                    }
                }
            }

            if (shimmerTitle.isVisible){
                continueInBackgroundSnackBar.setAction(R.string.ok) {
                    initSchedule(false)
                }
                continueInBackgroundSnackBar.show()
            }else{
                initSchedule(true)
            }
        }

        download!!.setOnClickListener {
            fun initDownload(processingFinished: Boolean) {
                scheduleBtn.isEnabled = false
                download.isEnabled = false
                lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        downloadViewModel.deleteAllWithID(currentDownloadIDs)
                    }

                    jobData.job?.cancel(CancellationException())
                    jobData.job = null
                    downloadProcessDownloadsInBackground(jobData, processingFinished)
                    dismiss()
                }
            }

            if (shimmerTitle.isVisible){
                continueInBackgroundSnackBar.setAction(R.string.ok) {
                    initDownload(false)
                }
                continueInBackgroundSnackBar.show()
            }else{
                initDownload(true)
            }


        }

        download.setOnLongClickListener {
            val dd = MaterialAlertDialogBuilder(requireContext())
            dd.setTitle(getString(R.string.save_for_later))
            dd.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
            dd.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch{
                    withContext(Dispatchers.IO){
                        downloadViewModel.deleteAllWithID(currentDownloadIDs)
                        downloadViewModel.moveProcessingToSavedCategory(jobData.processingDownloadItemIDs)
                    }
                    jobData.job?.cancel(CancellationException())
                    jobData.job = null
                    dismiss()
                }
            }
            dd.show()
            true
        }

        val formatListener = object : OnFormatClickListener {
            override fun onFormatClick(selectedFormats: List<FormatTuple>) {
                CoroutineScope(Dispatchers.IO).launch {
                    downloadViewModel.updateProcessingFormat(jobData.processingDownloadItemIDs, selectedFormats)
                }
            }

            override fun onFormatsUpdated(allFormats: List<List<Format>>) {
                CoroutineScope(Dispatchers.IO).launch {
                    downloadViewModel.updateProcessingAllFormats(jobData.processingDownloadItemIDs, allFormats)
                }
            }


            override fun onContinueOnBackground() {
                requireActivity().lifecycleScope.launch {
                    withContext(Dispatchers.IO){
                        downloadViewModel.continueUpdatingFormatsOnBackground(jobData.processingDownloadItemIDs)
                    }
                    jobData.job?.cancel(CancellationException())
                    jobData.job = null
                    dismiss()
                }
            }
        }

        bottomAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.folder -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    pathResultLauncher.launch(intent)
                }
                R.id.preferred_download_type -> {
                    lifecycleScope.launch{
                        val bottomSheet = BottomSheetDialog(requireContext())
                        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                        bottomSheet.setContentView(R.layout.download_type_sheet)

                        // BUTTON ----------------------------------
                        val audio = bottomSheet.findViewById<TextView>(R.id.audio)
                        val video = bottomSheet.findViewById<TextView>(R.id.video)
                        val command = bottomSheet.findViewById<TextView>(R.id.command)


                        withContext(Dispatchers.IO){
                            val nr = commandTemplateViewModel.getTotalNumber()
                            if(nr == 0){
                                command!!.visibility = View.GONE
                            }else{
                                command!!.visibility = View.VISIBLE
                            }
                        }

                        audio!!.setOnClickListener {
                            CoroutineScope(Dispatchers.IO).launch {
                                downloadViewModel.updateProcessingType(jobData.processingDownloadItemIDs, DownloadViewModel.Type.audio)
                                withContext(Dispatchers.Main){
                                    preferredDownloadType.setIcon(R.drawable.baseline_audio_file_24)
                                    bottomAppBar.menu[1].icon?.alpha = 255
                                    bottomAppBar.menu[3].icon?.alpha = 255
                                    bottomSheet.cancel()
                                }
                            }
                        }

                        video!!.setOnClickListener {
                            CoroutineScope(Dispatchers.IO).launch{
                                downloadViewModel.updateProcessingType(jobData.processingDownloadItemIDs, DownloadViewModel.Type.video)
                                withContext(Dispatchers.Main){
                                    preferredDownloadType.setIcon(R.drawable.baseline_video_file_24)
                                    bottomAppBar.menu[1].icon?.alpha = 255
                                    bottomAppBar.menu[3].icon?.alpha = 255
                                    bottomSheet.cancel()
                                }
                            }
                        }

                        command!!.setOnClickListener {
                            CoroutineScope(Dispatchers.IO).launch{
                                downloadViewModel.updateProcessingType(jobData.processingDownloadItemIDs, DownloadViewModel.Type.command)
                                withContext(Dispatchers.Main){
                                    preferredDownloadType.setIcon(R.drawable.baseline_insert_drive_file_24)
                                    bottomAppBar.menu[1].icon?.alpha = 255
                                    bottomAppBar.menu[3].icon?.alpha = 30
                                    bottomSheet.cancel()
                                }
                            }
                        }

                        bottomSheet.show()
                        val displayMetrics = DisplayMetrics()
                        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                        bottomSheet.window!!.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                }
                R.id.format -> {
                    lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO){
                            downloadViewModel.checkIfAllProcessingItemsHaveSameType(jobData.processingDownloadItemIDs,)
                        }
                        if (!res.first){
                            Toast.makeText(requireContext(), getString(R.string.format_filtering_hint), Toast.LENGTH_SHORT).show()
                        }else{

                            if (res.second == DownloadViewModel.Type.command){
                                UiUtil.showCommandTemplates(requireActivity(), commandTemplateViewModel) {
                                    val format  = Format(
                                        it.first().title,
                                        "",
                                        "",
                                        "",
                                        "",
                                        0,
                                        it.joinToString(" ") { c -> c.content }
                                    )

                                    CoroutineScope(Dispatchers.IO).launch {
                                        downloadViewModel.updateProcessingCommandFormat(jobData.processingDownloadItemIDs, format)
                                    }
                                }
                            }else{
                                val items = withContext(Dispatchers.IO){
                                    downloadViewModel.getAllByIDs(jobData.processingDownloadItemIDs)
                                }
                                val flatFormatCollection = items.map { it.allFormats }.flatten()
                                val commonFormats = withContext(Dispatchers.IO){
                                    flatFormatCollection.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }.map { it.value }
                                }

                                val formats = if (commonFormats.isNotEmpty() && items.none{it.allFormats.isEmpty()}) {
                                    items.map { it.allFormats }
                                }else{
                                    when(items.first().type){
                                        DownloadViewModel.Type.audio -> listOf<List<Format>>(infoUtil.getGenericAudioFormats(requireContext().resources))
                                        else -> listOf<List<Format>>(infoUtil.getGenericVideoFormats(requireContext().resources))
                                    }
                                }
                                val bottomSheet = FormatSelectionBottomSheetDialog(items, formats, formatListener)
                                bottomSheet.show(parentFragmentManager, "formatSheet")
                            }
                        }
                    }
                }
                R.id.more -> {
                    lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO){
                            downloadViewModel.checkIfAllProcessingItemsHaveSameType(jobData.processingDownloadItemIDs)
                        }
                        if (!res.first) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.format_filtering_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        }else{
                            val scale = resources.displayMetrics.density
                            val padding = (40*scale*0.5f).toInt()

                            when(res.second){
                                DownloadViewModel.Type.audio -> {
                                    val bottomSheet = BottomSheetDialog(requireContext())
                                    bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                                    bottomSheet.setContentView(R.layout.adjust_audio)
                                    val sheetView = bottomSheet.findViewById<View>(android.R.id.content)!!
                                    sheetView.findViewById<View>(R.id.adjust).setPadding(padding,padding,padding,padding)

                                    val items = withContext(Dispatchers.IO){
                                        downloadViewModel.getAllByIDs(jobData.processingDownloadItemIDs)
                                    }

                                    UiUtil.configureAudio(
                                        sheetView,
                                        requireActivity(),
                                        items,
                                        embedThumbClicked = {enabled ->
                                            items.forEach {
                                                it.audioPreferences.embedThumb = enabled
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        cropThumbClicked = {enabled ->
                                            items.forEach {
                                                it.audioPreferences.cropThumb = enabled
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        splitByChaptersClicked = {enabled ->
                                            items.forEach {
                                                it.audioPreferences.splitByChapters = enabled
                                            }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        filenameTemplateSet = {template ->
                                            items.forEach {
                                                it.customFileNameTemplate = template
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        sponsorBlockItemsSet = { values, checkedItems ->
                                            items.forEach { it.audioPreferences.sponsorBlockFilters.clear() }
                                            for (i in checkedItems.indices) {
                                                if (checkedItems[i]) {
                                                    items.forEach { it.audioPreferences.sponsorBlockFilters.add(values[i]) }
                                                }
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        cutClicked = {},
                                        cutDisabledClicked = {},
                                        extraCommandsClicked = {
                                            val callback = object : ExtraCommandsListener {
                                                override fun onChangeExtraCommand(c: String) {
                                                    items.forEach { it.extraCommands = c }
                                                    requireActivity().lifecycleScope.launch {
                                                        items.forEach { downloadViewModel.updateDownload(it) }
                                                    }
                                                    bottomSheet.dismiss()
                                                }
                                            }

                                            val bottomSheetDialog = AddExtraCommandsDialog(null, callback)
                                            bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                                        }
                                    )
                                    bottomSheet.show()
                                    val displayMetrics = DisplayMetrics()
                                    requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                                    bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                                    bottomSheet.window!!.setLayout(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                }
                                DownloadViewModel.Type.video -> {
                                    val bottomSheet = BottomSheetDialog(requireContext())
                                    bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                                    bottomSheet.setContentView(R.layout.adjust_video)
                                    val sheetView = bottomSheet.findViewById<View>(android.R.id.content)!!
                                    sheetView.findViewById<View>(R.id.adjust).setPadding(padding,padding,padding,padding)

                                    val items = withContext(Dispatchers.IO){
                                        downloadViewModel.getAllByIDs(jobData.processingDownloadItemIDs)
                                    }

                                    UiUtil.configureVideo(
                                        sheetView,
                                        requireActivity(),
                                        items,
                                        embedSubsClicked = {checked ->
                                            items.forEach { it.videoPreferences.embedSubs = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        addChaptersClicked = {checked ->
                                            items.forEach { it.videoPreferences.addChapters = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        splitByChaptersClicked = { checked ->
                                            items.forEach { it.videoPreferences.splitByChapters = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        saveThumbnailClicked = {checked ->
                                            items.forEach { it.SaveThumb = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        sponsorBlockItemsSet = { values, checkedItems ->
                                            items.forEach { it.videoPreferences.sponsorBlockFilters.clear() }
                                            for (i in checkedItems.indices) {
                                                if (checkedItems[i]) {
                                                    items.forEach { it.videoPreferences.sponsorBlockFilters.add(values[i]) }
                                                }
                                            }
                                            requireActivity().lifecycleScope.launch {
                                                items.forEach { downloadViewModel.updateDownload(it) }
                                            }
                                            bottomSheet.dismiss()
                                        },
                                        cutClicked = {},
                                        cutDisabledClicked = {},
                                        filenameTemplateSet = { checked ->
                                            items.forEach { it.customFileNameTemplate = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        saveSubtitlesClicked = {checked ->
                                            items.forEach { it.videoPreferences.writeSubs = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        saveAutoSubtitlesClicked = {checked ->
                                            items.forEach { it.videoPreferences.writeAutoSubs = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        subtitleLanguagesSet = {value ->
                                            items.forEach { it.videoPreferences.subsLanguages = value }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        removeAudioClicked = {checked ->
                                            items.forEach { it.videoPreferences.removeAudio = checked }
                                            CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                        },
                                        alsoDownloadAsAudioClicked = {},
                                        extraCommandsClicked = {
                                            val callback = object : ExtraCommandsListener {
                                                override fun onChangeExtraCommand(c: String) {
                                                    items.forEach { it.extraCommands = c }
                                                    CoroutineScope(Dispatchers.IO).launch { items.forEach { downloadViewModel.updateDownload(it) } }
                                                    bottomSheet.dismiss()
                                                }
                                            }

                                            val bottomSheetDialog = AddExtraCommandsDialog(null, callback)
                                            bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                                        }
                                    )

                                    bottomSheet.show()
                                    val displayMetrics = DisplayMetrics()
                                    requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                                    bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                                    bottomSheet.window!!.setLayout(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                                DownloadViewModel.Type.command -> {
                                }

                                else -> {}
                            }
                        }
                    }


                }
            }
            true
        }

    }

    private fun toggleLoadingShimmerTitle(show: Boolean) {
        shimmerTitle.isVisible = show
        title.isVisible = !show
        shimmerSubtitle.isVisible = show
        subtitle.isVisible = !show

        if (show){
            shimmerTitle.startShimmer()
            shimmerSubtitle.startShimmer()
        }else{
            shimmerTitle.stopShimmer()
            shimmerSubtitle.stopShimmer()
        }
    }

    private fun updateFileSize(items: List<Long>){
        if (items.all { it > 5L }){
            val size = FileUtil.convertFileSize(items.sum())
            if (size != "?"){
                filesize.visibility = View.VISIBLE
                filesize.text = "${getString(R.string.file_size)}: >~ $size"
            }

        }else{
            filesize.visibility = View.GONE
        }
    }

    private var pathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            CoroutineScope(Dispatchers.IO).launch {
                downloadViewModel.updateProcessingDownloadPath(jobData.processingDownloadItemIDs, result.data?.data.toString())
            }

            val path = FileUtil.formatPath(result.data!!.data.toString())
            Toast.makeText(requireContext(),getString(R.string.changed_path_for_everyone_to) + " " + path, Toast.LENGTH_LONG).show()
        }
    }

    override fun onButtonClick(id: Long) {
        lifecycleScope.launch {
            var item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            }

            val bottomSheet = BottomSheetDialog(requireContext())
            bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            bottomSheet.setContentView(R.layout.download_type_sheet)

            // BUTTON ----------------------------------
            val audio = bottomSheet.findViewById<TextView>(R.id.audio)
            val video = bottomSheet.findViewById<TextView>(R.id.video)
            val command = bottomSheet.findViewById<TextView>(R.id.command)

            withContext(Dispatchers.IO){
                val nr = commandTemplateViewModel.getTotalNumber()
                if(nr == 0){
                    command!!.visibility = View.GONE
                }else{
                    command!!.visibility = View.VISIBLE
                }
            }

            audio!!.setOnClickListener {
                lifecycleScope.launch {
                    item = downloadViewModel.switchDownloadType(listOf(item), DownloadViewModel.Type.audio).first()
                    withContext(Dispatchers.IO){
                        downloadViewModel.updateDownload(item)
                    }
                    bottomSheet.cancel()
                }
            }

            video!!.setOnClickListener {
                lifecycleScope.launch {
                    item = downloadViewModel.switchDownloadType(listOf(item), DownloadViewModel.Type.video).first()
                    withContext(Dispatchers.IO){
                        downloadViewModel.updateDownload(item)
                    }
                    bottomSheet.cancel()
                }
            }

            command!!.setOnClickListener {
                lifecycleScope.launch {
                    item = downloadViewModel.switchDownloadType(listOf(item), DownloadViewModel.Type.command).first()
                    withContext(Dispatchers.IO){
                        downloadViewModel.updateDownload(item)
                    }
                    bottomSheet.cancel()
                }
            }

            bottomSheet.show()
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
            bottomSheet.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun onCardClick(id: Long) {
        lifecycleScope.launch{

            val downloadItem = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            }

            val resultItem = withContext(Dispatchers.IO){
                resultViewModel.getItemByURL(downloadItem.url)!!
            }
            if (parentFragmentManager.findFragmentByTag("configureDownloadSingleSheet") == null){
                val bottomSheet = ConfigureDownloadBottomSheetDialog(resultItem, downloadItem, this@DownloadMultipleBottomSheetDialog)
                bottomSheet.show(parentFragmentManager, "configureDownloadSingleSheet")
            }
        }
    }

    override fun onDelete(id: Long) {
        lifecycleScope.launch {
            val deletedItem = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            } ?: return@launch

            UiUtil.showGenericDeleteDialog(requireContext(), deletedItem.title){
                lifecycleScope.launch {
                    processingItemsCount--
                    downloadViewModel.deleteDownload(id)

                    if (processingItemsCount > 0){
                        Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.undo)) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    processingItemsCount++
                                    downloadViewModel.insert(deletedItem)
                                }
                            }.show()
                    }else{
                        dismiss()
                    }

                }
            }
        }


    }

    override fun onClick(p0: View?) {
    }

    override fun onDownloadItemUpdate(resultItemID: Long, item: DownloadItem) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                downloadViewModel.updateDownload(item)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (jobData.job != null){
            jobData.job?.cancel(CancellationException())
            downloadViewModel.deleteAllWithID(jobData.processingDownloadItemIDs)
        }
        super.onDismiss(dialog)
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val itemID = viewHolder.itemView.tag.toString().toLong()
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(itemID)
                            }
                            processingItemsCount--
                            withContext(Dispatchers.IO){
                                downloadViewModel.deleteDownload(deletedItem.id)
                            }


                            if (processingItemsCount > 0) {
                                Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_LONG)
                                    .setAction(getString(R.string.undo)) {
                                        processingItemsCount++
                                        downloadViewModel.insert(deletedItem)
                                    }.show()
                            }else{
                                dismiss()
                            }
                        }
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    requireContext(),
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .addSwipeRightBackgroundColor(
                        MaterialColors.getColor(
                            requireContext(),
                            R.attr.colorOnSurfaceInverse, Color.TRANSPARENT
                        )
                    )
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

    private fun downloadProcessDownloadsInBackground(jobData: DownloadViewModel.ProcessingItemsJob, processingFinished: Boolean, timeInMillis: Long = 0){
        Intent(requireActivity(), ProcessDownloadsInBackgroundService::class.java).also { intent ->
            intent.putExtra("itemType", jobData.originItemType)
            intent.putExtra("itemIDs", jobData.originItemIDs.toLongArray())
            intent.putExtra("processingItemIDs", jobData.processingDownloadItemIDs.toLongArray())
            intent.putExtra("timeInMillis", timeInMillis)
            intent.putExtra("processingFinished", processingFinished)
            requireActivity().startService(intent)
        }
    }

}

