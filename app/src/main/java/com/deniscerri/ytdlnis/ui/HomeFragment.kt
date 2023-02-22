package com.deniscerri.ytdlnis.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.HomeAdapter
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadBottomSheetDialog
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadMultipleBottomSheetDialog
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.util.*

class HomeFragment : Fragment(), HomeAdapter.OnItemClickListener, View.OnClickListener {
    private var inputQuery: String? = null
    private var inputQueries: LinkedList<String?>? = null
    private var inputQueriesLength = 0
    private var homeAdapter: HomeAdapter? = null
    private var searchSuggestions: ScrollView? = null
    private var searchSuggestionsLinearLayout: LinearLayout? = null
    private var downloadFabs: CoordinatorLayout? = null
    private var downloadAllFabCoordinator: CoordinatorLayout? = null
    private var homeFabs: CoordinatorLayout? = null
    private var infoUtil: InfoUtil? = null
    private var downloadQueue: ArrayList<ResultItem>? = null

    private lateinit var resultViewModel : ResultViewModel
    private lateinit var downloadViewModel : DownloadViewModel

    private var downloading = false
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var topAppBar: MaterialToolbar? = null
    private var recyclerView: RecyclerView? = null
    private var uiHandler: Handler? = null
    private var resultsList: List<ResultItem?>? = null
    private var selectedObjects: ArrayList<ResultItem>? = null
    private var fileUtil: FileUtil? = null
    private var firstBoot = true
    private var sharedPreferences: SharedPreferences? = null
    private var _binding : FragmentHomeBinding? = null

    private var workManager: WorkManager? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        topAppBar = view.findViewById(R.id.downloads_toolbar)
        fileUtil = FileUtil()
        uiHandler = Handler(Looper.getMainLooper())
        selectedObjects = ArrayList()

        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        downloadQueue = ArrayList()
        resultsList = mutableListOf()
        selectedObjects = ArrayList()

        sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)

        //initViews
        shimmerCards = view.findViewById(R.id.shimmer_results_framelayout)
        topAppBar = view.findViewById(R.id.home_toolbar)
        searchSuggestions = view.findViewById(R.id.search_suggestions_scroll_view)
        searchSuggestionsLinearLayout = view.findViewById(R.id.search_suggestions_linear_layout)

        homeAdapter =
            HomeAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerViewHome)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = homeAdapter

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        resultViewModel.items.observe(viewLifecycleOwner) {
            homeAdapter!!.submitList(it)
            resultsList = it
            if(it.size > 1){
                if (it[0].playlistTitle.isNotEmpty() && it[0].playlistTitle != getString(R.string.trendingPlaylist)){
                    downloadAllFabCoordinator!!.visibility = VISIBLE
                }else{
                    downloadAllFabCoordinator!!.visibility = GONE
                }
            }else if (it.size == 1){
                if (sharedPreferences!!.getBoolean("download_card", true)){
                    if(it.size == 1 && !firstBoot && parentFragmentManager.findFragmentByTag("downloadSingleSheet") == null){
                        showSingleDownloadSheet(it[0], DownloadViewModel.Type.video)
                    }
                }
            }else{
                downloadAllFabCoordinator!!.visibility = GONE
            }
            firstBoot = false
        }

        resultViewModel.loadingItems.observe(viewLifecycleOwner){
            if (it){
                recyclerView?.setPadding(0,0,0,0)
                shimmerCards!!.startShimmer()
                shimmerCards!!.visibility = VISIBLE
            }else{
                recyclerView?.setPadding(0,0,0,100)
                shimmerCards!!.stopShimmer()
                shimmerCards!!.visibility = GONE
            }
        }

        initMenu()

        homeFabs = view.findViewById(R.id.home_fabs)
        downloadFabs = homeFabs!!.findViewById(R.id.download_selected_coordinator)
        downloadAllFabCoordinator = homeFabs!!.findViewById(R.id.download_all_coordinator)
        val downloadSelectedFab = downloadFabs!!.findViewById<ExtendedFloatingActionButton>(R.id.download_selected_fab)
        downloadSelectedFab.tag = "downloadSelected"
        downloadSelectedFab.setOnClickListener(this)
        val downloadAllFab =
            downloadAllFabCoordinator!!.findViewById<ExtendedFloatingActionButton>(R.id.download_all_fab)
        downloadAllFab.tag = "downloadAll"
        downloadAllFab.setOnClickListener(this)

        if (inputQueries != null) {
            resultViewModel.deleteAll()
            inputQueriesLength = inputQueries!!.size
            Handler(Looper.getMainLooper()).post { scrollToTop() }
            val thread = Thread {
                resultViewModel.parseQuery(inputQueries!!.pop()!!, true)
                while (!inputQueries!!.isEmpty()) {
                    inputQuery = inputQueries!!.pop()
                    resultViewModel.parseQuery(inputQuery!!, false)
                }
                try {
                    Handler(Looper.getMainLooper()).post {

                        // DOWNLOAD ALL BUTTON
                        if (resultsList!!.size > 1 || inputQueriesLength > 1) {
                            downloadAllFabCoordinator!!.visibility = VISIBLE
                        }
//                        databaseManager = DatabaseManager(context)
//                        databaseManager!!.clearResults()
//                        for (v in resultsList!!) v!!.isPlaylistItem = 1
//                        databaseManager!!.addToResults(resultsList)
                    }
                } catch (ignored: Exception) {
                }
            }
            thread.start()
        } else {
            resultViewModel.checkTrending()
        }

        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData("download")
            .observe(viewLifecycleOwner){ list ->
                list.forEach {
                    //Toast.makeText(context, """${it.progress.getInt("progress", 0)} ${it.progress.getString("output")}""", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun initMenu() {
        val onActionExpandListener: MenuItem.OnActionExpandListener =
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    homeFabs!!.visibility = GONE
                    recyclerView!!.visibility = GONE
                    searchSuggestions!!.visibility = VISIBLE
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    homeFabs!!.visibility = VISIBLE
                    recyclerView!!.visibility = VISIBLE
                    searchSuggestions!!.visibility = GONE
                    return true
                }
            }
        if (downloading) {
            topAppBar!!.menu.findItem(R.id.cancel_download).isVisible = true
        }
        topAppBar!!.menu.findItem(R.id.search).setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar!!.menu.findItem(R.id.search).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_TEXT_VARIATION_URI
        searchView.queryHint = getString(R.string.search_hint)
        infoUtil = InfoUtil(requireContext())
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar!!.menu.findItem(R.id.search).collapseActionView()
                downloadAllFabCoordinator!!.visibility = GONE
                downloadFabs!!.visibility = GONE
                selectedObjects = ArrayList()
                inputQuery = query.trim { it <= ' ' }
                resultViewModel.deleteAll()
                val thread = Thread {
                    resultViewModel.parseQuery(inputQuery!!, true)
                }
                thread.start()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchSuggestionsLinearLayout!!.removeAllViews()
                if (newText.isEmpty()) return false
                val thread = Thread {
                    val suggestions = infoUtil!!.getSearchSuggestions(newText)
                    for (i in suggestions.indices) {
                        val v = LayoutInflater.from(fragmentContext)
                            .inflate(R.layout.search_suggestion_item, null)
                        val textView = v.findViewById<TextView>(R.id.suggestion_text)
                        textView.text = suggestions[i]
                        Handler(Looper.getMainLooper()).post {
                            searchSuggestionsLinearLayout!!.addView(
                                v
                            )
                        }
                        textView.setOnClickListener { onQueryTextSubmit(textView.text.toString()) }
                        val mb = v.findViewById<MaterialButton>(R.id.set_search_query_button)
                        mb.setOnClickListener {
                            searchView.setQuery(
                                textView.text,
                                false
                            )
                        }
                    }
                }
                thread.start()
                return false
            }
        })
        topAppBar!!.setOnClickListener { scrollToTop() }
        topAppBar!!.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.delete_results) {
                resultViewModel.getTrending()
                selectedObjects = ArrayList()
                downloadAllFabCoordinator!!.visibility = GONE
                downloadFabs!!.visibility = GONE
            } else if (itemId == R.id.cancel_download) {
                try {
                    mainActivity!!.cancelAllDownloads()
                    topAppBar!!.menu.findItem(itemId).isVisible = false
//                    for (i in downloadInfo!!.downloadQueue.indices) {
//                        val vid = downloadInfo!!.downloadQueue[i]
//                        val type = vid.downloadedType
//                        updateDownloadingStatusOnResult(vid, type, false)
//                    }
//                    downloadQueue = ArrayList()
//                    downloading = false
                } catch (ignored: Exception) {
                }
            }
            true
        }
    }

    fun handleIntent(intent: Intent) {
        inputQueries = LinkedList()
        inputQueries!!.add(intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    fun handleFileIntent(lines: LinkedList<String?>?) {
        inputQueries = lines
    }

    fun scrollToTop() {
        recyclerView!!.scrollToPosition(0)
        (topAppBar!!.parent as AppBarLayout).setExpanded(true, true)
    }

    private fun findVideo(url: String): ResultItem? {
        for (i in resultsList!!.indices) {
            val v = resultsList!![i]
            if (v!!.url == url) {
                return v
            }
        }
        return null
    }

    @SuppressLint("ResourceType")
    override fun onButtonClick(videoURL: String, type: DownloadViewModel.Type?) {
        Log.e(TAG, type.toString() + " " + videoURL)
        val item = resultsList!!.find { it?.url == videoURL }
        Log.e(TAG, resultsList!![0].toString() + " " + videoURL)
        val btn = recyclerView!!.findViewWithTag<MaterialButton>("""${item?.url}##$type""")
//        if (downloading) {
//            try {
//                if (btn.getTag(R.id.cancelDownload) == "true") {
//                    mainActivity!!.removeItemFromDownloadQueue(vid, type)
//                    return
//                }
//            } catch (ignored: Exception) {
//            }
//        }
        if (sharedPreferences!!.getBoolean("download_card", true)) {
//            selectedObjects!!.clear()
//            selectedObjects!!.add(item!!)
            //showConfigureSingleDownloadCard(createDownloadItem(item!!, type), item)
            showSingleDownloadSheet(item!!, type!!)
        } else {
            val downloadItem = downloadViewModel.createDownloadItemFromResult(item!!, type!!)
            downloadViewModel.queueDownloads(listOf(downloadItem))
//            downloadQueue!!.add(vid)
//            updateDownloadingStatusOnResult(vid, type, true)
//            if (isStoragePermissionGranted) {
//                mainActivity!!.startDownloadService(downloadQueue, listener)
//                downloadQueue!!.clear()
//            }
        }
    }

    private fun showSingleDownloadSheet(resultItem : ResultItem, type: DownloadViewModel.Type){
        val bottomSheet = DownloadBottomSheetDialog(resultItem, type)
        bottomSheet.show(parentFragmentManager, "downloadSingleSheet")
    }


    fun updateDownloadStatusOnResult(v: ResultItem?, type: String?, downloaded: Boolean) {
//        if (v != null) {
//            databaseManager = DatabaseManager(context)
//            try {
//                databaseManager!!.updateDownloadStatusOnResult(v.videoId, type, downloaded)
//                databaseManager!!.close()
//            } catch (ignored: Exception) {
//            }
//        }
    }

    fun updateDownloadingStatusOnResult(v: ResultItem?, type: String, isDownloading: Boolean) {
//        if (v != null) {
//            if (type == "audio") v.isDownloadingAudio =
//                isDownloading else if (type == "video") v.isDownloadingVideo = isDownloading
//            homeAdapter!!.updateVideoListItem(v, resultsList!!.indexOf(v))
//            databaseManager = DatabaseManager(context)
//            try {
//                databaseManager!!.updateDownloadingStatusOnResult(v.videoId, type, isDownloading)
//                databaseManager!!.close()
//            } catch (ignored: Exception) {
//            }
//        }
    }

    override fun onCardClick(videoURL: String, add: Boolean) {
        val item = resultsList?.find { it -> it?.url == videoURL }
        if (add) selectedObjects!!.add(item!!) else selectedObjects!!.remove(item)
        if (selectedObjects!!.size > 1) {
            downloadAllFabCoordinator!!.visibility = GONE
            downloadFabs!!.visibility = VISIBLE
        } else {
            downloadFabs!!.visibility = GONE
            if (resultsList!![1]!!.playlistTitle.isNotEmpty() && resultsList!![1]!!.playlistTitle != getString(R.string.trendingPlaylist)){
                downloadAllFabCoordinator!!.visibility = VISIBLE
            }else{
                downloadAllFabCoordinator!!.visibility = GONE
            }
        }
    }

    override fun onClick(v: View) {
        val viewIdName: String = try {
            v.tag.toString()
        } catch (e: Exception) {""}
        if (viewIdName.isNotEmpty()) {
            if (viewIdName == "downloadSelected") {
                val downloadList = downloadViewModel.turnResultItemsToDownloadItems(selectedObjects!!)
                downloadViewModel.putDownloadsForProcessing(selectedObjects!!, downloadList).observe(viewLifecycleOwner) {
                    it.forEachIndexed { i, itemID ->
                        downloadList[i].id = itemID
                    }
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        val bottomSheet = DownloadMultipleBottomSheetDialog(downloadList)
                        bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                    } else {
                        downloadViewModel.queueDownloads(downloadList)
                    }
                }
            }
            if (viewIdName == "downloadAll") {
                val downloadList = downloadViewModel.turnResultItemsToDownloadItems(resultsList!!)
                downloadViewModel.putDownloadsForProcessing(resultsList!!, downloadList).observe(viewLifecycleOwner) {
                    it.forEachIndexed { i, itemID ->
                        downloadList[i].id = itemID
                    }
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        val bottomSheet = DownloadMultipleBottomSheetDialog(downloadList)
                        bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                    } else {
                        downloadViewModel.queueDownloads(downloadList)
                    }
                }
            }
        }
    }

    private fun initDownloadAll(
        bottomSheet: BottomSheetDialog,
        start: Int,
        end: Int,
        type: String
    ) {
//        var start = start
//        var end = end
//        if (start > end) {
//            val first = bottomSheet.findViewById<TextInputLayout>(R.id.first_textinput)
//            first!!.error = getString(R.string.first_cant_be_larger_than_last)
//            val last = bottomSheet.findViewById<TextInputLayout>(R.id.last_textinput)
//            last!!.error = getString(R.string.last_cant_be_smaller_than_first)
//            return
//        }
//        bottomSheet.cancel()
//        start--
//        end--
//        if (start <= 1) start = 0
//        val sharedPreferences =
//            requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
//        if (sharedPreferences.getBoolean("download_card", true)) {
//            selectedObjects!!.clear()
//            selectedObjects!!.addAll(resultsList!!.subList(start, end + 1) as ArrayList<ResultItem>)
//            showConfigureDownloadCard(type)
//        } else {
//            for (i in start..end) {
//                val vid = findVideo(
//                    resultsList!![i]!!.getURL()
//                )
//                vid!!.downloadedType = type
//                updateDownloadingStatusOnResult(vid, type, true)
//                downloadQueue!!.add(vid)
//            }
//            if (isStoragePermissionGranted) {
//                mainActivity!!.startDownloadService(downloadQueue, listener)
//                downloadQueue!!.clear()
//            }
//        }
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}