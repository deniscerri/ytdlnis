package com.deniscerri.ytdlnis.ui

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.HistoryAdapter
import com.deniscerri.ytdlnis.database.DatabaseManager
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.HistoryRepository.HistorySort
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.databinding.FragmentDownloadsBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File

/**
 * A fragment representing a list of Items.
 */
class DownloadsFragment : Fragment(), HistoryAdapter.OnItemClickListener,
    OnClickListener, OnLongClickListener {
    private lateinit var historyViewModel : HistoryViewModel

    private var downloading = false
    private var fragmentView: View? = null
    private var databaseManager: DatabaseManager? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var topAppBar: MaterialToolbar? = null
    private var recyclerView: RecyclerView? = null
    private var historyAdapter: HistoryAdapter? = null
    private var bottomSheet: BottomSheetDialog? = null
    private var sortSheet: BottomSheetDialog? = null
    private var uiHandler: Handler? = null
    private var noResults: RelativeLayout? = null
    private var selectionChips: LinearLayout? = null
    private var websiteGroup: ChipGroup? = null
    private var downloadsList: List<HistoryItem?>? = null
    private var allDownloadsList: List<HistoryItem?>? = null
    private var selectedObjects: ArrayList<HistoryItem>? = null
    private var deleteFab: ExtendedFloatingActionButton? = null
    private var fileUtil: FileUtil? = null

    private var _binding : FragmentDownloadsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_downloads, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        shimmerCards = view.findViewById(R.id.shimmer_downloads_framelayout)
        topAppBar = view.findViewById(R.id.downloads_toolbar)
        noResults = view.findViewById(R.id.downloads_no_results)
        selectionChips = view.findViewById(R.id.downloads_selection_chips)
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

        historyAdapter =
            HistoryAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerviewdownloadss)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = historyAdapter

        noResults?.visibility = GONE
        selectionChips?.visibility = VISIBLE
        shimmerCards?.visibility = GONE


        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        historyViewModel.allItems.observe(viewLifecycleOwner) {
            allDownloadsList = it
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
            downloadsList = it
        }

        initMenu()
        initChips()
    }

    fun scrollToTop() {
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
        topAppBar!!.menu.findItem(R.id.search_downloads)
            .setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar!!.menu.findItem(R.id.search_downloads).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView.queryHint = getString(R.string.search_history_hint)
        databaseManager = DatabaseManager(context)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar!!.menu.findItem(R.id.search_downloads).collapseActionView()
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
                R.id.remove_downloads -> {
                    if(allDownloadsList!!.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                        deleteDialog.setMessage(getString(R.string.confirm_delete_history_desc))
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.deleteAll()
                        }
                        deleteDialog.show()
                    }
                }
                R.id.remove_deleted_downloads -> {
                    if(allDownloadsList!!.isEmpty()){
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
                    if(allDownloadsList!!.isEmpty()){
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
                R.id.remove_downloading -> {

                }
            }
            true
        }
    }


    private fun initChips() {
        val sortChip = fragmentView!!.findViewById<Chip>(R.id.sortChip)
        sortChip.setOnClickListener {
            sortSheet = BottomSheetDialog(requireContext())
            sortSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
            sortSheet!!.setContentView(R.layout.downloads_sort_sheet)
            val newest = sortSheet!!.findViewById<TextView>(R.id.newest)
            val oldest = sortSheet!!.findViewById<TextView>(R.id.oldest)
            newest!!.setOnClickListener {
                historyViewModel.setSorting(HistorySort.DESC)
                sortSheet!!.cancel()
            }
            oldest!!.setOnClickListener {
                historyViewModel.setSorting(HistorySort.ASC)
                sortSheet!!.cancel()
            }
            val cancel = sortSheet!!.findViewById<TextView>(R.id.cancel)
            cancel!!.setOnClickListener { sortSheet!!.cancel() }
            sortSheet!!.show()
            sortSheet!!.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
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
    }

    private fun updateWebsiteChips(list : List<HistoryItem>) {
        val websites = mutableListOf<String>()
        for (item in list){
            if (!websites.contains(item.website)) websites.add(item.website)
        }
        websiteGroup!!.removeAllViews()
        //val websites = downloadsRecyclerViewAdapter!!.websites
        for (i in websites.indices) {
            val w = websites[i]
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, websiteGroup, false) as Chip
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
            websiteGroup!!.addView(tmp)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.bottomsheet_remove_button -> {
                removeItem(v.tag as Int)
            }
            R.id.bottom_sheet_link -> {
                openLinkIntent(v.tag as Int)
            }
            R.id.bottomsheet_open_file_button -> {
                openFileIntent(v.tag as Int)
            }
            R.id.delete_selected_fab -> {
                removeSelectedItems()
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        val id = v.id
        if (id == R.id.bottom_sheet_link) {
            copyLinkToClipBoard(v.tag as Int)
            return true
        }
        return false
    }

    private fun removeSelectedItems() {
        if (bottomSheet != null) bottomSheet!!.hide()
        val deleteFile = booleanArrayOf(false)
        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
        deleteDialog.setMultiChoiceItems(
            arrayOf(getString(R.string.delete_files_too)),
            booleanArrayOf(false)
        ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            for (item in selectedObjects!!){
                historyViewModel.delete(item, deleteFile[0])
            }
            selectedObjects = ArrayList()
            historyAdapter!!.clearCheckedVideos()
            deleteFab!!.visibility = GONE
        }
        deleteDialog.show()
    }

    private fun removeItem(id: Int) {
        if (bottomSheet != null) bottomSheet!!.hide()
        val deleteFile = booleanArrayOf(false)
        val v = findItem(id)
        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + v!!.title + "\"!")
        deleteDialog.setMultiChoiceItems(
            arrayOf(getString(R.string.delete_file_too)),
            booleanArrayOf(false)
        ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
              historyViewModel.delete(v, deleteFile[0])
        }
        deleteDialog.show()
    }

    private fun copyLinkToClipBoard(id: Int) {
        val url = findItem(id)?.url
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.url), url)
        clipboard.setPrimaryClip(clip)
        if (bottomSheet != null) bottomSheet!!.hide()
        Toast.makeText(context, getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }

    private fun openLinkIntent(id: Int) {
        val url = findItem(id)?.url
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        if (bottomSheet != null) bottomSheet!!.hide()
        startActivity(i)
    }

    private fun openFileIntent(id: Int) {
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

    override fun onCardClick(id: Int, isFilePresent: Boolean) {
        bottomSheet = BottomSheetDialog(fragmentContext!!)
        bottomSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet!!.setContentView(R.layout.downloads_bottom_sheet)
        val video = findItem(id)
        val title = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = video!!.title
        val author = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = video.author
        val link = bottomSheet!!.findViewById<Button>(R.id.bottom_sheet_link)
        val url = video.url
        link!!.text = url
        link.tag = id
        link.setOnClickListener(this)
        link.setOnLongClickListener(this)
        val remove = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = id
        remove.setOnClickListener(this)
        val openFile = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.tag = id
        openFile.setOnClickListener(this)
        if (!isFilePresent) openFile.visibility = GONE
        bottomSheet!!.show()
        bottomSheet!!.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCardSelect(id: Int, add: Boolean) {
        val item = findItem(id)
        if (add) selectedObjects!!.add(item!!)
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

    private fun findItem(id : Int): HistoryItem? {
        return downloadsList?.find { it?.id == id }
    }

    companion object {
        private const val TAG = "downloadsFragment"
    }
}