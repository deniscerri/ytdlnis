package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.adapter.GenericDownloadAdapter
import com.deniscerri.ytdl.ui.adapter.ScheduledDownloadAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.forceFastScrollMode
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class ScheduledDownloadsFragment : Fragment(), ScheduledDownloadAdapter.OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var scheduledRecyclerView : RecyclerView
    private lateinit var preferences : SharedPreferences
    private lateinit var adapter : ScheduledDownloadAdapter
    private lateinit var noResults : RelativeLayout
    private var actionMode : ActionMode? = null
    private var totalSize = 0

    private lateinit var listHeader : ConstraintLayout
    private lateinit var count : TextView
    private lateinit var headerMenuBtn : TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.generic_list, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return fragmentView
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter =
            ScheduledDownloadAdapter(
                this,
                requireActivity()
            )

        noResults = view.findViewById(R.id.no_results)
        scheduledRecyclerView = view.findViewById(R.id.download_recyclerview)
        scheduledRecyclerView.forceFastScrollMode()
        scheduledRecyclerView.adapter = adapter
        scheduledRecyclerView.enableFastScroll()
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("scheduled")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(scheduledRecyclerView)
        }
        scheduledRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))


        lifecycleScope.launch {
            downloadViewModel.scheduledDownloads.collectLatest {
                adapter.submitData(it)
            }
        }

        listHeader = view.findViewById(R.id.list_header)
        count = view.findViewById(R.id.count)
        headerMenuBtn = view.findViewById(R.id.dropdown_menu)

        headerMenuBtn.setOnClickListener {
            val popup = PopupMenu(activity, it)
            popup.menuInflater.inflate(R.menu.scheduled_header_menu, popup.menu)
            popup.setOnMenuItemClickListener { m ->
                when(m.itemId){
                    R.id.download_now -> {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                downloadViewModel.resetScheduleItemForAllScheduledItemsAndStartDownload()
                            }
                        }
                    }
                    R.id.delete_all -> {
                        UiUtil.showGenericDeleteAllDialog(requireContext()) {
                            downloadViewModel.deleteScheduled()
                        }
                    }
                    R.id.copy_urls -> {
                        lifecycleScope.launch {
                            val urls = withContext(Dispatchers.IO){
                                downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Scheduled))
                            }
                            UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                        }
                    }
                }
                true
            }

            popup.show()
        }

        lifecycleScope.launch {
            downloadViewModel.scheduledDownloadsCount.collectLatest {
                totalSize = it
                listHeader.isVisible = it > 0
                count.text = "$it ${getString(R.string.items)}"
                noResults.visibility = if (it == 0) View.VISIBLE else View.GONE
            }
        }

    }

    override fun onActionButtonClick(itemID: Long) {
        lifecycleScope.launch {
            actionMode?.finish()
            runCatching {
                withContext(Dispatchers.IO){
                    downloadViewModel.resetScheduleTimeForItemsAndStartDownload(listOf(itemID))
                }
            }.onFailure {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCardClick(itemID: Long, position: Int) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }

            UiUtil.showDownloadItemDetailsCard(
                item,
                requireActivity(),
                DownloadRepository.Status.valueOf(item.status),
                removeItem = { it: DownloadItem, sheet: BottomSheetDialog ->
                    sheet.hide()
                    removeItem(it, sheet)
                },
                downloadItem = {
                    downloadViewModel.deleteDownload(it.id)
                    it.downloadStartTime = 0
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(it))
                    }
                },
                longClickDownloadButton = {
                    findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                        Pair("downloadItem", it),
                        Pair("result", downloadViewModel.createResultItemFromDownload(it)),
                        Pair("type", it.type)
                    )
                    )
                },
                scheduleButtonClick = {downloadItem ->
                    UiUtil.showDatePicker(parentFragmentManager, preferences) {
                        Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + it.time, Toast.LENGTH_LONG).show()
                        downloadViewModel.deleteDownload(downloadItem.id)
                        downloadItem.downloadStartTime = it.timeInMillis
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(downloadItem))
                            adapter.notifyItemChanged(position)
                        }
                    }
                }
            )
        }
    }

    override fun onCardSelect(isChecked: Boolean, position: Int) {
        lifecycleScope.launch {
            val selectedObjects = adapter.getSelectedObjectsCount(totalSize)
            if (actionMode == null) actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)
            actionMode?.apply {
                if (selectedObjects == 0){
                    this.finish()
                }else{
                    this.title = "$selectedObjects ${getString(R.string.selected)}"
                    this.menu.findItem(R.id.select_between).isVisible = false
                    if (selectedObjects == 2){
                        val selectedIDs = contextualActionBar.getSelectedIDs().sortedBy { it }
                        val idsInMiddle = withContext(Dispatchers.IO){
                            downloadViewModel.getScheduledIDsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
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


    private fun removeItem(item: DownloadItem, bottomSheet: BottomSheetDialog?){
        bottomSheet?.hide()
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            downloadViewModel.deleteDownload(item.id)
        }
        deleteDialog.show()
    }


    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.scheduled_downloads_menu_context, menu)
            mode.title = "${adapter.getSelectedObjectsCount(totalSize)} ${getString(R.string.selected)}"
            headerMenuBtn.isEnabled = false
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
                            downloadViewModel.getScheduledIDsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
                        }.toMutableList()
                        idsInMiddle.addAll(selectedIDs)
                        if (idsInMiddle.isNotEmpty()){
                            adapter.checkMultipleItems(idsInMiddle)
                            actionMode?.title = "${idsInMiddle.count()} ${getString(R.string.selected)}"
                        }
                        mode?.menu?.findItem(R.id.select_between)?.isVisible = false
                    }
                    true
                }
                R.id.delete_results -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        lifecycleScope.launch {
                            val selectedObjects = getSelectedIDs()
                            adapter.clearCheckedItems()
                            downloadViewModel.deleteAllWithID(selectedObjects)
                            actionMode?.finish()
                        }
                    }
                    deleteDialog.show()
                    true
                }
                R.id.download -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        adapter.clearCheckedItems()
                        withContext(Dispatchers.IO) {
                            downloadViewModel.resetScheduleTimeForItemsAndStartDownload(selectedObjects)
                        }
                        actionMode?.finish()
                    }
                    true
                }
                R.id.select_all -> {
                    adapter.checkAll()
                    mode?.title = "(${adapter.getSelectedObjectsCount(totalSize)}) ${resources.getString(R.string.all_items_selected)}"
                    true
                }
                R.id.invert_selected -> {
                    adapter.invertSelected()
                    val selectedObjects = adapter.getSelectedObjectsCount(totalSize)
                    actionMode!!.title = "$selectedObjects ${getString(R.string.selected)}"
                    if (selectedObjects == 0) actionMode?.finish()
                    true
                }
                R.id.copy_urls -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        val urls = withContext(Dispatchers.IO){
                            downloadViewModel.getURLsByIds(selectedObjects)
                        }
                        UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                        actionMode?.finish()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            adapter.clearCheckedItems()
            headerMenuBtn.isEnabled = true
        }

        suspend fun getSelectedIDs() : List<Long>{
            return if (adapter.inverted || adapter.checkedItems.isEmpty()){
                withContext(Dispatchers.IO){
                    downloadViewModel.getItemIDsNotPresentIn(adapter.checkedItems.toList(), listOf(
                        DownloadRepository.Status.Scheduled))
                }
            }else{
                adapter.checkedItems.toList()
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
                            val deletedItem = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(itemID)
                            }
                            downloadViewModel.deleteDownload(deletedItem.id)
                            Snackbar.make(scheduledRecyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title.ifEmpty { deletedItem.url }, Snackbar.LENGTH_INDEFINITE)
                                .setAction(getString(R.string.undo)) {
                                    downloadViewModel.insert(deletedItem)
                                }.show()
                        }
                    }
                    ItemTouchHelper.RIGHT -> {
                        onActionButtonClick(itemID)
                        adapter.notifyItemChanged(position)
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
                            R.attr.colorOnSurfaceInverse,Color.TRANSPARENT
                        )
                    )
                    .addSwipeRightActionIcon(R.drawable.ic_downloads)
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

}