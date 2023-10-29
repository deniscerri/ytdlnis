package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.receiver.ShareActivity
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*


class DownloadBottomSheetDialog(private var result: ResultItem, private val type: Type, private var currentDownloadItem: DownloadItem? = null) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var commandTemplateDao : CommandTemplateDao
    private lateinit var infoUtil: InfoUtil
    private lateinit var sharedPreferences : SharedPreferences
    private lateinit var updateItem : Button
    private lateinit var view: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        commandTemplateDao = DBManager.getInstance(requireContext()).commandTemplateDao
        infoUtil = InfoUtil(requireContext())
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    @SuppressLint("RestrictedApi", "InflateParams")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        view = LayoutInflater.from(context).inflate(R.layout.download_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)
        updateItem = view.findViewById(R.id.update_item)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        var commandTemplateNr = 0
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                commandTemplateNr = commandTemplateDao.getTotalNumber()
                if (!Patterns.WEB_URL.matcher(result.url).matches()) commandTemplateNr++
                if(commandTemplateNr <= 0){
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 0.3f
                }
            }
        }

        //check if the item has formats and its audio-only
        val formats = result.formats
        val isAudioOnly = formats.isNotEmpty() && formats.none { !it.format_note.contains("audio") }
        if (isAudioOnly){
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.isClickable = true
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.alpha = 0.3f
        }

        //check if the item is coming from a text file
        val isCommandOnly = (type == Type.command && !Patterns.WEB_URL.matcher(result.url).matches())
        if (isCommandOnly){
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.isClickable = false
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.alpha = 0.3f

            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.isClickable = false
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.alpha = 0.3f

            updateItem.visibility = View.GONE
        }

        //remove outdated player url of 1hr so it can refetch it in the cut player
        if (result.creationTime > System.currentTimeMillis() - 3600000) result.urls = ""

        val fragmentManager = parentFragmentManager
        fragmentAdapter = DownloadFragmentAdapter(
            fragmentManager,
            lifecycle,
            result,
            currentDownloadItem
        )

        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        when(type) {
            Type.audio -> {
                tabLayout.getTabAt(0)!!.select()
                viewPager2.setCurrentItem(0, false)
            }
            Type.video -> {
                if (isAudioOnly){
                    tabLayout.getTabAt(0)!!.select()
                    viewPager2.setCurrentItem(0, false)
                    Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
                }else{
                    tabLayout.getTabAt(1)!!.select()
                    viewPager2.setCurrentItem(1, false)
                }
            }
            else -> {
                tabLayout.getTabAt(2)!!.select()
                viewPager2.postDelayed( {
                    viewPager2.setCurrentItem(2, false)
                }, 200)
            }
        }

        sharedPreferences.edit(commit = true) {
            putString("last_used_download_type",
                type.toString())
        }


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab!!.position == 2 && commandTemplateNr == 0){
                    tabLayout.selectTab(tabLayout.getTabAt(1))
                    Toast.makeText(context, getString(R.string.add_template_first), Toast.LENGTH_SHORT).show()
                }else if (tab.position == 1 && isAudioOnly){
                    tabLayout.selectTab(tabLayout.getTabAt(0))
                    Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
                }
                else{
                    viewPager2.setCurrentItem(tab.position, false)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        viewPager2.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
                runCatching {
                    sharedPreferences.edit(commit = true) {
                        putString("last_used_download_type",
                            listOf(Type.audio, Type.video, Type.command)[position].toString())
                    }
                    updateTitleAuthorWhenSwitching()
                }
            }
        })

        viewPager2.setPageTransformer(BackgroundToForegroundPageTransformer())

        val shownFields = sharedPreferences.getStringSet("modify_download_card", setOf())!!.toList().ifEmpty { requireContext().getStringArray(R.array.modify_download_card_values).toList() }

        val scheduleBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_schedule_button)
        scheduleBtn.visibility = if(shownFields.contains("schedule")){
            View.VISIBLE
        }else{
            View.GONE
        }
        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)


        scheduleBtn.setOnClickListener{
            UiUtil.showDatePicker(fragmentManager) {
                scheduleBtn.isEnabled = false
                download.isEnabled = false
                val item: DownloadItem = getDownloadItem()
                item.downloadStartTime = it.timeInMillis
                runBlocking {
                    downloadViewModel.queueDownloads(listOf(item))
                    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(item.downloadStartTime)
                    Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + date, Toast.LENGTH_LONG).show()
                }
                dismiss()
            }
        }
        download!!.setOnClickListener {
            scheduleBtn.isEnabled = false
            download.isEnabled = false
            val item: DownloadItem = getDownloadItem()
            runBlocking {
                downloadViewModel.queueDownloads(listOf(item))
            }
            dismiss()
        }

        download.setOnLongClickListener {
            val dd = MaterialAlertDialogBuilder(requireContext())
            dd.setTitle(getString(R.string.save_for_later))
            dd.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
            dd.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch(Dispatchers.IO){
                    val item = getDownloadItem()
                    item.status = DownloadRepository.Status.Saved.toString()
                    downloadViewModel.updateDownload(item)
                    dismiss()
                }
            }
            dd.show()
            true
        }

        val link = view.findViewById<Button>(R.id.bottom_sheet_link)
        link.visibility = if(shownFields.contains("url")){
            View.VISIBLE
        }else{
            View.GONE
        }

        if (Patterns.WEB_URL.matcher(result.url).matches()){
            link.text = result.url
            link.setOnClickListener{
                UiUtil.openLinkIntent(requireContext(), result.url)
            }
            link.setOnLongClickListener{
                UiUtil.copyLinkToClipBoard(requireContext(), result.url)
                true
            }

            //if auto-update after the card is open is off
            if (result.title.isEmpty() && currentDownloadItem == null) {
                if(sharedPreferences.getBoolean("quick_download", false)) {
                    (updateItem.parent as LinearLayout).visibility = View.VISIBLE
                    updateItem.setOnClickListener {
                        (updateItem.parent as LinearLayout).visibility = View.GONE
                        initUpdateData(view)
                    }
                }
            }else{
                (updateItem.parent as LinearLayout).visibility = View.GONE
            }

        }else{
            link.visibility = View.GONE
            (updateItem.parent as LinearLayout).visibility = View.GONE
        }


        //update in the background if there is no data
        if(result.title.isEmpty() && currentDownloadItem == null && !sharedPreferences.getBoolean("quick_download", false) && type != Type.command){
            initUpdateData(view)
        }else {
            val usingGenericFormatsOrEmpty = result.formats.isEmpty() || result.formats.any { it.format_note.contains("ytdlnisgeneric") }
            if (usingGenericFormatsOrEmpty && sharedPreferences.getBoolean("update_formats", false) && !sharedPreferences.getBoolean("quick_download", false)){
                initUpdateFormats(result.url)
            }
        }

        CoroutineScope(Dispatchers.IO).launch{
            downloadViewModel.uiState.collectLatest { res ->
                if (res.errorMessage != null) {
                    withContext(Dispatchers.Main){
                        kotlin.runCatching {
                            UiUtil.handleDownloadsResponse(
                                requireActivity(),
                                requireActivity().lifecycleScope,
                                requireActivity().supportFragmentManager,
                                res,
                                downloadViewModel,
                                historyViewModel)
                        }
                    }
                    downloadViewModel.uiState.value =  DownloadViewModel.DownloadsUiState(
                        errorMessage = null,
                        actions = null
                    )
                }
            }
        }

        lifecycleScope.launch {
            resultViewModel.uiState.collectLatest { res ->
                if (res.errorMessage != null){
                    kotlin.runCatching { UiUtil.handleResultResponse(requireActivity(), res, closed = {
                        dismiss()
                    }) }
                    resultViewModel.uiState.update {it.copy(errorMessage  = null, actions  = null) }
                }
            }
        }
    }
    private fun getDownloadItem(selectedTabPosition: Int = tabLayout.selectedTabPosition) : DownloadItem{
        return when(selectedTabPosition){
            0 -> {
                val f = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                f.downloadItem
            }
            1 -> {
                val f = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                f.downloadItem
            }
            else -> {
                val f = fragmentManager?.findFragmentByTag("f2") as DownloadCommandFragment
                f.downloadItem
            }
        }
    }

    private fun updateTitleAuthorWhenSwitching(){
        val prevDownloadItem = getDownloadItem(
            if (viewPager2.currentItem == 1) 0 else 1
        )
        fragmentAdapter.setTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)

        when(viewPager2.currentItem){
            0 -> {
                val f = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                f.updateTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)
            }
            1 -> {
                val f = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                f.updateTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)
            }
            else -> {}
        }
    }

    private fun initUpdateData(v: View){
        val shimmerLoading = v.findViewById<ShimmerFrameLayout>(R.id.shimmer_loading_title)
        val title = v.findViewById<View>(R.id.bottom_sheet_title)
        val shimmerLoadingSubtitle = v.findViewById<ShimmerFrameLayout>(R.id.shimmer_loading_subtitle)
        val subtitle = v.findViewById<View>(R.id.bottom_sheet_subtitle)

        val updateJob = CoroutineScope(SupervisorJob()).launch(Dispatchers.IO){
            withContext(Dispatchers.Main){
                title.visibility = View.GONE
                subtitle.visibility = View.GONE
                shimmerLoading.visibility = View.VISIBLE
                shimmerLoadingSubtitle.visibility = View.VISIBLE
                shimmerLoading.startShimmer()
                shimmerLoadingSubtitle.startShimmer()
            }


            if (result.url.isBlank()){
                withContext(Dispatchers.Main){dismiss()}
                return@launch
            }

            val result = resultViewModel.parseQueries(listOf(result.url))
            if (result.isEmpty()){
                return@launch
            }

            if (result.size == 1 && result[0] != null){
                fragmentAdapter.setResultItem(result[0]!!)
                withContext(Dispatchers.Main){
                    runCatching {
                        val f = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                        f.updateUI(result[0])
                    }

                    runCatching {
                        val f1 = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                        f1.updateUI(result[0])
                    }
                }

                withContext(Dispatchers.Main){
                    title.visibility = View.VISIBLE
                    subtitle.visibility = View.VISIBLE
                    shimmerLoading.visibility = View.GONE
                    shimmerLoadingSubtitle.visibility = View.GONE
                    shimmerLoading.stopShimmer()
                    shimmerLoadingSubtitle.stopShimmer()
                }

                val usingGenericFormatsOrEmpty = result[0]!!.formats.isEmpty() || result[0]!!.formats.any { it.format_note.contains("ytdlnisgeneric") }

                if (usingGenericFormatsOrEmpty && sharedPreferences.getBoolean("update_formats", false)){
                    initUpdateFormats(result[0]!!.url)
                }

            }else{
                //open multi download card instead
                if (activity is ShareActivity){
                    val preferredType = DownloadViewModel.Type.valueOf(sharedPreferences.getString("preferred_download_type", "video")!!)
                    withContext(Dispatchers.Main){
                        val playlistSelect = SelectPlaylistItemsDialog(items = result, type = preferredType)
                        parentFragmentManager.addFragmentOnAttachListener { fragmentManager, fragment ->
                            dismiss()
                        }
                        playlistSelect.show(parentFragmentManager, "downloadPlaylistSheet")
                    }
                }else{
                    dismiss()
                }
            }
        }
        shimmerLoading.setOnClickListener {
            updateJob.cancel()
            (updateItem.parent as LinearLayout).visibility = View.VISIBLE
        }
        updateJob.invokeOnCompletion {
            requireActivity().runOnUiThread {
                title.visibility = View.VISIBLE
                subtitle.visibility = View.VISIBLE
                shimmerLoading.visibility = View.GONE
                shimmerLoadingSubtitle.visibility = View.GONE
                shimmerLoading.stopShimmer()
                shimmerLoadingSubtitle.stopShimmer()
            }
        }
        updateJob.start()
    }

    private fun initUpdateFormats(url: String){
        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
            withContext(Dispatchers.Main){
                runCatching {
                    val f1 = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                    f1.view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.visibility = View.VISIBLE
                }
                runCatching {
                    val f1 = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                    f1.view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.visibility = View.VISIBLE
                }
            }

            val formats = runCatching {
                infoUtil.getFormats(url).toMutableList()
            }.getOrElse {
                mutableListOf()
            }

            withContext(Dispatchers.Main){
                runCatching {
                    val f1 = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                    val resultItem = downloadViewModel.createResultItemFromDownload(f1.downloadItem)
                    resultItem.formats = formats
                    fragmentAdapter.setResultItem(resultItem)
                    f1.updateUI(resultItem)
                    f1.view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.visibility = View.GONE
                }
                runCatching {
                    val f1 = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                    val resultItem = downloadViewModel.createResultItemFromDownload(f1.downloadItem)
                    resultItem.formats = formats
                    fragmentAdapter.setResultItem(resultItem)
                    f1.updateUI(resultItem)
                    f1.view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.visibility = View.GONE
                }
            }

            if (formats.isNotEmpty()){
                result.formats = formats
                resultViewModel.update(result)
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
            if (activity is ShareActivity && !parentFragmentManager.fragments.map { it.tag }.contains("downloadPlaylistSheet")){
                (activity as ShareActivity).finish()
            }
        }
    }
}

