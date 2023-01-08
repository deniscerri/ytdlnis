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
import android.view.View.OnLongClickListener
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.DownloadsRecyclerViewAdapter
import com.deniscerri.ytdlnis.database.DatabaseManager
import com.deniscerri.ytdlnis.database.Video
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.service.DownloadInfo
import com.deniscerri.ytdlnis.util.FileUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

/**
 * A fragment representing a list of Items.
 */
class DownloadsFragment : Fragment(), DownloadsRecyclerViewAdapter.OnItemClickListener,
    View.OnClickListener, OnLongClickListener {
    private lateinit var historyViewModel : HistoryViewModel

    private var downloading = false
    private var fragmentView: View? = null
    private var databaseManager: DatabaseManager? = null
    var activity: Activity? = null
    var mainActivity: MainActivity? = null
    var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var topAppBar: MaterialToolbar? = null
    private var recyclerView: RecyclerView? = null
    private var downloadsRecyclerViewAdapter: DownloadsRecyclerViewAdapter? = null
    private var bottomSheet: BottomSheetDialog? = null
    private var sortSheet: BottomSheetDialog? = null
    private var uiHandler: Handler? = null
    private var no_results: RelativeLayout? = null
    private var selectionChips: LinearLayout? = null
    private var websiteGroup: ChipGroup? = null
    private var downloadsObjects: ArrayList<Video?>? = null
    var selectedObjects: ArrayList<Video?>? = null
    private var progressBar: LinearProgressIndicator? = null
    private var deleteFab: ExtendedFloatingActionButton? = null
    private var fileUtil: FileUtil? = null
    private var format = ""
    private var website = ""
    private var sort = "DESC"
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_downloads, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        fragmentContext = context?.applicationContext
        layoutinflater = LayoutInflater.from(context)

        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]


        shimmerCards = this.view?.findViewById(R.id.shimmer_downloads_framelayout)
        topAppBar = this.view?.findViewById(R.id.downloads_toolbar)
        no_results = this.view?.findViewById(R.id.downloads_no_results)
        selectionChips = this.view?.findViewById(R.id.downloads_selection_chips)
        websiteGroup = this.view?.findViewById(R.id.website_chip_group)
        deleteFab = this.view?.findViewById(R.id.delete_selected_fab)
        fileUtil = FileUtil()
        deleteFab?.tag = "deleteSelected"
        deleteFab?.setOnClickListener(this)
        uiHandler = Handler(Looper.getMainLooper())
        downloadsObjects = ArrayList()
        selectedObjects = ArrayList()
        downloading = mainActivity!!.isDownloadServiceRunning()
        recyclerView = this.view?.findViewById(R.id.recycler_view_downloads)
        downloadsRecyclerViewAdapter =
            DownloadsRecyclerViewAdapter(downloadsObjects, this, activity)
        recyclerView?.adapter = downloadsRecyclerViewAdapter
        recyclerView?.isNestedScrollingEnabled = false
        initMenu()
        initChips()
        initCards()
        return fragmentView
    }

    fun initCards() {
        shimmerCards!!.startShimmer()
        shimmerCards!!.visibility = View.VISIBLE
        downloadsRecyclerViewAdapter!!.clear()
        no_results!!.visibility = View.GONE
        selectionChips!!.visibility = View.VISIBLE
        databaseManager = DatabaseManager(context)
        try {
            val thread = Thread {
                if (!downloading) databaseManager!!.clearDownloadingHistory()
                downloadsObjects = databaseManager!!.getHistory("", format, website, sort)
                uiHandler!!.post {
                    downloadsRecyclerViewAdapter!!.add(downloadsObjects)
                    shimmerCards!!.stopShimmer()
                    shimmerCards!!.visibility = View.GONE
                    updateWebsiteChips()
                }
                if ((downloadsObjects as ArrayList<Video>?)?.size == 0) {
                    uiHandler!!.post {
                        no_results!!.visibility = View.VISIBLE
                        selectionChips!!.visibility = View.GONE
                        websiteGroup!!.removeAllViews()
                    }
                }
                databaseManager!!.close()
            }
            thread.start()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
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
        val toolbar = fragmentView!!.findViewById<MaterialToolbar>(R.id.downloads_toolbar)
        topAppBar!!.menu.findItem(R.id.search_downloads)
            .setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar!!.menu.findItem(R.id.search_downloads).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView.queryHint = getString(R.string.search_history_hint)
        databaseManager = DatabaseManager(context)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchQuery = query
                topAppBar!!.menu.findItem(R.id.search_downloads).collapseActionView()
                downloadsObjects = databaseManager!!.getHistory(query, format, website, sort)
                downloadsRecyclerViewAdapter!!.clear()
                downloadsRecyclerViewAdapter!!.add(downloadsObjects)
                if ((downloadsObjects as ArrayList<Video>?)?.size == 0) {
                    no_results!!.visibility = View.VISIBLE
                    selectionChips!!.visibility = View.GONE
                    websiteGroup!!.removeAllViews()
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchQuery = newText
                downloadsObjects = databaseManager!!.getHistory(newText, format, website, sort)
                downloadsRecyclerViewAdapter!!.clear()
                downloadsRecyclerViewAdapter!!.add(downloadsObjects)
                if ((downloadsObjects as ArrayList<Video>?)?.size == 0) {
                    no_results!!.visibility = View.VISIBLE
                    selectionChips!!.visibility = View.GONE
                } else {
                    no_results!!.visibility = View.GONE
                    selectionChips!!.visibility = View.VISIBLE
                }
                return true
            }
        })
        topAppBar!!.setOnClickListener { view: View? -> scrollToTop() }
        topAppBar!!.setOnMenuItemClickListener { m: MenuItem ->
            val itemID = m.itemId
            if (itemID == R.id.remove_downloads) {
                if (downloadsObjects!!.size == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    return@setOnMenuItemClickListener true
                }
                val delete_dialog = MaterialAlertDialogBuilder(fragmentContext!!)
                delete_dialog.setTitle(getString(R.string.confirm_delete_history))
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_desc))
                delete_dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
                delete_dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface: DialogInterface?, i: Int ->
                    databaseManager!!.clearHistory()
                    downloadsRecyclerViewAdapter!!.clear()
                    downloadsObjects!!.clear()
                    no_results!!.visibility = View.VISIBLE
                    selectionChips!!.visibility = View.GONE
                    websiteGroup!!.removeAllViews()
                }
                delete_dialog.show()
            } else if (itemID == R.id.remove_deleted_downloads) {
                if (downloadsObjects!!.size == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    return@setOnMenuItemClickListener true
                }
                val delete_dialog = MaterialAlertDialogBuilder(fragmentContext!!)
                delete_dialog.setTitle(getString(R.string.confirm_delete_history))
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_deleted_desc))
                delete_dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
                delete_dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface: DialogInterface?, i: Int ->
                    databaseManager!!.clearDeletedHistory()
                    initCards()
                }
                delete_dialog.show()
            } else if (itemID == R.id.remove_duplicates) {
                if (downloadsObjects!!.size == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    return@setOnMenuItemClickListener true
                }
                val delete_dialog = MaterialAlertDialogBuilder(fragmentContext!!)
                delete_dialog.setTitle(getString(R.string.confirm_delete_history))
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_duplicates_desc))
                delete_dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
                delete_dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface: DialogInterface?, i: Int ->
                    databaseManager!!.clearDuplicateHistory()
                    initCards()
                }
                delete_dialog.show()
            } else if (itemID == R.id.remove_downloading) {
                if (downloadsObjects!!.size == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    return@setOnMenuItemClickListener true
                }
                val delete_dialog = MaterialAlertDialogBuilder(fragmentContext!!)
                delete_dialog.setTitle(getString(R.string.confirm_delete_history))
                delete_dialog.setMessage(getString(R.string.confirm_delete_downloading_desc))
                delete_dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
                delete_dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface: DialogInterface?, i: Int ->
                    databaseManager!!.clearDownloadingHistory()
                    mainActivity!!.cancelDownloadService()
                    initCards()
                }
                delete_dialog.show()
            }
            true
        }
    }

    private fun initChips() {
        //sort and history/downloading switch
        val sortChip = fragmentView!!.findViewById<Chip>(R.id.sort_chip)
        sortChip.setOnClickListener { view: View? ->
            sortSheet = BottomSheetDialog(fragmentContext!!)
            sortSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
            sortSheet!!.setContentView(R.layout.downloads_sort_sheet)
            val newest = sortSheet!!.findViewById<TextView>(R.id.newest)
            val oldest = sortSheet!!.findViewById<TextView>(R.id.oldest)
            newest!!.setOnClickListener { view1: View? ->
                sort = "DESC"
                downloadsObjects = databaseManager!!.getHistory(searchQuery, format, website, sort)
                downloadsRecyclerViewAdapter!!.clear()
                downloadsRecyclerViewAdapter!!.add(downloadsObjects)
                sortSheet!!.cancel()
            }
            oldest!!.setOnClickListener { view1: View? ->
                sort = "ASC"
                downloadsObjects = databaseManager!!.getHistory(searchQuery, format, website, sort)
                downloadsRecyclerViewAdapter!!.clear()
                downloadsRecyclerViewAdapter!!.add(downloadsObjects)
                sortSheet!!.cancel()
            }
            val cancel = sortSheet!!.findViewById<TextView>(R.id.cancel)
            cancel!!.setOnClickListener { view1: View? -> sortSheet!!.cancel() }
            sortSheet!!.show()
            sortSheet!!.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        //format
        val audio = fragmentView!!.findViewById<Chip>(R.id.audio_chip)
        audio.setOnClickListener { view: View? ->
            if (audio.isChecked) {
                format = "audio"
                if (recyclerView!!.visibility == View.GONE) {
                }
                downloadsObjects = databaseManager!!.getHistory(searchQuery, format, website, sort)
                audio.isChecked = true
            } else {
                format = ""
                downloadsObjects = databaseManager!!.getHistory(searchQuery, format, website, sort)
                audio.isChecked = false
            }
            downloadsRecyclerViewAdapter!!.clear()
            downloadsRecyclerViewAdapter!!.add(downloadsObjects)
        }
        val video = fragmentView!!.findViewById<Chip>(R.id.video_chip)
        video.setOnClickListener { view: View? ->
            if (video.isChecked) {
                format = "video"
                downloadsObjects = databaseManager!!.getHistory(searchQuery, format, website, sort)
                video.isChecked = true
            } else {
                format = ""
                downloadsObjects = databaseManager!!.getHistory(searchQuery, format, website, sort)
                video.isChecked = false
            }
            downloadsRecyclerViewAdapter!!.clear()
            downloadsRecyclerViewAdapter!!.add(downloadsObjects)
        }
    }

    private fun updateWebsiteChips() {
        websiteGroup!!.removeAllViews()
        val websites = downloadsRecyclerViewAdapter!!.websites
        for (i in websites.indices) {
            val w = websites[i]
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, websiteGroup, false) as Chip
            tmp.text = w
            tmp.id = i
            tmp.setOnClickListener { view: View ->
                if (tmp.isChecked) {
                    website = tmp.text as String
                    downloadsObjects =
                        databaseManager!!.getHistory(searchQuery, format, website, sort)
                    websiteGroup!!.check(view.id)
                } else {
                    website = ""
                    downloadsObjects =
                        databaseManager!!.getHistory(searchQuery, format, website, sort)
                    websiteGroup!!.clearCheck()
                }
                downloadsRecyclerViewAdapter!!.clear()
                downloadsRecyclerViewAdapter!!.add(downloadsObjects)
            }
            websiteGroup!!.addView(tmp)
        }
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.bottomsheet_remove_button) {
            removedownloadsItem(v.tag as Int)
        } else if (id == R.id.bottom_sheet_link) {
            openLinkIntent(v.tag as Int)
        } else if (id == R.id.bottomsheet_open_file_button) {
            openFileIntent(v.tag as Int)
        } else if (id == R.id.delete_selected_fab) {
            removeSelectedItems()
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
        val delete_file = booleanArrayOf(false)
        databaseManager = DatabaseManager(context)
        val delete_dialog = MaterialAlertDialogBuilder(fragmentContext!!)
        delete_dialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
        delete_dialog.setMultiChoiceItems(
            arrayOf(getString(R.string.delete_files_too)),
            booleanArrayOf(false)
        ) { dialogInterface: DialogInterface?, i: Int, b: Boolean -> delete_file[0] = b }
        delete_dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
        delete_dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface: DialogInterface?, i: Int ->
            for (j in selectedObjects!!.indices) {
                val v = selectedObjects!![j]
                val position = downloadsObjects!!.indexOf(v)
                downloadsObjects!!.remove(v)
                downloadsRecyclerViewAdapter!!.remove(position)
                databaseManager!!.clearHistoryItem(v, delete_file[0])
            }
            updateWebsiteChips()
            databaseManager!!.close()
            selectedObjects = ArrayList()
            downloadsRecyclerViewAdapter!!.clearCheckedVideos()
            deleteFab!!.visibility = View.GONE
            if (downloadsObjects!!.size == 0) {
                uiHandler!!.post {
                    no_results!!.visibility = View.VISIBLE
                    selectionChips!!.visibility = View.GONE
                    websiteGroup!!.removeAllViews()
                }
            }
        }
        delete_dialog.show()
    }

    private fun removedownloadsItem(position: Int) {
        if (bottomSheet != null) bottomSheet!!.hide()
        val delete_file = booleanArrayOf(false)
        databaseManager = DatabaseManager(context)
        val v = downloadsObjects!![position]
        val delete_dialog = MaterialAlertDialogBuilder(fragmentContext!!)
        delete_dialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + v!!.title + "\"!")
        delete_dialog.setMultiChoiceItems(
            arrayOf(getString(R.string.delete_file_too)),
            booleanArrayOf(false)
        ) { dialogInterface: DialogInterface?, i: Int, b: Boolean -> delete_file[0] = b }
        delete_dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
        delete_dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface: DialogInterface?, i: Int ->
            downloadsObjects!!.removeAt(position)
            downloadsRecyclerViewAdapter!!.remove(position)
            updateWebsiteChips()
            databaseManager!!.clearHistoryItem(v, delete_file[0])
            databaseManager!!.close()
            if (downloadsObjects!!.size == 0) {
                uiHandler!!.post {
                    no_results!!.visibility = View.VISIBLE
                    selectionChips!!.visibility = View.GONE
                    websiteGroup!!.removeAllViews()
                }
            }
        }
        delete_dialog.show()
    }

    private fun copyLinkToClipBoard(position: Int) {
        val url = downloadsObjects!![position]!!.getURL()
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.url), url)
        clipboard.setPrimaryClip(clip)
        if (bottomSheet != null) bottomSheet!!.hide()
        Toast.makeText(context, getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }

    private fun openLinkIntent(position: Int) {
        val url = downloadsObjects!![position]!!.getURL()
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        if (bottomSheet != null) bottomSheet!!.hide()
        startActivity(i)
    }

    private fun openFileIntent(position: Int) {
        val downloadPath = downloadsObjects!![position]!!.downloadPath
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

    override fun onCardClick(position: Int, isFilePresent: Boolean) {
        bottomSheet = BottomSheetDialog(fragmentContext!!)
        bottomSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet!!.setContentView(R.layout.downloads_bottom_sheet)
        val video = downloadsObjects!![position]
        val title = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = video!!.title
        val author = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = video.author
        val link = bottomSheet!!.findViewById<Button>(R.id.bottom_sheet_link)
        val url = video.getURL()
        link!!.text = url
        link.tag = position
        link.setOnClickListener(this)
        link.setOnLongClickListener(this)
        val remove = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = position
        remove.setOnClickListener(this)
        val openFile = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.tag = position
        openFile.setOnClickListener(this)
        if (!isFilePresent) openFile.visibility = View.GONE
        bottomSheet!!.show()
        bottomSheet!!.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCardSelect(position: Int, add: Boolean) {
        val video = downloadsObjects!![position]
        if (add) selectedObjects!!.add(video) else selectedObjects!!.remove(video)
        if (selectedObjects!!.size > 1) {
            deleteFab!!.visibility = View.VISIBLE
        } else {
            deleteFab!!.visibility = View.GONE
        }
    }

    override fun onButtonClick(position: Int) {
        val vid = downloadsObjects!![position]
        try {
            mainActivity!!.removeItemFromDownloadQueue(vid, vid!!.downloadedType)
        } catch (e: Exception) {
            val info = DownloadInfo()
            info.video = vid
            info.downloadType = vid!!.downloadedType
        }
    }

    fun findVideo(url: String, type: String): Video? {
        for (i in downloadsObjects!!.indices) {
            val v = downloadsObjects!![i]
            if (v!!.getURL() == url && v.downloadedType == type && v.isQueuedDownload) {
                return v
            }
        }
        return null
    }

    companion object {
        private const val TAG = "downloadsFragment"
        fun newInstance(): DownloadsFragment {
            val fragment = DownloadsFragment()
            val args = Bundle()
            return fragment
        }
    }
}