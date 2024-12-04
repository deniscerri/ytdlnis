package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.ui.adapter.HistoryPaginatedAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * A fragment representing a list of Items.
 */
class HistoryFragment : Fragment(), HistoryPaginatedAdapter.OnItemClickListener{
    private lateinit var historyViewModel : HistoryViewModel
    private lateinit var downloadViewModel : DownloadViewModel

    private lateinit var fragmentView: View
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private lateinit var layoutinflater: LayoutInflater
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryPaginatedAdapter
    private lateinit var sortSheet: BottomSheetDialog
    private lateinit var uiHandler: Handler
    private lateinit var noResults: RelativeLayout
    private lateinit var selectionChips: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences
    private var websiteList: MutableList<String> = mutableListOf()
    private var totalCount = 0
    private var actionMode : ActionMode? = null

    private lateinit var sortChip: Chip

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentView = inflater.inflate(R.layout.fragment_history, container, false)
        mainActivity = activity as MainActivity?
        return fragmentView
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        topAppBar = view.findViewById(R.id.history_toolbar)
        noResults = view.findViewById(R.id.no_results)
        selectionChips = view.findViewById(R.id.history_selection_chips)
        uiHandler = Handler(Looper.getMainLooper())
        sortChip = view.findViewById(R.id.sortChip)

        val isInNavBar = NavbarUtil.getNavBarItems(requireActivity()).any { n -> n.itemId == R.id.historyFragment && n.isVisible }
        if (isInNavBar) {
            topAppBar.navigationIcon = null
        }else{
            mainActivity?.hideBottomNavigation()
        }
        topAppBar.setNavigationOnClickListener { mainActivity?.onBackPressedDispatcher?.onBackPressed() }
        historyAdapter = HistoryPaginatedAdapter(this, requireActivity())
        recyclerView = view.findViewById(R.id.recyclerviewhistorys)
        recyclerView.enableFastScroll()

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("history")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        recyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        noResults.isVisible = false
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        recyclerView.adapter = historyAdapter
        lifecycleScope.launch {
            historyViewModel.paginatedItems.collectLatest {
                withContext(Dispatchers.IO){
                    historyAdapter.submitData(it)
                }
            }
        }
        lifecycleScope.launch {
            historyViewModel.websites.collectLatest {
                if(it.isEmpty()) {
                    noResults.isVisible = true
                    selectionChips.isVisible = false
                    topAppBar.menu.children.firstOrNull { m -> m.itemId == R.id.filters }?.isVisible = false
                }else{
                    noResults.isVisible = false
                    selectionChips.isVisible = true
                    topAppBar.menu.children.firstOrNull { m -> m.itemId == R.id.filters }?.isVisible = true

                    websiteList = mutableListOf()
                    for (item in it){
                        if (item == "null" || item.isEmpty()) continue
                        if (!websiteList.any { it.contentEquals(item, true) }) websiteList.add(item)
                    }
                }
            }
        }

        lifecycleScope.launch {
            historyViewModel.totalCount.collectLatest {
                totalCount = it
            }
        }

        lifecycleScope.launch {
            historyViewModel.sortOrder.collectLatest {
                when(it){
                    SORTING.ASC -> sortChip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_down)
                    SORTING.DESC -> sortChip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_up)
                }
            }
        }

        lifecycleScope.launch {
            historyViewModel.sortType.collectLatest {
            when(it){
                    HistoryRepository.HistorySortType.AUTHOR -> sortChip.text = getString(R.string.author)
                    HistoryRepository.HistorySortType.DATE -> sortChip.text = getString(R.string.date_added)
                    HistoryRepository.HistorySortType.TITLE -> sortChip.text = getString(R.string.title)
                    HistoryRepository.HistorySortType.FILESIZE -> sortChip.text = getString(R.string.file_size)
                }
            }
        }

        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        lifecycleScope.launch{
            downloadViewModel.alreadyExistsUiState.collectLatest { res ->
                if (res.isNotEmpty()){
                    withContext(Dispatchers.Main){
                        val bundle = bundleOf(
                            Pair("duplicates", res)
                        )
                        findNavController().navigate(R.id.action_historyFragment_to_downloadsAlreadyExistDialog, bundle)
                    }
                    downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                }
            }
        }

        initMenu()
        initChips()
    }

    fun scrollToTop() {
        recyclerView.scrollToPosition(0)
        Handler(Looper.getMainLooper()).post {
            (topAppBar.parent as AppBarLayout).setExpanded(
                true,
                true
            )
        }
    }

    private fun initMenu() {
        val onActionExpandListener: MenuItem.OnActionExpandListener =
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    return true
                }
            }
        topAppBar.menu.findItem(R.id.search_history)
            .setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar.menu.findItem(R.id.search_history).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView.queryHint = getString(R.string.search_history_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar.menu.findItem(R.id.search_history).collapseActionView()
                historyViewModel.setQueryFilter(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                historyViewModel.setQueryFilter(newText)
                return true
            }
        })
        topAppBar.setOnClickListener { scrollToTop() }
        val showingDownloadQueue = NavbarUtil.getNavBarItems(requireContext()).any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        topAppBar.menu.findItem(R.id.download_queue).isVisible = !showingDownloadQueue
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.remove_history -> {
                    if(websiteList.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteFile = booleanArrayOf(false)
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                        deleteDialog.setMultiChoiceItems(
                            arrayOf(getString(R.string.delete_files_too)),
                            booleanArrayOf(false)
                        ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.deleteAll(deleteFile[0])
                        }
                        deleteDialog.show()
                    }
                }
                R.id.download_queue -> {
                    findNavController().navigate(R.id.downloadQueueMainFragment)
                }
                R.id.remove_deleted_history -> {
                    if(websiteList.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                        deleteDialog.setMessage(getString(R.string.confirm_delete_history_desc))
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.clearDeleted()
                        }
                        deleteDialog.show()
                    }
                }
                R.id.remove_duplicates -> {
                    if(websiteList.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                        deleteDialog.setMessage(getString(R.string.confirm_delete_history_desc))
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.deleteDuplicates()
                        }
                        deleteDialog.show()
                    }
                }
                R.id.filters -> {
                    val filterSheet = BottomSheetDialog(requireContext())
                    filterSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    filterSheet.setContentView(R.layout.history_other_filters_sheet)

                    //format status
                    val notDeleted = filterSheet.findViewById<TextView>(R.id.not_deleted)!!
                    val deleted = filterSheet.findViewById<TextView>(R.id.deleted)!!
                    when(historyViewModel.statusFilter.value) {
                        HistoryViewModel.HistoryStatus.ALL -> {
                            notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                            deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                        }
                        HistoryViewModel.HistoryStatus.DELETED -> {
                            notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0)
                            deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                        }
                        HistoryViewModel.HistoryStatus.NOT_DELETED -> {
                            notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                            deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0)
                        }
                        else -> {}
                    }

                    notDeleted.setOnClickListener {
                        val status = historyViewModel.statusFilter.value
                        when (status) {
                            HistoryViewModel.HistoryStatus.ALL -> {
                                notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.DELETED)
                            }
                            HistoryViewModel.HistoryStatus.NOT_DELETED -> {
                                notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.UNSET)
                            }
                            HistoryViewModel.HistoryStatus.DELETED -> {
                                notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.ALL)
                            }
                            else -> {
                                notDeleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.NOT_DELETED)
                            }
                        }
                    }
                    deleted.setOnClickListener {
                        val status = historyViewModel.statusFilter.value
                        when (status) {
                            HistoryViewModel.HistoryStatus.ALL -> {
                                deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.NOT_DELETED)
                            }
                            HistoryViewModel.HistoryStatus.NOT_DELETED -> {
                                deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.ALL)
                            }
                            HistoryViewModel.HistoryStatus.DELETED -> {
                                deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.UNSET)
                            }
                            else -> {
                                deleted.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check,0,0,0)
                                historyViewModel.setStatusFilter(HistoryViewModel.HistoryStatus.DELETED)
                            }
                        }
                    }

                    if (websiteList.size < 2) {
                        filterSheet.findViewById<View>(R.id.websiteFilters)?.isVisible = false
                    }else{
                        val websiteGroup = filterSheet.findViewById<ChipGroup>(R.id.websitesChipGroup)
                        val websiteFilter = historyViewModel.websiteFilter.value

                        for (i in websiteList.indices) {
                            val w = websiteList[i]
                            val tmp = layoutinflater.inflate(R.layout.filter_chip, websiteGroup, false) as Chip
                            tmp.text = w
                            tmp.id = i
                            tmp.setOnClickListener {
                                Log.e(TAG, tmp.isChecked.toString())
                                if (tmp.isChecked) {
                                    historyViewModel.setWebsiteFilter(tmp.text as String)
                                    tmp.isChecked = true
                                } else {
                                    historyViewModel.setWebsiteFilter("")
                                    tmp.isChecked = false
                                }
                            }
                            if (w == websiteFilter){
                                tmp.isChecked = true
                            }
                            websiteGroup!!.addView(tmp)
                        }
                    }

                    val displayMetrics = DisplayMetrics()
                    requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                    filterSheet.behavior.peekHeight = displayMetrics.heightPixels
                    filterSheet.show()
                }
            }
            true
        }
    }

    private fun changeSortIcon(item: TextView, order: SORTING){
        when(order){
            SORTING.DESC ->{
                item.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_up, 0,0,0)
            }
            SORTING.ASC ->                 {
                item.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_down, 0,0,0)
            }
        }
    }


    private fun initChips() {
        val sortChip = fragmentView.findViewById<Chip>(R.id.sortChip)
        sortChip.setOnClickListener {
            sortSheet = BottomSheetDialog(requireContext())
            sortSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            sortSheet.setContentView(R.layout.history_sort_sheet)

            val date = sortSheet.findViewById<TextView>(R.id.date)
            val title = sortSheet.findViewById<TextView>(R.id.title)
            val author = sortSheet.findViewById<TextView>(R.id.author)
            val filesize = sortSheet.findViewById<TextView>(R.id.filesize)

            val sortOptions = listOf(date!!, title!!, author!!, filesize!!)
            sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
            when(historyViewModel.sortType.value!!) {
                HistoryRepository.HistorySortType.DATE -> changeSortIcon(date, historyViewModel.sortOrder.value!!)
                HistoryRepository.HistorySortType.TITLE -> changeSortIcon(title, historyViewModel.sortOrder.value!!)
                HistoryRepository.HistorySortType.AUTHOR -> changeSortIcon(author, historyViewModel.sortOrder.value!!)
                HistoryRepository.HistorySortType.FILESIZE -> changeSortIcon(filesize, historyViewModel.sortOrder.value!!)
            }

            date.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.DATE)
                changeSortIcon(date, historyViewModel.sortOrder.value!!)
            }
            title.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.TITLE)
                changeSortIcon(title, historyViewModel.sortOrder.value!!)
            }
            author.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.AUTHOR)
                changeSortIcon(author, historyViewModel.sortOrder.value!!)
            }
            filesize.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.FILESIZE)
                changeSortIcon(filesize, historyViewModel.sortOrder.value!!)
            }
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            sortSheet.behavior.peekHeight = displayMetrics.heightPixels
            sortSheet.show()
        }

        //format
        val audio = fragmentView.findViewById<Chip>(R.id.audio_chip)
        audio.setOnClickListener {
            if (audio.isChecked) {
                historyViewModel.setTypeFilter(DownloadViewModel.Type.audio.toString())
                audio.isChecked = true
            } else {
                historyViewModel.setTypeFilter("")
                audio.isChecked = false
            }
        }
        val video = fragmentView.findViewById<Chip>(R.id.video_chip)
        video.setOnClickListener {
            if (video.isChecked) {
                historyViewModel.setTypeFilter(DownloadViewModel.Type.video.toString())
                video.isChecked = true
            } else {
                historyViewModel.setTypeFilter("")
                video.isChecked = false
            }
        }
        val command = fragmentView.findViewById<Chip>(R.id.command_chip)
        command.setOnClickListener {
            if (command.isChecked) {
                historyViewModel.setTypeFilter(DownloadViewModel.Type.command.toString())
                command.isChecked = true
            } else {
                historyViewModel.setTypeFilter("")
                command.isChecked = false
            }
        }


    }


    override fun onCardClick(itemID: Long, filePresent: Boolean) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                historyViewModel.getByID(itemID)
            }

            UiUtil.showHistoryItemDetailsCard(item, requireActivity(), filePresent,
                removeItem = { it, deleteFile ->
                    historyViewModel.delete(it, deleteFile)
                },
                redownloadItem = {
                    val downloadItem = downloadViewModel.createDownloadItemFromHistory(it)
                    runBlocking{
                        if (!filePresent) {
                            historyViewModel.delete(it, false)
                        }
                        downloadViewModel.queueDownloads(listOf(downloadItem))
                    }
                    historyViewModel.delete(it, false)
                },
                redownloadShowDownloadCard = {
                    findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                        Pair("result", downloadViewModel.createResultItemFromHistory(it)),
                        Pair("type", it.type),
                    ))
                }
            )
        }
    }

    override fun onCardSelect(isChecked: Boolean, position: Int) {
        lifecycleScope.launch {
            val selectedObjects = historyAdapter.getSelectedObjectsCount(totalCount)
            if (actionMode == null) actionMode = (activity as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)
            actionMode?.apply {
                if (selectedObjects == 0){
                    this.finish()
                }else{
                    actionMode?.title = "$selectedObjects ${getString(R.string.selected)}"
                    this.menu.findItem(R.id.select_between).isVisible = false
                    if(selectedObjects == 2){
                        val selectedIDs = contextualActionBar.getSelectedIDs().sortedBy { it }
                        val idsInMiddle = withContext(Dispatchers.IO){
                            historyViewModel.getIDsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
                        }
                        this.menu.findItem(R.id.select_between).isVisible = idsInMiddle.isNotEmpty()
                    }
                }
            }

            if (isChecked) {
                if (actionMode == null){
                    actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)
                }else{
                    actionMode!!.title = "$selectedObjects ${getString(R.string.selected)}"
                }
            }
            else {
                actionMode?.title = "$selectedObjects ${getString(R.string.selected)}"
                if (selectedObjects == 0){
                    actionMode?.finish()
                }
            }
        }
    }


    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.history_menu_context, menu)
            mode.title = "${historyAdapter.getSelectedObjectsCount(totalCount)} ${getString(R.string.selected)}"
            (activity as MainActivity).disableBottomNavigation()
            topAppBar.menu.forEach { it.isEnabled = false }
            return true
        }

        override fun onPrepareActionMode(
            mode: ActionMode?,
            menu: Menu?
        ): Boolean {
            return false
        }

        override fun onActionItemClicked(
            mode: ActionMode?,
            item: MenuItem?
        ): Boolean {
            return when (item!!.itemId) {
                R.id.select_between -> {
                    lifecycleScope.launch {
                        val selectedIDs = getSelectedIDs().sortedBy { it }
                        val idsInMiddle = withContext(Dispatchers.IO){
                            historyViewModel.getIDsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
                        }.toMutableList()
                        idsInMiddle.addAll(selectedIDs)
                        if (idsInMiddle.isNotEmpty()){
                            historyAdapter.checkMultipleItems(idsInMiddle)
                            actionMode?.title = "${idsInMiddle.count()} ${getString(R.string.selected)}"
                        }
                        mode?.menu?.findItem(R.id.select_between)?.isVisible = false
                    }
                    true
                }
                R.id.delete_results -> {
                    val deleteFile = booleanArrayOf(false)
                    val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setMultiChoiceItems(
                        arrayOf(getString(R.string.delete_files_too)),
                        booleanArrayOf(false)
                    ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        lifecycleScope.launch {
                            val selectedObjects = getSelectedIDs()
                            historyAdapter.clearCheckedItems()
                            historyViewModel.deleteAllWithIDs(selectedObjects, deleteFile[0])
                            actionMode?.finish()
                        }
                    }
                    deleteDialog.show()
                    true
                }
                R.id.share -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        val paths = withContext(Dispatchers.IO){
                            historyViewModel.getDownloadPathsFromIDs(selectedObjects)
                        }
                        FileUtil.shareFileIntent(requireContext(), paths.flatten())
                        historyAdapter.clearCheckedItems()
                        actionMode?.finish()
                    }
                    true
                }
                R.id.redownload -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        historyAdapter.clearCheckedItems()
                        actionMode?.finish()
                        if (selectedObjects.size == 1) {
                            val tmp = withContext(Dispatchers.IO) {
                                historyViewModel.getByID(selectedObjects.first())
                            }

                            findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                                Pair("result", downloadViewModel.createResultItemFromHistory(tmp)),
                                Pair("type", tmp.type)
                            ))
                        }else {
                            val showDownloadCard = sharedPreferences.getBoolean("download_card", true)
                            downloadViewModel.turnHistoryItemsToProcessingDownloads(selectedObjects, downloadNow = !showDownloadCard)
                            actionMode?.finish()
                            if (showDownloadCard){
                                val bundle = Bundle()
                                bundle.putLongArray("currentHistoryIDs", selectedObjects.toLongArray())
                                findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2, bundle)
                            }
                        }
                    }
                    true
                }
                R.id.select_all -> {
                    historyAdapter.checkAll()
                    val selectedCount = historyAdapter.getSelectedObjectsCount(totalCount)
                    mode?.title = "(${selectedCount}) ${resources.getString(R.string.all_items_selected)}"
                    true
                }
                R.id.invert_selected -> {
                    historyAdapter.invertSelected()
                    val selectedCount = historyAdapter.getSelectedObjectsCount(totalCount)
                    actionMode?.title = "$selectedCount ${getString(R.string.selected)}"
                    if (selectedCount == 0) actionMode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            (activity as MainActivity).enableBottomNavigation()
            historyAdapter.clearCheckedItems()
            topAppBar.menu.forEach { it.isEnabled = true }
        }

        suspend fun getSelectedIDs() : List<Long>{
            return if (historyAdapter.inverted || historyAdapter.checkedItems.isEmpty()){
                withContext(Dispatchers.IO){
                    historyViewModel.getItemIDsNotPresentIn(historyAdapter.checkedItems.toList())
                }
            }else{
                historyAdapter.checkedItems.toList()
            }
        }
    }


    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val itemID = viewHolder.itemView.tag.toString().toLong()
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO) {
                                historyViewModel.getByID(itemID)
                            }
                            historyAdapter.notifyItemChanged(position)
                            UiUtil.showRemoveHistoryItemDialog(deletedItem, requireActivity(),
                                delete = { item, deleteFile ->
                                    lifecycleScope.launch {
                                        withContext(Dispatchers.IO){
                                            historyViewModel.delete(item, deleteFile)
                                        }

                                        if (!deleteFile){
                                            Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_INDEFINITE)
                                                .setAction(getString(R.string.undo)) {
                                                    historyViewModel.insert(deletedItem)
                                                }.show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                    ItemTouchHelper.RIGHT -> {
                        lifecycleScope.launch {
                            val item = withContext(Dispatchers.IO) {
                                historyViewModel.getByID(itemID)
                            }
                            historyAdapter.notifyItemChanged(position)
                            findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                                Pair("result", downloadViewModel.createResultItemFromHistory(item)),
                                Pair("type", item.type)
                            ))
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
                    .addSwipeRightActionIcon(R.drawable.ic_refresh)
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

    override fun onButtonClick(itemID: Long, isPresent: Boolean) {
        if (isPresent){
            lifecycleScope.launch {
                val item = withContext(Dispatchers.IO){
                    historyViewModel.getByID(itemID)
                }
                FileUtil.shareFileIntent(requireContext(), item.downloadPath)
            }

        }
    }
    companion object {
        private const val TAG = "historyFragment"
    }
}