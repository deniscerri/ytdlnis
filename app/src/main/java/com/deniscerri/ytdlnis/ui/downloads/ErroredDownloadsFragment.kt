package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadBottomSheetDialog
import com.deniscerri.ytdlnis.ui.more.downloadLogs.DownloadLogFragment
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.runBlocking


class ErroredDownloadsFragment : Fragment(), GenericDownloadAdapter.OnItemClickListener, OnItemClickListener {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var erroredRecyclerView : RecyclerView
    private lateinit var erroredDownloads : GenericDownloadAdapter
    private lateinit var items : MutableList<DownloadItem>
    private var selectedObjects: ArrayList<DownloadItem>? = null
    private var actionMode : ActionMode? = null
    private lateinit var fileUtil: FileUtil
    private lateinit var uiUtil : UiUtil
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_generic_download_queue, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        items = mutableListOf()
        selectedObjects = arrayListOf()
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        erroredDownloads =
            GenericDownloadAdapter(
                this,
                requireActivity()
            )

        erroredRecyclerView = view.findViewById(R.id.download_recyclerview)
        erroredRecyclerView.adapter = erroredDownloads
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(erroredRecyclerView)
        erroredRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        downloadViewModel.erroredDownloads.observe(viewLifecycleOwner) {
            items = it.toMutableList()
            erroredDownloads.submitList(it)
        }
    }

    override fun onActionButtonClick(itemID: Long) {
        val item = items.find { it.id == itemID } ?: return
        val logFile = fileUtil.getLogFile(requireContext(), item)
        if (logFile.exists()) {
            val bundle = Bundle()
            bundle.putString("logpath", logFile.absolutePath)
            findNavController().navigate(
                R.id.downloadLogFragment,
                bundle
            )
        }
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
        val formatNote = bottomSheet.findViewById<TextView>(R.id.format_note)
        val container = bottomSheet.findViewById<TextView>(R.id.container_chip)
        val codec = bottomSheet.findViewById<TextView>(R.id.codec)
        val fileSize = bottomSheet.findViewById<TextView>(R.id.file_size)

        time!!.visibility = View.GONE

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

        val fileSizeReadable = fileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize!!.visibility = View.GONE
        else fileSize!!.text = fileSizeReadable

        val link = bottomSheet.findViewById<Button>(R.id.bottom_sheet_link)
        val url = item.url
        link!!.text = url
        link.tag = itemID
        link.setOnClickListener{
            uiUtil.openLinkIntent(requireContext(), item.url, bottomSheet)
        }
        link.setOnLongClickListener{
            uiUtil.copyLinkToClipBoard(requireContext(), item.url, bottomSheet)
            true
        }
        val remove = bottomSheet.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = itemID
        remove.setOnClickListener{
            removeItem(item, bottomSheet)
        }
        val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.visibility = View.GONE

        val redownload = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
        redownload!!.tag = itemID
        redownload.setOnClickListener{
            runBlocking{
                downloadViewModel.queueDownloads(listOf(item))
            }
            bottomSheet.cancel()
        }

        redownload.setOnLongClickListener {
            bottomSheet.cancel()
            val sheet = DownloadBottomSheetDialog(downloadViewModel.createResultItemFromDownload(item), item.type, item, false)
            sheet.show(parentFragmentManager, "downloadSingleSheet")
            true
        }

        openFile.visibility = View.GONE

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

    private fun removeItem(item: DownloadItem, bottomSheet: BottomSheetDialog?){
        bottomSheet?.hide()
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            downloadViewModel.deleteDownload(item)
        }
        deleteDialog.show()
    }

    override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        TODO("Not yet implemented")
    }



    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.cancelled_downloads_menu_context, menu)
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
                            downloadViewModel.deleteDownload(obj)
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.redownload -> {
                    runBlocking {
                        downloadViewModel.queueDownloads(selectedObjects!!.toMutableList())
                        actionMode?.finish()
                    }
                    true
                }
                R.id.select_all -> {
                    erroredDownloads.checkAll(items)
                    selectedObjects?.clear()
                    items.forEach { selectedObjects?.add(it) }
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    erroredDownloads.invertSelected(items)
                    val invertedList = arrayListOf<DownloadItem>()
                    items.forEach {
                        if (!selectedObjects?.contains(it)!!) invertedList.add(it)
                    }
                    selectedObjects?.clear()
                    selectedObjects?.addAll(invertedList)
                    actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
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
        erroredDownloads.clearCheckeditems()
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
                    ItemTouchHelper.RIGHT -> {
                        runBlocking{
                            downloadViewModel.queueDownloads(listOf(items[position]))
                        }
                    }
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = items[position]
                        downloadViewModel.deleteDownload(deletedItem)
                        Snackbar.make(erroredRecyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.undo)) {
                                downloadViewModel.insert(deletedItem)
                            }.show()
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

}