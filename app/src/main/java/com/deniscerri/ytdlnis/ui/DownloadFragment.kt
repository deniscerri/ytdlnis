package com.deniscerri.ytdlnis.ui

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.View.*
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdlnis.adapter.QueuedDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHistoryBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File

/**
 * A fragment representing a list of Items.
 */
class DownloadFragment : Fragment(), ActiveDownloadAdapter.OnItemClickListener, QueuedDownloadAdapter.OnItemClickListener,
    OnClickListener, OnLongClickListener {
    private lateinit var downloadViewModel : DownloadViewModel

    private var downloading = false
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var topAppBar: MaterialToolbar? = null
    private var activeRecyclerView: RecyclerView? = null
    private var othersRecyclerView: RecyclerView? = null
    private var downloadsAdapter: ActiveDownloadAdapter? = null
    private var queuedDownloadsAdapter: QueuedDownloadAdapter? = null
    private var bottomSheet: BottomSheetDialog? = null
    private var sortSheet: BottomSheetDialog? = null
    private var uiHandler: Handler? = null
    private var noResults: RelativeLayout? = null
    private var websiteGroup: ChipGroup? = null
    private var downloadsList: List<DownloadItem?>? = null
    private var allDownloadsList: List<DownloadItem?>? = null
    private var selectedObjects: ArrayList<DownloadItem>? = null
    private var deleteFab: ExtendedFloatingActionButton? = null
    private var fileUtil: FileUtil? = null

    private var _binding : FragmentHistoryBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_downloads, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        topAppBar = view.findViewById(R.id.downloads_toolbar)
        noResults = view.findViewById(R.id.no_results)
        websiteGroup = view.findViewById(R.id.website_chip_group)
        deleteFab = view.findViewById(R.id.delete_selected_fab)
        fileUtil = FileUtil()
        deleteFab?.tag = "deleteSelected"
        deleteFab?.setOnClickListener(this)
        uiHandler = Handler(Looper.getMainLooper())
        selectedObjects = ArrayList()
        downloading = mainActivity!!.isDownloadServiceRunning()


        downloadsList = mutableListOf()
        allDownloadsList = mutableListOf()

        downloadsAdapter =
            ActiveDownloadAdapter(
                this,
                requireActivity()
            )
        activeRecyclerView = view.findViewById(R.id.recyclerviewactivedownloads)
        activeRecyclerView?.layoutManager = LinearLayoutManager(context)
        activeRecyclerView?.adapter = downloadsAdapter

        queuedDownloadsAdapter =
            QueuedDownloadAdapter(
                this,
                requireActivity()
            )
        othersRecyclerView = view.findViewById(R.id.recyclerviewotherdownloads)
        othersRecyclerView?.layoutManager = LinearLayoutManager(context)
        othersRecyclerView?.adapter = queuedDownloadsAdapter

        noResults?.visibility = GONE
        shimmerCards?.visibility = GONE


        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        downloadViewModel.activeDownloads.observe(viewLifecycleOwner){
            // update active
        }
        downloadViewModel.queuedDownloads.observe(viewLifecycleOwner){
            // update queued
        }
    }

    fun scrollToTop() {
        activeRecyclerView!!.scrollToPosition(0)
        Handler(Looper.getMainLooper()).post {
            (topAppBar!!.parent as AppBarLayout).setExpanded(
                true,
                true
            )
        }
    }

    override fun onLongClick(v: View): Boolean {
        val id = v.id
        if (id == R.id.bottom_sheet_link) {
            copyLinkToClipBoard(v.tag as Long)
            return true
        }
        return false
    }

    private fun removeSelectedItems() {
//        if (bottomSheet != null) bottomSheet!!.hide()
//        val deleteFile = booleanArrayOf(false)
//        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
//        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
//        deleteDialog.setMultiChoiceItems(
//            arrayOf(getString(R.string.delete_files_too)),
//            booleanArrayOf(false)
//        ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
//        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
//        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
//            for (item in selectedObjects!!){
//                historyViewModel.delete(item, deleteFile[0])
//            }
//            selectedObjects = ArrayList()
//            historyAdapter!!.clearCheckeditems()
//            deleteFab!!.visibility = GONE
//        }
//        deleteDialog.show()
    }

    private fun removeItem(id: Long) {
//        if (bottomSheet != null) bottomSheet!!.hide()
//        val deleteFile = booleanArrayOf(false)
//        val v = findItem(id)
//        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
//        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + v!!.title + "\"!")
//        deleteDialog.setMultiChoiceItems(
//            arrayOf(getString(R.string.delete_file_too)),
//            booleanArrayOf(false)
//        ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
//        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
//        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
//              historyViewModel.delete(v, deleteFile[0])
//        }
//        deleteDialog.show()
    }

    private fun copyLinkToClipBoard(id: Long) {
        val url = findItem(id)?.url
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.url), url)
        clipboard.setPrimaryClip(clip)
        if (bottomSheet != null) bottomSheet!!.hide()
        Toast.makeText(context, getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }

    private fun openLinkIntent(id: Long) {
        val url = findItem(id)?.url
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        if (bottomSheet != null) bottomSheet!!.hide()
        startActivity(i)
    }

    private fun openFileIntent(id: Long) {
        val downloadPath = findItem(id)!!.downloadPath
        val file = File(downloadPath)
        val uri = FileProvider.getUriForFile(
            fragmentContext!!,
            fragmentContext!!.packageName + ".fileprovider",
            file
        )
        val mime = mainActivity!!.contentResolver.getType(uri)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, mime)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(i)
    }

    override fun onCardClick(videoID: Long, isPresent: Boolean) {
        bottomSheet = BottomSheetDialog(fragmentContext!!)
        bottomSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet!!.setContentView(R.layout.history_item_details_bottom_sheet)
        val video = findItem(videoID)
        val title = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = video!!.title
        val author = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = video.author
        val link = bottomSheet!!.findViewById<Button>(R.id.bottom_sheet_link)
        val url = video.url
        link!!.text = url
        link.tag = videoID
        link.setOnClickListener(this)
        link.setOnLongClickListener(this)
        val remove = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = videoID
        remove.setOnClickListener(this)
        val openFile = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.tag = videoID
        openFile.setOnClickListener(this)
        if (!isPresent) openFile.visibility = GONE
        bottomSheet!!.show()
        bottomSheet!!.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCardSelect(videoID: Long, isChecked: Boolean) {
        val item = findItem(videoID)
        if (isChecked) selectedObjects!!.add(item!!)
        else selectedObjects!!.remove(item)
        if (selectedObjects!!.size > 1) {
            deleteFab!!.visibility = VISIBLE
        } else {
            deleteFab!!.visibility = GONE
        }
    }

    override fun onButtonClick(position: Int) {
//        val vid = downloadsObjects!![position]
//        try {
//            //mainActivity!!.removeItemFromDownloadQueue(vid, vid!!.downloadedType)
//        } catch (e: Exception) {
//            val info = DownloadInfo()
//            info.video = vid
//            info.downloadType = vid!!.downloadedType
//        }
    }

    private fun findItem(id : Long): DownloadItem? {
        return downloadsList?.find { it?.id == id }
    }

    companion object {
        private const val TAG = "downloadsFragment"
    }

    override fun onClick(p0: View?) {
        TODO("Not yet implemented")
    }
}