package com.deniscerri.ytdlnis.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.HistoryAdapter
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.DBManager.SORTING
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHistoryBinding
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadBottomSheetDialog
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.Extensions.enableFastScroll
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
class HistoryFragment : Fragment(), HistoryAdapter.OnItemClickListener{
    private lateinit var historyViewModel : HistoryViewModel
    private lateinit var downloadViewModel : DownloadViewModel

    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var topAppBar: MaterialToolbar? = null
    private lateinit var recyclerView: RecyclerView
    private var historyAdapter: HistoryAdapter? = null
    private var sortSheet: BottomSheetDialog? = null
    private var uiHandler: Handler? = null
    private var noResults: RelativeLayout? = null
    private var selectionChips: LinearLayout? = null
    private var websiteGroup: ChipGroup? = null
    private var historyList: List<HistoryItem?>? = null
    private var allhistoryList: List<HistoryItem?>? = null
    private var selectedObjects: ArrayList<HistoryItem>? = null
    private var actionMode : ActionMode? = null

    private lateinit var sortChip: Chip

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_history, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        return fragmentView
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        topAppBar = view.findViewById(R.id.history_toolbar)
        noResults = view.findViewById(R.id.no_results)
        selectionChips = view.findViewById(R.id.history_selection_chips)
        websiteGroup = view.findViewById(R.id.website_chip_group)
        uiHandler = Handler(Looper.getMainLooper())
        sortChip = view.findViewById(R.id.sortChip)
        selectedObjects = ArrayList()


        historyList = mutableListOf()
        allhistoryList = mutableListOf()

        historyAdapter =
            HistoryAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerviewhistorys)
        recyclerView.adapter = historyAdapter
        recyclerView.enableFastScroll()

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("history")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        recyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        noResults?.visibility = GONE


        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        historyViewModel.allItems.observe(viewLifecycleOwner) {
            allhistoryList = it
            if(it.isEmpty()){
                noResults!!.visibility = VISIBLE
                selectionChips!!.visibility = GONE
                websiteGroup!!.removeAllViews()
            }else{
                noResults!!.visibility = GONE
                selectionChips!!.visibility = VISIBLE
                updateWebsiteChips(it)
            }
        }

        historyViewModel.getFilteredList().observe(viewLifecycleOwner) {
            historyAdapter!!.submitList(it)
            historyList = it
            scrollToTop()
        }

        historyViewModel.sortOrder.observe(viewLifecycleOwner){
            if (it != null){
                when(it){
                    SORTING.ASC -> sortChip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_down)
                    SORTING.DESC -> sortChip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_up)
                }
            }
        }

        historyViewModel.sortType.observe(viewLifecycleOwner){
            if(it != null){
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
                if (res.downloadItems.isNotEmpty() || res.historyItems.isNotEmpty()) {
                    withContext(Dispatchers.Main){
                        kotlin.runCatching {
                            UiUtil.handleExistingDownloadsResponse(
                                requireActivity(),
                                requireActivity().lifecycleScope,
                                requireActivity().supportFragmentManager,
                                res,
                                downloadViewModel,
                                historyViewModel)
                        }
                    }
                    downloadViewModel.alreadyExistsUiState.value =  DownloadViewModel.AlreadyExistsUIState(
                        mutableListOf(),
                        mutableListOf()
                    )
                }
            }
        }


        initMenu()
        initChips()
    }

    private fun scrollToTop() {
        recyclerView!!.scrollToPosition(0)
        Handler(Looper.getMainLooper()).post {
            (topAppBar!!.parent as AppBarLayout).setExpanded(
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
        topAppBar!!.menu.findItem(R.id.search_history)
            .setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar!!.menu.findItem(R.id.search_history).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView.queryHint = getString(R.string.search_history_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar!!.menu.findItem(R.id.search_history).collapseActionView()
                historyViewModel.setQueryFilter(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                historyViewModel.setQueryFilter(newText)
                return true
            }
        })
        topAppBar!!.setOnClickListener { scrollToTop() }
        topAppBar!!.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.remove_history -> {
                    if(allhistoryList!!.isEmpty()){
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
                    if(allhistoryList!!.isEmpty()){
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
                    if(allhistoryList!!.isEmpty()){
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
        val sortChip = fragmentView!!.findViewById<Chip>(R.id.sortChip)
        sortChip.setOnClickListener {
            sortSheet = BottomSheetDialog(requireContext())
            sortSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
            sortSheet!!.setContentView(R.layout.history_sort_sheet)

            val date = sortSheet!!.findViewById<TextView>(R.id.date)
            val title = sortSheet!!.findViewById<TextView>(R.id.title)
            val author = sortSheet!!.findViewById<TextView>(R.id.author)
            val filesize = sortSheet!!.findViewById<TextView>(R.id.filesize)

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
            sortSheet!!.behavior.peekHeight = displayMetrics.heightPixels
            sortSheet!!.show()
        }

        //format
        val audio = fragmentView!!.findViewById<Chip>(R.id.audio_chip)
        audio.setOnClickListener {
            if (audio.isChecked) {
                historyViewModel.setFormatFilter("audio")
                audio.isChecked = true
            } else {
                historyViewModel.setFormatFilter("")
                audio.isChecked = false
            }
        }
        val video = fragmentView!!.findViewById<Chip>(R.id.video_chip)
        video.setOnClickListener {
            if (video.isChecked) {
                historyViewModel.setFormatFilter("video")
                video.isChecked = true
            } else {
                historyViewModel.setFormatFilter("")
                video.isChecked = false
            }
        }
        val command = fragmentView!!.findViewById<Chip>(R.id.command_chip)
        command.setOnClickListener {
            if (command.isChecked) {
                historyViewModel.setFormatFilter("command")
                command.isChecked = true
            } else {
                historyViewModel.setFormatFilter("")
                command.isChecked = false
            }
        }

        val notDeleted = fragmentView!!.findViewById<Chip>(R.id.not_deleted_chip)
        notDeleted.setOnClickListener {
            if (notDeleted.isChecked) {
                historyViewModel.setNotDeleted(true)
                notDeleted.isChecked = true
            } else {
                historyViewModel.setNotDeleted(false)
                notDeleted.isChecked = false
            }
        }
    }

    private fun updateWebsiteChips(list : List<HistoryItem>) {
        val websites = mutableListOf<String>()
        val websiteFilter = historyViewModel.websiteFilter.value
        for (item in list){
            if (!websites.contains(item.website.lowercase())) websites.add(item.website.lowercase())
        }
        websiteGroup!!.removeAllViews()
        if (websites.size <= 1) {
            requireView().findViewById<View>(R.id.website_divider).visibility = GONE
            return
        }
        //val websites = historyRecyclerViewAdapter!!.websites
        for (i in websites.indices) {
            val w = websites[i]
            if (w == "null" || w.isEmpty()) continue
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, websiteGroup, false) as Chip
            tmp.text = w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
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
         requireView().findViewById<View>(R.id.website_divider).visibility = VISIBLE
    }

    override fun onCardClick(itemID: Long, isPresent: Boolean) {
        val item = findItem(itemID)
        UiUtil.showHistoryItemDetailsCard(item, requireActivity(), isPresent,
            removeItem = { it, deleteFile ->
                historyViewModel.delete(it, deleteFile)
            },
            redownloadItem = {
                val downloadItem = downloadViewModel.createDownloadItemFromHistory(it)
                runBlocking{
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

    override fun onCardSelect(itemID: Long, isChecked: Boolean) {
        val item = findItem(itemID)
        if (isChecked) {
            selectedObjects!!.add(item!!)
            if (actionMode == null){
                actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)

            }else{
                actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            }
        }
        else {
            selectedObjects!!.remove(item)
            actionMode?.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            if (selectedObjects!!.isEmpty()){
                actionMode?.finish()
            }
        }
    }

    private fun findItem(id : Long): HistoryItem? {
        return historyList?.find { it?.id == id }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.history_menu_context, menu)
            mode.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            (activity as MainActivity).disableBottomNavigation()
            topAppBar!!.menu.forEach { it.isEnabled = false }
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
                        for (obj in selectedObjects!!){
                            historyViewModel.delete(obj, deleteFile[0])
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.share -> {
                    UiUtil.shareFileIntent(requireContext(), selectedObjects!!.map { it.downloadPath }.flatten())
                    clearCheckedItems()
                    actionMode?.finish()
                    true
                }
                R.id.select_all -> {
                    historyAdapter?.checkAll(historyList)
                    selectedObjects?.clear()
                    historyList?.forEach { selectedObjects?.add(it!!) }
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    historyAdapter?.invertSelected(historyList)
                    val invertedList = arrayListOf<HistoryItem>()
                    historyList?.forEach {
                        if (!selectedObjects?.contains(it)!!) invertedList.add(it!!)
                    }
                    selectedObjects?.clear()
                    selectedObjects?.addAll(invertedList)
                    actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
                    if (invertedList.isEmpty()) actionMode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            (activity as MainActivity).enableBottomNavigation()
            clearCheckedItems()
            topAppBar!!.menu.forEach { it.isEnabled = true }
        }
    }

    private fun clearCheckedItems(){
        historyAdapter?.clearCheckeditems()
        selectedObjects?.clear()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = historyList!![position]
                        historyAdapter!!.notifyItemChanged(position)
                        UiUtil.showRemoveHistoryItemDialog(deletedItem!!, requireActivity(),
                            delete = { item, deleteFile ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO){
                                        historyViewModel.delete(item, deleteFile)
                                    }

                                    if (!deleteFile){
                                        Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_LONG)
                                            .setAction(getString(R.string.undo)) {
                                                historyViewModel.insert(deletedItem)
                                            }.show()
                                    }
                                }
                            })
                    }
                    ItemTouchHelper.RIGHT -> {
                        val item = historyList!![position]!!
                        historyAdapter!!.notifyItemChanged(position)
                        findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                            Pair("result", downloadViewModel.createResultItemFromHistory(item)),
                            Pair("type", item.type)
                        ))
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
                    viewHolder!!,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

    override fun onButtonClick(itemID: Long, isPresent: Boolean) {
        if (isPresent){
            UiUtil.shareFileIntent(requireContext(), historyList!!.first { it!!.id == itemID }!!.downloadPath)
        }
    }
    companion object {
        private const val TAG = "historyFragment"
    }
}