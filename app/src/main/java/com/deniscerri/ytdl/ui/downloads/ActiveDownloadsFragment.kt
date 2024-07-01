package com.deniscerri.ytdl.ui.downloads

import android.animation.AnimatorSet
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
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
import com.deniscerri.ytdl.ui.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdl.ui.adapter.QueuedDownloadAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.forceFastScrollMode
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class ActiveDownloadsFragment : Fragment(), ActiveDownloadAdapter.OnItemClickListener, QueuedDownloadAdapter.OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel

    private lateinit var activeDownloadsLinear: LinearLayout
    private lateinit var activeDownloadsNoResults: View
    private lateinit var activeRecyclerView : RecyclerView
    private lateinit var activeDownloads : ActiveDownloadAdapter

    private lateinit var queuedDownloadsLinear: LinearLayout
    private lateinit var queuedRecyclerView : RecyclerView
    private lateinit var queuedDownloads : QueuedDownloadAdapter
    private lateinit var queuedFilesizeCount : TextView
    private var queuedTotalSize = 0
    private lateinit var queuedDragHandleToggle : TextView
    private var queuedActionMode : ActionMode? = null
    private lateinit var inQueueLabel : MaterialButton

    private lateinit var activeQueuedLinear: LinearLayout

    private lateinit var notificationUtil: NotificationUtil
    private lateinit var pause: ExtendedFloatingActionButton
    private lateinit var resume: ExtendedFloatingActionButton
    private lateinit var noResults: RelativeLayout
    private lateinit var workManager: WorkManager
    private lateinit var preferences: SharedPreferences


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_active, container, false)
        return fragmentView
    }

    @OptIn(ExperimentalBadgeUtils::class)
    @SuppressLint("NotifyDataSetChanged", "RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity = getActivity()
        notificationUtil = NotificationUtil(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        workManager = WorkManager.getInstance(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        activeDownloadsLinear = view.findViewById(R.id.active_linear)
        activeDownloadsNoResults = view.findViewById(R.id.active_no_results)
        activeDownloads = ActiveDownloadAdapter(this,requireActivity())
        activeRecyclerView = view.findViewById(R.id.download_recyclerview)
        activeRecyclerView.forceFastScrollMode()
        activeRecyclerView.adapter = activeDownloads
        activeRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        queuedDownloadsLinear = view.findViewById(R.id.queue_linear)
        val itemTouchHelper = ItemTouchHelper(queuedDragDropHelper)
        queuedDownloads = QueuedDownloadAdapter(this, requireActivity(), itemTouchHelper)
        queuedRecyclerView = view.findViewById(R.id.queued_recyclerview)
        queuedRecyclerView.forceFastScrollMode()
        queuedRecyclerView.adapter = queuedDownloads
        queuedRecyclerView.enableFastScroll()
        itemTouchHelper.attachToRecyclerView(queuedRecyclerView)
        queuedFilesizeCount = view.findViewById(R.id.filesize)
        queuedDragHandleToggle = view.findViewById(R.id.drag)
        inQueueLabel = view.findViewById(R.id.in_queue_label)

        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("queued")){
            val swipeItemTouchHelper = ItemTouchHelper(queuedSwipeCallback)
            swipeItemTouchHelper.attachToRecyclerView(queuedRecyclerView)
        }
        queuedRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        activeQueuedLinear = view.findViewById(R.id.active_queued_linear)

        pause = view.findViewById(R.id.pause)
        pause.isVisible = false
        resume = view.findViewById(R.id.resume)
        noResults = view.findViewById(R.id.no_results)

        pause.setOnClickListener {
            lifecycleScope.launch {
                workManager.cancelAllWorkByTag("download")
                val activeDownloadsList = withContext(Dispatchers.IO){
                    downloadViewModel.getActiveDownloads()
                }

                activeDownloadsList.forEach {
                    YoutubeDL.getInstance().destroyProcessById(it.id.toString())
                    notificationUtil.cancelDownloadNotification(it.id.toInt())
                }
                preferences.edit().putBoolean("paused_downloads", true).apply()
                pause.isEnabled = false
                activeDownloads.notifyDataSetChanged()
                withContext(Dispatchers.Main){
                    delay(1000)
                    pause.isVisible = false
                    pause.isEnabled = true
                    resume.isVisible = true
                }

            }
        }

        resume.setOnClickListener {
            lifecycleScope.launch {
                preferences.edit().putBoolean("paused_downloads", false).apply()
                resume.isEnabled = false
                withContext(Dispatchers.IO) {
                    downloadViewModel.resetActiveToQueued()
                    downloadViewModel.startDownloadWorker(listOf())
                    withContext(Dispatchers.Main){
                        activeDownloads.notifyDataSetChanged()
                    }
                }
                withContext(Dispatchers.Main){
                    delay(1000)
                    resume.isVisible = false
                    resume.isEnabled = true
                    pause.isVisible = true
                }
            }
        }

        lifecycleScope.launch {
            val activeQueuedDownloadsCount = withContext(Dispatchers.IO) {
                downloadViewModel.getActiveDownloadsCount()
            }
            val pausedDownloads = preferences.getBoolean("paused_downloads", false)
            pause.isVisible = activeQueuedDownloadsCount > 0 && !pausedDownloads
            resume.isVisible = activeQueuedDownloadsCount > 0 && pausedDownloads
        }

        lifecycleScope.launch {
            downloadViewModel.activeQueuedDownloadsCount.collectLatest {
                activeQueuedLinear.isVisible = it > 0
                noResults.isVisible = it == 0
                if (it == 0) {
                    pause.isVisible = false
                    resume.isVisible = false
                }
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloads.collectLatest {
                activeDownloadsNoResults.isVisible = it.isEmpty()
                activeDownloads.submitList(it)
            }
        }

        lifecycleScope.launch {
            downloadViewModel.queuedDownloads.collectLatest {
                queuedDownloads.submitData(it)
            }
        }

        queuedDownloads.addLoadStateListener { loadState ->
            lifecycleScope.launch {
                if (loadState.append.endOfPaginationReached )
                {
                    if (queuedDownloads.itemCount < 1){
                        queuedFilesizeCount.visibility = View.GONE
                    }else{
                        val size = withContext(Dispatchers.IO){
                            FileUtil.convertFileSize(downloadViewModel.getQueuedCollectedFileSize())
                        }
                        if (size == "?")  queuedFilesizeCount.visibility = View.GONE
                        else {
                            queuedFilesizeCount.visibility = View.VISIBLE
                            queuedFilesizeCount.text = "${getString(R.string.file_size)}: ~ $size"
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            downloadViewModel.getTotalSize(listOf(DownloadRepository.Status.Queued)).observe(viewLifecycleOwner){
                queuedDownloadsLinear.isVisible = it > 0
                queuedTotalSize = it
                queuedDragHandleToggle.isVisible = it > 1
                if (it > 0) {
                    val badgeDrawable = BadgeDrawable.create(requireContext())
                    badgeDrawable.number = it
                    badgeDrawable.isVisible = true
                    badgeDrawable.verticalOffset = 25
                    requireView().post {
                        BadgeUtils.attachBadgeDrawable(badgeDrawable, inQueueLabel)
                    }
                }
            }
        }

        queuedDragHandleToggle.setOnClickListener {
            queuedDownloads.toggleShowDragHandle()
        }
    }

    //dont remove
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadProgressEvent(event: DownloadWorker.WorkerProgress) {
        val progressBar = requireView().findViewWithTag<LinearProgressIndicator>("${event.downloadItemID}##progress")
        val outputText = requireView().findViewWithTag<TextView>("${event.downloadItemID}##output")

        requireActivity().runOnUiThread {
            try {
                progressBar?.setProgressCompat(event.progress, true)
                outputText?.text = event.output
            }catch (ignored: Exception) {}
        }
    }

    fun hidePauseBtn(){
        pause.isVisible = false
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onCancelClick(itemID: Long) {
        lifecycleScope.launch {
            YoutubeDL.getInstance().destroyProcessById(itemID.toString())
            notificationUtil.cancelDownloadNotification(itemID.toInt())

            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }
            item.status = DownloadRepository.Status.Cancelled.toString()
            withContext(Dispatchers.IO){
                downloadViewModel.updateDownload(item)
            }

            val activeCount = withContext(Dispatchers.IO){
                downloadViewModel.getActiveDownloadsCount()
            }

            if (activeCount == 0){
                val queuedCount = withContext(Dispatchers.IO){
                    downloadViewModel.getQueuedDownloadsCount()
                }
                if (queuedCount == 0) {
                    preferences.edit().putBoolean("paused_downloads", false).apply()
                }
            }

            if (activeCount == 1){
                val queue = withContext(Dispatchers.IO){
                    downloadViewModel.getQueued()
                }

                if (!preferences.getBoolean("paused_downloads", false)){
                    runBlocking {
                        downloadViewModel.queueDownloads(queue)
                    }
                }
            }

        }
    }

    override fun onOutputClick(item: DownloadItem) {
        if (item.logID != null && item.logID != 0L) {
            val bundle = Bundle()
            bundle.putLong("logID", item.logID!!)
            findNavController().navigate(
                R.id.downloadLogFragment,
                bundle
            )
        }
    }

    //QUEUED ITEMS FUNCTIONS
    private val queuedContextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.queued_menu_context, menu)
            mode.title = "${queuedDownloads.getSelectedObjectsCount(queuedTotalSize)} ${getString(R.string.selected)}"
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
                            downloadViewModel.getIDsBetweenTwoItems(selectedIDs.first(), selectedIDs.last(), listOf(
                                DownloadRepository.Status.Queued).toListString())
                        }.toMutableList()
                        idsInMiddle.addAll(selectedIDs)
                        if (idsInMiddle.isNotEmpty()){
                            queuedDownloads.checkMultipleItems(idsInMiddle)
                            queuedActionMode?.title = "${idsInMiddle.count()} ${getString(R.string.selected)}"
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
                            queuedDownloads.clearCheckedItems()
                            for (id in selectedObjects){
                                YoutubeDL.getInstance().destroyProcessById(id.toInt().toString())
                                WorkManager.getInstance(requireContext()).cancelAllWorkByTag(id.toInt().toString())
                                notificationUtil.cancelDownloadNotification(id.toInt())
                            }
                            downloadViewModel.deleteAllWithID(selectedObjects)
                            queuedActionMode?.finish()
                        }

                    }
                    deleteDialog.show()
                    true
                }
                R.id.select_all -> {
                    queuedDownloads.checkAll()
                    mode?.title = "(${queuedDownloads.getSelectedObjectsCount(queuedTotalSize)}) ${resources.getString(R.string.all_items_selected)}"
                    true
                }
                R.id.invert_selected -> {
                    queuedDownloads.invertSelected()
                    val selectedObjects = queuedDownloads.getSelectedObjectsCount(queuedTotalSize)
                    queuedActionMode!!.title = "$selectedObjects ${getString(R.string.selected)}"
                    if (selectedObjects == 0) queuedActionMode?.finish()
                    true
                }
                R.id.up -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        queuedDownloads.clearCheckedItems()
                        withContext(Dispatchers.IO){
                            downloadViewModel.putAtTopOfQueue(selectedObjects)
                        }
                        queuedActionMode?.finish()
                    }
                    true
                }
                R.id.down -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        queuedDownloads.clearCheckedItems()
                        withContext(Dispatchers.IO){
                            downloadViewModel.putAtBottomOfQueue(selectedObjects)
                        }
                        queuedActionMode?.finish()
                    }
                    true
                }
                R.id.copy_urls -> {
                    lifecycleScope.launch {
                        val selectedObjects = getSelectedIDs()
                        val urls = withContext(Dispatchers.IO){
                            downloadViewModel.getURLsByIds(selectedObjects)
                        }
                        UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                        queuedActionMode?.finish()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            queuedActionMode = null
            queuedDownloads.clearCheckedItems()
        }

        suspend fun getSelectedIDs() : List<Long>{
            return if (queuedDownloads.inverted || queuedDownloads.checkedItems.isEmpty()){
                withContext(Dispatchers.IO){
                    downloadViewModel.getItemIDsNotPresentIn(queuedDownloads.checkedItems.toList(), listOf(
                        DownloadRepository.Status.Queued))
                }
            }else{
                queuedDownloads.checkedItems.toList()
            }
        }
    }

    var movedToNewPositionID = 0L
    private val queuedDragDropHelper: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                movedToNewPositionID = target.itemView.tag.toString().toLong()
                queuedDownloads.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)
                if (ItemTouchHelper.ACTION_STATE_DRAG == actionState) {
                    /**
                     * Change alpha, scale and elevation on drag.
                     */
                    (viewHolder?.itemView as? MaterialCardView)?.also {
                        AnimatorSet().apply {
                            this.duration = 100L
                            this.interpolator = AccelerateDecelerateInterpolator()

                            playTogether(
                                UiUtil.getAlphaAnimator(it, 0.7f),
                                UiUtil.getScaleXAnimator(it, 1.02f),
                                UiUtil.getScaleYAnimator(it, 1.02f),
                                UiUtil.getElevationAnimator(it, R.dimen.elevation_6dp)
                            )
                        }.start()
                    }
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                /**
                 * Clear alpha, scale and elevation after drag/swipe
                 */
                (viewHolder.itemView as? MaterialCardView)?.also {
                    AnimatorSet().apply {
                        this.duration = 100L
                        this.interpolator = AccelerateDecelerateInterpolator()

                        playTogether(
                            UiUtil.getAlphaAnimator(it, 1f),
                            UiUtil.getScaleXAnimator(it, 1f),
                            UiUtil.getScaleYAnimator(it, 1f),
                            UiUtil.getElevationAnimator(it, R.dimen.elevation_2dp)
                        )
                    }.start()
                }

                downloadViewModel.putAtPosition(viewHolder.itemView.tag.toString().toLong(), movedToNewPositionID)
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
        }


    private var queuedSwipeCallback: ItemTouchHelper.SimpleCallback =
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
                            queuedDownloads.notifyItemChanged(viewHolder.bindingAdapterPosition)
                            removeQueuedItem(deletedItem.id)
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

    override fun onQueuedCancelClick(itemID: Long) {
        lifecycleScope.launch {
            YoutubeDL.getInstance().destroyProcessById(itemID.toInt().toString())
            notificationUtil.cancelDownloadNotification(itemID.toInt())

            withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }.let {
                it.status = DownloadRepository.Status.Cancelled.toString()
                withContext(Dispatchers.IO){
                    downloadViewModel.updateDownload(it)
                }
            }
        }
    }

    override fun onMoveQueuedItemToTop(itemID: Long) {
        lifecycleScope.launch {
            downloadViewModel.putAtTopOfQueue(listOf(itemID))
        }
    }

    override fun onMoveQueuedItemToBottom(itemID: Long) {
        lifecycleScope.launch {
            downloadViewModel.putAtBottomOfQueue(listOf(itemID))
        }
    }

    override fun onQueuedCardClick(itemID: Long) {
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
                    removeQueuedItem(it.id)
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
                    lifecycleScope.launch {
                        it.status = DownloadRepository.Status.Saved.toString()
                        withContext(Dispatchers.IO){
                            downloadViewModel.updateToStatus(it.id, DownloadRepository.Status.Saved)
                        }
                        findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                            Pair("downloadItem", it),
                            Pair("result", downloadViewModel.createResultItemFromDownload(it)),
                            Pair("type", it.type)
                        )
                        )
                    }
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


    override fun onQueuedCardSelect(isChecked: Boolean, position: Int) {
        lifecycleScope.launch {
            val selectedObjects = queuedDownloads.getSelectedObjectsCount(queuedTotalSize)
            if (queuedActionMode == null) queuedActionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(queuedContextualActionBar)
            val now = System.currentTimeMillis()
            queuedActionMode?.apply {
                if (selectedObjects == 0){
                    this.finish()
                }else{
                    this.title = "$selectedObjects ${getString(R.string.selected)}"

                    this.menu.findItem(R.id.up).isVisible = position > 0
                    this.menu.findItem(R.id.down).isVisible = position < queuedTotalSize
                    this.menu.findItem(R.id.select_between).isVisible = false
                    if(selectedObjects == 2){
                        val selectedIDs = queuedContextualActionBar.getSelectedIDs().sortedBy { it }
                        val idsInMiddle = withContext(Dispatchers.IO){
                            downloadViewModel.getIDsBetweenTwoItems(selectedIDs.first(), selectedIDs.last(), listOf(DownloadRepository.Status.Queued).toListString())
                        }
                        this.menu.findItem(R.id.select_between).isVisible = idsInMiddle.isNotEmpty()
                    }
                }
            }
        }
    }

    private fun removeQueuedItem(id: Long){
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

}