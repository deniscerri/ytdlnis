package com.deniscerri.ytdlnis.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
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
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadBottomSheetDialog
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.Extensions.enableFastScroll
import com.deniscerri.ytdlnis.util.Extensions.forceFastScrollMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class QueuedDownloadsFragment : Fragment(), GenericDownloadAdapter.OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var queuedRecyclerView : RecyclerView
    private lateinit var adapter : GenericDownloadAdapter
    private lateinit var noResults : RelativeLayout
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var fileSize: TextView
    private var totalSize: Int = 0
    private var actionMode : ActionMode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_inqueue, container, false)
        activity = getActivity()
        notificationUtil = NotificationUtil(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return fragmentView
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fileSize = view.findViewById(R.id.filesize)
        adapter =
            GenericDownloadAdapter(
                this,
                requireActivity()
            )

        noResults = view.findViewById(R.id.no_results)
        queuedRecyclerView = view.findViewById(R.id.download_recyclerview)
        queuedRecyclerView.forceFastScrollMode()
        queuedRecyclerView.adapter = adapter
        queuedRecyclerView.enableFastScroll()

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("queued")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(queuedRecyclerView)
        }
        queuedRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        lifecycleScope.launch {
            downloadViewModel.queuedDownloads.collectLatest {
                adapter.submitData(it)
            }
        }

        adapter.addLoadStateListener { loadState ->
            lifecycleScope.launch {
                if (loadState.append.endOfPaginationReached )
                {
                    if ( adapter.itemCount < 1){
                        fileSize.visibility = View.GONE
                    }else{
                        val size = withContext(Dispatchers.IO){
                            FileUtil.convertFileSize(downloadViewModel.getQueuedCollectedFileSize())
                        }
                        if (size == "?")  fileSize.visibility = View.GONE
                        else {
                            fileSize.visibility = View.VISIBLE
                            fileSize.text = "${getString(R.string.file_size)}: ~ $size"
                        }
                    }
                }
            }
        }

        downloadViewModel.getTotalSize(listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused)).observe(viewLifecycleOwner){
            totalSize = it
            noResults.visibility = if (it == 0) View.VISIBLE else View.GONE
        }
    }

    override fun onActionButtonClick(itemID: Long) {
        removeItem(itemID)
    }

    override fun onCardClick(itemID: Long) {
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
                    removeItem(it.id)
                },
                downloadItem = {
                    downloadViewModel.deleteDownload(it.id)
                    it.downloadStartTime = 0
                    WorkManager.getInstance(requireContext()).cancelAllWorkByTag(it.id.toString())
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(it))
                    }
                },
                longClickDownloadButton = {
                    findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                        Pair("downloadItem", it),
                        Pair("result", downloadViewModel.createResultItemFromDownload(it)),
                        Pair("type", it.type)
                    ))
                },
                scheduleButtonClick = {downloadItem ->
                    UiUtil.showDatePicker(parentFragmentManager) {
                        Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + it.time, Toast.LENGTH_LONG).show()
                        downloadViewModel.deleteDownload(downloadItem.id)
                        downloadItem.downloadStartTime = it.timeInMillis
                        WorkManager.getInstance(requireContext()).cancelAllWorkByTag(downloadItem.id.toString())
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(downloadItem))
                        }
                    }
                }
            )
        }
    }

    override fun onCardSelect(isChecked: Boolean, position: Int) {
        lifecycleScope.launch {
            val selectedObjects = adapter.getSelectedObjectsCount(totalSize)
            val now = System.currentTimeMillis()
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
            if (actionMode != null){
                actionMode!!.menu.getItem(2).isVisible = withContext(Dispatchers.IO){
                    downloadViewModel.checkAllQueuedItemsAreScheduledAfterNow(adapter.checkedItems, adapter.inverted, now)
                }

                actionMode!!.menu.getItem(1).isVisible = selectedObjects == 1 && position > 0
            }
        }
    }

    private fun removeItem(id: Long){
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            }
            val deleteDialog = MaterialAlertDialogBuilder(requireContext())
            deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
            deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
            deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                item.status = DownloadRepository.Status.Cancelled.toString()
                lifecycleScope.launch(Dispatchers.IO){
                    downloadViewModel.updateDownload(item)
                }

                Snackbar.make(queuedRecyclerView, getString(R.string.cancelled) + ": " + item.title, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            downloadViewModel.deleteDownload(item.id)
                            downloadViewModel.queueDownloads(listOf(item))
                        }
                    }.show()
            }
            deleteDialog.show()
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.queued_menu_context, menu)
            mode.title = "${adapter.getSelectedObjectsCount(totalSize)} ${getString(R.string.selected)}"
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
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        lifecycleScope.launch {
                            val selectedObjects = if (adapter.inverted || adapter.checkedItems.isEmpty()){
                                withContext(Dispatchers.IO){
                                    downloadViewModel.getItemIDsNotPresentIn(adapter.checkedItems, listOf(
                                        DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused))
                                }
                            }else{
                                adapter.checkedItems.toList()
                            }
                            adapter.clearCheckedItems()
                            for (id in selectedObjects){
                                YoutubeDL.getInstance().destroyProcessById(id.toInt().toString())
                                WorkManager.getInstance(requireContext()).cancelAllWorkByTag(id.toInt().toString())
                                notificationUtil.cancelDownloadNotification(id.toInt())
                            }
                            downloadViewModel.deleteAllWithID(selectedObjects)
                            actionMode?.finish()
                        }

                    }
                    deleteDialog.show()
                    true
                }
                R.id.download -> {
                    lifecycleScope.launch {
                        val selectedObjects = if (adapter.inverted || adapter.checkedItems.isEmpty()){
                            withContext(Dispatchers.IO){
                                downloadViewModel.getItemIDsNotPresentIn(adapter.checkedItems, listOf(
                                    DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused))
                            }
                        }else{
                            adapter.checkedItems.toList()
                        }
                        adapter.clearCheckedItems()
                        for (id in selectedObjects){
                            WorkManager.getInstance(requireContext()).cancelAllWorkByTag(id.toInt().toString())
                        }
                        withContext(Dispatchers.IO) {
                            downloadViewModel.resetScheduleTimeForItemsAndStartDownload(selectedObjects)
                        }
                        actionMode?.finish()
                    }
                    true
                }
                R.id.select_all -> {
                    adapter.checkAll()
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    adapter.invertSelected()
                    val selectedObjects = adapter.getSelectedObjectsCount(totalSize)
                    actionMode!!.title = "$selectedObjects ${getString(R.string.selected)}"
                    if (selectedObjects == 0) actionMode?.finish()
                    true
                }
                R.id.up -> {
                    lifecycleScope.launch {
                        if (adapter.getSelectedObjectsCount(totalSize) == 1){
                            val selectedObjects = if (adapter.inverted || adapter.checkedItems.isEmpty()){
                                withContext(Dispatchers.IO){
                                    downloadViewModel.getItemIDsNotPresentIn(adapter.checkedItems, listOf(
                                        DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused))
                                }
                            }else{
                                adapter.checkedItems.toList()
                            }

                            adapter.clearCheckedItems()
                            withContext(Dispatchers.IO){
                                downloadViewModel.putAtTopOfQueue(selectedObjects[0])
                            }
                            actionMode?.finish()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            adapter.clearCheckedItems()
        }
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
                            adapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                            removeItem(deletedItem.id)
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
}