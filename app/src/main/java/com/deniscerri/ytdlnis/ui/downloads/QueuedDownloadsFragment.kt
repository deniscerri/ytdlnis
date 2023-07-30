package com.deniscerri.ytdlnis.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.UiUtil.forceFastScrollMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID


class QueuedDownloadsFragment : Fragment(), GenericDownloadAdapter.OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var queuedRecyclerView : RecyclerView
    private lateinit var queuedDownloads : GenericDownloadAdapter
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var fileSize: TextView
    private var selectedObjects: ArrayList<DownloadItem>? = null
    private var actionMode : ActionMode? = null
    private lateinit var items : MutableList<DownloadItem>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_inqueue, container, false)
        activity = getActivity()
        notificationUtil = NotificationUtil(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        items = mutableListOf()
        selectedObjects = arrayListOf()
        return fragmentView
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fileSize = view.findViewById(R.id.filesize)
        queuedDownloads =
            GenericDownloadAdapter(
                this,
                requireActivity()
            )

        queuedRecyclerView = view.findViewById(R.id.download_recyclerview)
        queuedRecyclerView.forceFastScrollMode()
        queuedRecyclerView.adapter = queuedDownloads
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getBoolean("swipe_gestures", true)){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(queuedRecyclerView)
        }
        queuedRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        downloadViewModel.queuedDownloads.observe(viewLifecycleOwner) {
            items = it.toMutableList()
            if (it.isEmpty()) fileSize.visibility = View.GONE
            else{
                val size = FileUtil.convertFileSize(it.sumOf { i -> i.format.filesize })
                if (size == "?")  fileSize.visibility = View.GONE
                else {
                    fileSize.visibility = View.VISIBLE
                    fileSize.text = "${getString(R.string.file_size)}: ~ $size"
                }
             }
            queuedDownloads.submitList(it)
        }
    }

    override fun onActionButtonClick(itemID: Long) {
        removeItem(itemID)
    }

    override fun onCardClick(itemID: Long) {
        val item = items.find { it.id == itemID } ?: return

        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)
        val title = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = item.title.ifEmpty { "`${requireContext().getString(R.string.defaultValue)}`" }
        val author = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = item.author.ifEmpty { "`${requireContext().getString(R.string.defaultValue)}`" }

        // BUTTON ----------------------------------
        val btn = bottomSheet.findViewById<MaterialButton>(R.id.downloads_download_button_type)

        when (item.type) {
            DownloadViewModel.Type.audio -> {
                btn!!.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_music)
            }
            DownloadViewModel.Type.video -> {
                btn!!.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_video)
            }
            else -> {
                btn!!.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_terminal)
            }
        }

        val time = bottomSheet.findViewById<Chip>(R.id.time)
        val formatNote = bottomSheet.findViewById<Chip>(R.id.format_note)
        val container = bottomSheet.findViewById<Chip>(R.id.container_chip)
        val codec = bottomSheet.findViewById<Chip>(R.id.codec)
        val fileSize = bottomSheet.findViewById<Chip>(R.id.file_size)

        if (item.downloadStartTime <= System.currentTimeMillis() / 1000) time!!.visibility = View.GONE
        else {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = item.downloadStartTime
            time!!.text = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(calendar.time)

            time.setOnClickListener {
                UiUtil.showDatePicker(parentFragmentManager) {
                    bottomSheet.dismiss()
                    Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + it.time, Toast.LENGTH_LONG).show()
                    downloadViewModel.deleteDownload(item)
                    item.downloadStartTime = it.timeInMillis
                    WorkManager.getInstance(requireContext()).cancelAllWorkByTag(item.id.toString())
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(item))
                    }
                }
            }
        }

        if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
            View.GONE
        else formatNote!!.text = item.format.format_note

        if (item.format.container != "") container!!.text = item.format.container.uppercase()
        else container!!.visibility = View.GONE

        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none"){
            codec!!.visibility = View.GONE
        }else{
            codec!!.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSizeReadable = FileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize!!.visibility = View.GONE
        else fileSize!!.text = fileSizeReadable

        val link = bottomSheet.findViewById<Button>(R.id.bottom_sheet_link)
        val url = item.url
        link!!.text = url
        link.tag = itemID
        link.setOnClickListener{
            UiUtil.openLinkIntent(requireContext(), item.url, bottomSheet)
        }
        link.setOnLongClickListener{
            UiUtil.copyLinkToClipBoard(requireContext(), item.url, bottomSheet)
            true
        }
        val remove = bottomSheet.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = itemID
        remove.setOnClickListener{
            bottomSheet.hide()
            removeItem(itemID)
        }
        val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.visibility = View.GONE



        val downloadNow = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
        if (item.downloadStartTime <= System.currentTimeMillis() / 1000) downloadNow!!.visibility = View.GONE
        else{
            downloadNow!!.text = getString(R.string.download_now)
            downloadNow.setOnClickListener {
                bottomSheet.dismiss()
                downloadViewModel.deleteDownload(item)
                item.downloadStartTime = 0
                WorkManager.getInstance(requireContext()).cancelAllWorkByTag(item.id.toString())
                runBlocking {
                    downloadViewModel.queueDownloads(listOf(item))
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

    override fun onCardSelect(itemID: Long, isChecked: Boolean) {
        val item = items.find { it.id == itemID }
        val now = System.currentTimeMillis()
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
        if (actionMode != null){
            actionMode!!.menu.getItem(1).isVisible = selectedObjects!!.all { it.downloadStartTime > now }
        }
    }

    private fun removeItem(id: Long){
        val item = items.find { it.id == id } ?: return
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
                        downloadViewModel.deleteDownload(item)
                        downloadViewModel.queueDownloads(listOf(item))
                    }
                }.show()
        }
        deleteDialog.show()
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.queued_menu_context, menu)
            mode.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
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
                        for (obj in selectedObjects!!){
                            val id = obj.id.toInt()
                            YoutubeDL.getInstance().destroyProcessById(id.toString())
                            WorkManager.getInstance(requireContext()).cancelAllWorkByTag(id.toString())
                            notificationUtil.cancelDownloadNotification(id)
                            downloadViewModel.deleteDownload(obj)
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.download -> {
                    for (obj in selectedObjects!!){
                        WorkManager.getInstance(requireContext()).cancelAllWorkByTag(obj.id.toInt().toString())
                    }
                    selectedObjects!!.forEach { it.downloadStartTime = 0L }
                    lifecycleScope.launch(Dispatchers.IO) {
                        downloadViewModel.queueDownloads(selectedObjects!!)
                    }
                    true
                }
                R.id.select_all -> {
                    queuedDownloads.checkAll(items)
                    selectedObjects?.clear()
                    items.forEach { selectedObjects?.add(it) }
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    queuedDownloads.invertSelected(items)
                    val invertedList = arrayListOf<DownloadItem>()
                    items.forEach {
                        if (!selectedObjects?.contains(it)!!) invertedList.add(it)
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
            clearCheckedItems()
        }
    }

    private fun clearCheckedItems(){
        queuedDownloads.clearCheckeditems()
        selectedObjects?.clear()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = items[position]
                        queuedDownloads.notifyItemChanged(position)
                        removeItem(deletedItem.id)
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
                    viewHolder!!,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }
}