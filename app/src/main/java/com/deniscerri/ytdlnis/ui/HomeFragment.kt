package com.deniscerri.ytdlnis.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.HomeAdapter
import com.deniscerri.ytdlnis.database.DatabaseManager
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentDownloadsBinding
import com.deniscerri.ytdlnis.service.DownloadInfo
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class HomeFragment : Fragment(), HomeAdapter.OnItemClickListener, View.OnClickListener {
    private var progressBar: LinearProgressIndicator? = null
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
    private var downloadInfo: DownloadInfo? = null

    private lateinit var resultViewModel : ResultViewModel

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
    private var bottomSheet: BottomSheetDialog? = null
    private var sortSheet: BottomSheetDialog? = null
    private var uiHandler: Handler? = null
    private var noResults: RelativeLayout? = null
    private var selectionChips: LinearLayout? = null
    private var websiteGroup: ChipGroup? = null
    private var resultsList: List<ResultItem?>? = null
    private var selectedObjects: ArrayList<ResultItem>? = null
    private var deleteFab: ExtendedFloatingActionButton? = null
    private var fileUtil: FileUtil? = null

    private var _binding : FragmentDownloadsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false)
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


        downloadQueue = ArrayList()
        resultsList = mutableListOf()
        selectedObjects = ArrayList()

        //initViews
        shimmerCards = view.findViewById(R.id.shimmer_results_framelayout)
        topAppBar = view.findViewById(R.id.home_toolbar)
        searchSuggestions = view.findViewById(R.id.search_suggestions_scroll_view)
        searchSuggestionsLinearLayout = view.findViewById(R.id.search_suggestions_linear_layout)

        homeAdapter =
            HomeAdapter(
                this,
                activity
            )
        recyclerView = view.findViewById(R.id.recyclerViewHome)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = homeAdapter

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        resultViewModel.items.observe(viewLifecycleOwner) {
            homeAdapter!!.submitList(it)
            resultsList = it
            shimmerCards!!.stopShimmer()
            shimmerCards!!.visibility = GONE
        }

        initMenu()

        homeFabs = view.findViewById(R.id.home_fabs)
        downloadFabs = homeFabs!!.findViewById(R.id.download_selected_coordinator)
        downloadAllFabCoordinator = homeFabs!!.findViewById(R.id.download_all_coordinator)
        val musicFab = downloadFabs!!.findViewById<FloatingActionButton>(R.id.audio_fab)
        val videoFab = downloadFabs!!.findViewById<FloatingActionButton>(R.id.video_fab)
        musicFab.tag = "SELECT##audio"
        videoFab.tag = "SELECT##video"
        musicFab.setOnClickListener(this)
        videoFab.setOnClickListener(this)
        val downloadAllFab =
            downloadAllFabCoordinator!!.findViewById<ExtendedFloatingActionButton>(R.id.download_all_fab)
        downloadAllFab.tag = "downloadAll"
        downloadAllFab.setOnClickListener(this)

        if (inputQueries != null) {
            resultViewModel.deleteAll()
            inputQueriesLength = inputQueries!!.size
            shimmerCards!!.startShimmer()
            shimmerCards!!.visibility = VISIBLE
            val thread = Thread {
                while (!inputQueries!!.isEmpty()) {
                    inputQuery = inputQueries!!.pop()
                    parseQuery(false)
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
            if (resultViewModel.checkTrending()){
                shimmerCards!!.startShimmer()
                shimmerCards!!.visibility = VISIBLE
                resultViewModel.getTrending()
            }
        }
    }

//
//    private fun initCards() {
//        val uiHandler = Handler(Looper.getMainLooper())
//        try {
//            val thread = Thread {
//                databaseManager = DatabaseManager(context)
//                resultsList = databaseManager!!.results
//                var playlistTitle = ""
//                try {
//                    playlistTitle = resultsList.get(0).playlistTitle
//                } catch (ignored: Exception) {
//                }
//                if (resultsList.size == 0 || playlistTitle == getString(R.string.trendingPlaylist) && !downloading) {
//                    try {
//                        databaseManager!!.clearResults()
//                        uiHandler.post {
//                            shimmerCards!!.startShimmer()
//                            shimmerCards!!.visibility = View.VISIBLE
//                        }
//                        infoUtil = InfoUtil(context!!)
//                        resultsList = infoUtil!!.getTrending(context!!)
//                        databaseManager!!.addToResults(resultsList)
//                    } catch (e: Exception) {
//                        Log.e(TAG, e.toString())
//                    }
//                } else {
//                    if (!downloading) {
//                        homeAdapter!!.add(resultsList)
//                        for (i in resultsList.indices) {
//                            val tmp = resultsList.get(i)
//                            if (tmp!!.isDownloading) {
//                                updateDownloadingStatusOnResult(tmp, "audio", false)
//                                updateDownloadingStatusOnResult(tmp, "video", false)
//                            }
//                        }
//                    }
//                }
//                uiHandler.post {
//                    if (homeAdapter!!.itemCount != resultsList.size) {
//                        homeAdapter!!.add(resultsList)
//                    }
//                    shimmerCards!!.stopShimmer()
//                    shimmerCards!!.visibility = View.GONE
//                }
//                databaseManager!!.close()
//                if (resultsList != null) {
//                    uiHandler.post { scrollToTop() }
//                    if (resultsList!!.size > 1 && resultsList!![1]!!.isPlaylistItem == 1) {
//                        uiHandler.post { downloadAllFabCoordinator!!.visibility = View.VISIBLE }
//                    }
//                }
//            }
//            thread.start()
//        } catch (e: Exception) {
//            Log.e(TAG, e.toString())
//            uiHandler.post {
//                shimmerCards!!.stopShimmer()
//                shimmerCards!!.visibility = View.GONE
//            }
//        }
//    }

    private fun initMenu() {
        val onActionExpandListener: MenuItem.OnActionExpandListener =
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    homeFabs!!.visibility = View.GONE
                    recyclerView!!.visibility = View.GONE
                    searchSuggestions!!.visibility = View.VISIBLE
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    homeFabs!!.visibility = View.VISIBLE
                    recyclerView!!.visibility = View.VISIBLE
                    searchSuggestions!!.visibility = View.GONE
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
        databaseManager = DatabaseManager(context)
        infoUtil = InfoUtil(requireContext())
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar!!.menu.findItem(R.id.search).collapseActionView()
                downloadAllFabCoordinator!!.visibility = GONE
                downloadFabs!!.visibility = View.GONE
                selectedObjects = ArrayList()
                inputQuery = query.trim { it <= ' ' }
                shimmerCards!!.startShimmer()
                shimmerCards!!.visibility = View.VISIBLE
                resultViewModel.deleteAll()
                val thread = Thread {
                    parseQuery(true)
                    // DOWNLOAD ALL BUTTON
                    if (resultsList!!.size > 1 && resultsList!![1]!!.playlistTitle.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            downloadAllFabCoordinator!!.visibility = View.VISIBLE
                        }
                    }
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
        topAppBar!!.setOnClickListener { view: View? -> scrollToTop() }
        topAppBar!!.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.delete_results) {
                resultViewModel.getTrending()
                selectedObjects = ArrayList()
                downloadAllFabCoordinator!!.visibility = View.GONE
                downloadFabs!!.visibility = View.GONE
            }
//            } else if (itemId == R.id.cancel_download) {
//                try {
//                    mainActivity!!.cancelDownloadService()
//                    topAppBar!!.menu.findItem(itemId).isVisible = false
//                    for (i in downloadInfo!!.downloadQueue.indices) {
//                        val vid = downloadInfo!!.downloadQueue[i]
//                        val type = vid.downloadedType
//                        updateDownloadingStatusOnResult(vid, type, false)
//                    }
//                    downloadQueue = ArrayList()
//                    downloading = false
//                } catch (ignored: Exception) {
//                }
//            }
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

    private fun parseQuery(resetResults: Boolean) {
        databaseManager = DatabaseManager(context)
        infoUtil = InfoUtil(requireContext())
        Handler(Looper.getMainLooper()).post { scrollToTop() }
        var type = "Search"
        val p = Pattern.compile("^(https?)://(www.)?youtu(.be)?")
        val m = p.matcher(inputQuery!!)
        if (m.find()) {
            type = "Video"
            if (inputQuery!!.contains("playlist?list=")) {
                type = "Playlist"
            }
        } else if (inputQuery!!.contains("http")) {
            type = "Default"
        }
        Log.e(TAG, "$inputQuery $type")
        try {
            when (type) {
                "Search" -> {
                    try {
                        val res = infoUtil!!.search(
                            inputQuery!!
                        )
                        Handler(Looper.getMainLooper()).post {
                            if (resetResults) resultViewModel.deleteAll()
                            resultViewModel.insert(res)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        Handler(Looper.getMainLooper()).post {
                            shimmerCards!!.stopShimmer()
                            shimmerCards!!.visibility = View.GONE
                        }
                    }
                }
                "Video" -> {
                    var el: Array<String?> =
                        inputQuery!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    inputQuery = el[el.size - 1]
                    if (inputQuery!!.contains("watch?v=")) {
                        inputQuery = inputQuery!!.substring(8)
                    }
                    el = inputQuery!!.split("&".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    inputQuery = el[0]
                    el = inputQuery!!.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    inputQuery = el[0]
                    try {
                        val v = infoUtil!!.getVideo(inputQuery!!)
                        val res = ArrayList<ResultItem?>()
                        res.add(v)
                        Handler(Looper.getMainLooper()).post {
                            if (resetResults) resultViewModel.deleteAll()
                            resultViewModel.insert(res)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        Handler(Looper.getMainLooper()).post {
                            shimmerCards!!.stopShimmer()
                            shimmerCards!!.visibility = View.GONE
                        }
                    }
                }
                "Playlist" -> {
                    inputQuery =
                        inputQuery!!.split("list=".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[1]
                    var nextPageToken = ""
                    if (resetResults) resultViewModel.deleteAll()
                    do {
                        val tmp = infoUtil!!.getPlaylist(inputQuery!!, nextPageToken)
                        val tmp_vids = tmp.videos
                        val tmp_token = tmp.nextPageToken
                        Handler(Looper.getMainLooper()).post {
                            resultViewModel.insert(tmp_vids)
                        }
                        if (tmp_token.isEmpty()) break
                        if (tmp_token == nextPageToken) break
                        nextPageToken = tmp_token
                    } while (true)
                }
                "Default" -> {
                    try {
                        val video = infoUtil!!.getFromYTDL(
                            inputQuery!!
                        )
                        Handler(Looper.getMainLooper()).post {
                            if (resetResults) resultViewModel.deleteAll()
                            resultViewModel.insert(video)
                        }
                        Handler(Looper.getMainLooper()).post {
                            shimmerCards!!.stopShimmer()
                            shimmerCards!!.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        databaseManager!!.close()
    }

    fun findVideo(url: String): ResultItem? {
        for (i in resultsList!!.indices) {
            val v = resultsList!![i]
            if (v!!.url == url) {
                return v
            }
        }
        return null
    }

    @SuppressLint("ResourceType")
    override fun onButtonClick(position: Int, type: String) {
//        Log.e(TAG, type)
//        val vid = resultsList!![position]
//        vid!!.downloadedType = type
//        val btn = recyclerView!!.findViewWithTag<MaterialButton>(vid.videoId + "##" + type)
//        if (downloading) {
//            try {
//                if (btn.getTag(R.id.cancelDownload) == "true") {
//                    mainActivity!!.removeItemFromDownloadQueue(vid, type)
//                    return
//                }
//            } catch (ignored: Exception) {
//            }
//        }
//        val sharedPreferences =
//            context!!.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
//        if (sharedPreferences.getBoolean("download_card", true)) {
//            selectedObjects!!.clear()
//            selectedObjects!!.add(resultsList!![position])
//            showConfigureDownloadCard(type)
//        } else {
//            downloadQueue!!.add(vid)
//            updateDownloadingStatusOnResult(vid, type, true)
//            if (isStoragePermissionGranted) {
//                mainActivity!!.startDownloadService(downloadQueue, listener)
//                downloadQueue!!.clear()
//            }
//        }
    }

    private val isStoragePermissionGranted: Boolean
        get() = if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            downloadQueue = ArrayList()
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
            false
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

    override fun onCardClick(position: Int, add: Boolean) {
        val item = resultsList!![position]
        if (add) selectedObjects!!.add(item!!) else selectedObjects!!.remove(item)
        if (selectedObjects!!.size > 1) {
            downloadAllFabCoordinator!!.visibility = View.GONE
            downloadFabs!!.visibility = View.VISIBLE
        } else {
            downloadFabs!!.visibility = View.GONE
            if (resultsList!!.size > 1 && resultsList!![1]!!.playlistTitle.isNotEmpty()) {
                downloadAllFabCoordinator!!.visibility = View.VISIBLE
            }
        }
    }

    override fun onClick(v: View) {
        val viewIdName: String
        viewIdName = try {
            v.tag.toString()
        } catch (e: Exception) {
            ""
        }
        if (!viewIdName.isEmpty()) {
            if (viewIdName.contains("audio") || viewIdName.contains("video")) {
                val buttonData =
                    viewIdName.split("##".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (buttonData[0] == "SELECT") {
                    initSelectedDownload(buttonData[1])
                }
            }
            if (viewIdName == "downloadAll") {
                //remove previously selected
                for (i in selectedObjects!!.indices) {
                    val vid = findVideo(
                        selectedObjects!![i]!!.url
                    )
                    homeAdapter!!.notifyItemChanged(resultsList!!.indexOf(vid))
                }
                selectedObjects = ArrayList()
                downloadFabs!!.visibility = View.GONE
                bottomSheet = BottomSheetDialog(fragmentContext!!)
                bottomSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
                bottomSheet!!.setContentView(R.layout.home_download_all_bottom_sheet)
                val first = bottomSheet!!.findViewById<TextInputLayout>(R.id.first_textinput)
                first!!.editText!!.setText(1.toString())
                val last = bottomSheet!!.findViewById<TextInputLayout>(R.id.last_textinput)
                last!!.editText!!.setText(resultsList!!.size.toString())
                val audio = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_audio_button)
                audio!!.setOnClickListener { view: View? ->
                    val start = first.editText!!.text.toString().toInt()
                    val end = last.editText!!.text.toString().toInt()
                    initDownloadAll(bottomSheet!!, start, end, "audio")
                }
                val video = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_video_button)
                video!!.setOnClickListener { view: View? ->
                    val start = first.editText!!.text.toString().toInt()
                    val end = last.editText!!.text.toString().toInt()
                    initDownloadAll(bottomSheet!!, start, end, "video")
                }
                bottomSheet!!.show()
                bottomSheet!!.window!!.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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

    private fun initSelectedDownload(type: String) {
//        val sharedPreferences =
//            context!!.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
//        val editor = sharedPreferences.edit()
//        if (sharedPreferences.getBoolean("download_card", true)) {
//            showConfigureDownloadCard(type)
//        } else {
//            for (i in selectedObjects!!.indices) {
//                val vid = findVideo(
//                    selectedObjects!![i]!!.getURL()
//                )
//                vid!!.downloadedType = type
//                updateDownloadingStatusOnResult(vid, type, true)
//                homeAdapter!!.notifyItemChanged(resultsList!!.indexOf(vid))
//                downloadQueue!!.add(vid)
//            }
//            selectedObjects = ArrayList()
//            homeAdapter!!.clearCheckedVideos()
//            downloadFabs!!.visibility = View.GONE
//            if (isStoragePermissionGranted) {
//                mainActivity!!.startDownloadService(downloadQueue, listener)
//                downloadQueue!!.clear()
//            }
//        }
    }

    private fun showConfigureDownloadCard(type: String) {
//        val sharedPreferences =
//            context!!.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
//        val editor = sharedPreferences.edit()
//        try {
//            bottomSheet = BottomSheetDialog(fragmentContext!!)
//            bottomSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
//            if (type == "audio") {
//                bottomSheet!!.setContentView(R.layout.home_download_audio_bottom_sheet)
//                val title = bottomSheet!!.findViewById<TextInputLayout>(R.id.title_textinput)
//                if (selectedObjects!!.size > 1) {
//                    title!!.editText!!.setText(getString(R.string.mutliple_titles))
//                    title.editText!!.isClickable = false
//                    title.editText!!.isLongClickable = false
//                } else {
//                    title!!.editText!!.setText(selectedObjects!![0]!!.title)
//                    title.editText!!.addTextChangedListener(object : TextWatcher {
//                        override fun beforeTextChanged(
//                            charSequence: CharSequence,
//                            i: Int,
//                            i1: Int,
//                            i2: Int
//                        ) {
//                        }
//
//                        override fun onTextChanged(
//                            charSequence: CharSequence,
//                            i: Int,
//                            i1: Int,
//                            i2: Int
//                        ) {
//                        }
//
//                        override fun afterTextChanged(editable: Editable) {
//                            val index = resultsList!!.indexOf(selectedObjects!![0])
//                            resultsList!![index]!!.title = editable.toString()
//                        }
//                    })
//                }
//                val author = bottomSheet!!.findViewById<TextInputLayout>(R.id.author_textinput)
//                if (selectedObjects!!.size > 1) {
//                    author!!.editText!!.setText(getString(R.string.mutliple_authors))
//                    author.editText!!.isClickable = false
//                    author.editText!!.isLongClickable = false
//                } else {
//                    author!!.editText!!.setText(selectedObjects!![0]!!.author)
//                    author.editText!!.addTextChangedListener(object : TextWatcher {
//                        override fun beforeTextChanged(
//                            charSequence: CharSequence,
//                            i: Int,
//                            i1: Int,
//                            i2: Int
//                        ) {
//                        }
//
//                        override fun onTextChanged(
//                            charSequence: CharSequence,
//                            i: Int,
//                            i1: Int,
//                            i2: Int
//                        ) {
//                        }
//
//                        override fun afterTextChanged(editable: Editable) {
//                            val index = resultsList!!.indexOf(selectedObjects!![0])
//                            resultsList!![index]!!.author = editable.toString()
//                        }
//                    })
//                }
//                val audioFormats = context!!.resources.getStringArray(R.array.music_formats)
//                val audioFormat = bottomSheet!!.findViewById<TextInputLayout>(R.id.audio_format)
//                val autoCompleteTextView =
//                    bottomSheet!!.findViewById<AutoCompleteTextView>(R.id.audio_format_textview)
//                val preference = sharedPreferences.getString("audio_format", "mp3")
//                autoCompleteTextView!!.setText(preference, false)
//                (audioFormat!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
//                    AdapterView.OnItemClickListener { adapterView: AdapterView<*>?, view: View?, index: Int, l: Long ->
//                        for (i in selectedObjects!!.indices) {
//                            val vid = findVideo(
//                                selectedObjects!![i]!!.getURL()
//                            )
//                            vid!!.audioFormat = audioFormats[index]
//                        }
//                        editor.putString("audio_format", audioFormats[index])
//                        editor.apply()
//                    }
//            } else {
//                bottomSheet!!.setContentView(R.layout.home_download_video_bottom_sheet)
//                val title = bottomSheet!!.findViewById<TextInputLayout>(R.id.title_textinput)
//                if (selectedObjects!!.size > 1) {
//                    title!!.editText!!.setText(getString(R.string.mutliple_titles))
//                    title.editText!!.isClickable = false
//                    title.editText!!.isLongClickable = false
//                } else {
//                    title!!.editText!!.setText(selectedObjects!![0]!!.title)
//                    title.editText!!.addTextChangedListener(object : TextWatcher {
//                        override fun beforeTextChanged(
//                            charSequence: CharSequence,
//                            i: Int,
//                            i1: Int,
//                            i2: Int
//                        ) {
//                        }
//
//                        override fun onTextChanged(
//                            charSequence: CharSequence,
//                            i: Int,
//                            i1: Int,
//                            i2: Int
//                        ) {
//                        }
//
//                        override fun afterTextChanged(editable: Editable) {
//                            val index = resultsList!!.indexOf(selectedObjects!![0])
//                            resultsList!![index]!!.title = editable.toString()
//                        }
//                    })
//                }
//                val videoFormats = context!!.resources.getStringArray(R.array.video_formats)
//                val videoQualities = context!!.resources.getStringArray(R.array.video_quality)
//                val videoFormat = bottomSheet!!.findViewById<TextInputLayout>(R.id.video_format)
//                var autoCompleteTextView =
//                    bottomSheet!!.findViewById<AutoCompleteTextView>(R.id.video_format_textview)
//                var preference = sharedPreferences.getString("video_format", "webm")
//                autoCompleteTextView!!.setText(preference, false)
//                (videoFormat!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
//                    AdapterView.OnItemClickListener { adapterView: AdapterView<*>?, view: View?, index: Int, l: Long ->
//                        for (i in selectedObjects!!.indices) {
//                            val vid = findVideo(
//                                selectedObjects!![i]!!.getURL()
//                            )
//                            vid!!.videoFormat = videoFormats[index]
//                        }
//                        editor.putString("video_format", videoFormats[index])
//                        editor.apply()
//                    }
//                val videoQuality = bottomSheet!!.findViewById<TextInputLayout>(R.id.video_quality)
//                autoCompleteTextView = bottomSheet!!.findViewById(R.id.video_quality_textview)
//                preference = sharedPreferences.getString("video_quality", "Best Quality")
//                autoCompleteTextView!!.setText(preference, false)
//                (videoQuality!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
//                    AdapterView.OnItemClickListener { adapterView: AdapterView<*>?, view: View?, index: Int, l: Long ->
//                        for (i in selectedObjects!!.indices) {
//                            val vid = findVideo(
//                                selectedObjects!![i]!!.getURL()
//                            )
//                            vid!!.videoQuality = videoQualities[index]
//                        }
//                        editor.putString("video_quality", videoQualities[index])
//                        editor.apply()
//                    }
//                val embedSubs = bottomSheet!!.findViewById<Chip>(R.id.embed_subtitles)
//                embedSubs!!.isChecked = sharedPreferences.getBoolean("embed_subtitles", false)
//                embedSubs.setOnClickListener { view: View? ->
//                    if (embedSubs.isChecked) {
//                        editor.putBoolean("embed_subtitles", true)
//                    } else {
//                        editor.putBoolean("embed_subtitles", false)
//                    }
//                    editor.apply()
//                }
//                val addChapters = bottomSheet!!.findViewById<Chip>(R.id.add_chapters)
//                addChapters!!.isChecked = sharedPreferences.getBoolean("add_chapters", false)
//                addChapters.setOnClickListener { view: View? ->
//                    if (addChapters.isChecked) {
//                        editor.putBoolean("add_chapters", true)
//                    } else {
//                        editor.putBoolean("add_chapters", false)
//                    }
//                    editor.apply()
//                }
//                val saveThumbnail = bottomSheet!!.findViewById<Chip>(R.id.save_thumbnail)
//                saveThumbnail!!.isChecked = sharedPreferences.getBoolean("write_thumbnail", false)
//                saveThumbnail.setOnClickListener { view: View? ->
//                    if (saveThumbnail.isChecked) {
//                        editor.putBoolean("write_thumbnail", true)
//                    } else {
//                        editor.putBoolean("write_thumbnail", false)
//                    }
//                    editor.apply()
//                }
//            }
//            val cancel = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_cancel_button)
//            cancel!!.setOnClickListener { view: View? -> bottomSheet!!.cancel() }
//            val download = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_download_button)
//            download!!.setOnClickListener { view: View? ->
//                for (i in selectedObjects!!.indices) {
//                    val vid = findVideo(
//                        selectedObjects!![i]!!.getURL()
//                    )
//                    vid!!.downloadedType = type
//                    updateDownloadingStatusOnResult(vid, type, true)
//                    homeAdapter!!.notifyItemChanged(resultsList!!.indexOf(vid))
//                    downloadQueue!!.add(vid)
//                }
//                selectedObjects = ArrayList()
//                homeAdapter!!.clearCheckedVideos()
//                downloadFabs!!.visibility = View.GONE
//                if (isStoragePermissionGranted) {
//                    mainActivity!!.startDownloadService(downloadQueue, listener)
//                    downloadQueue!!.clear()
//                }
//                bottomSheet!!.cancel()
//            }
//            bottomSheet!!.show()
//            bottomSheet!!.window!!.setLayout(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )
//        } catch (e: Exception) {
//            Toast.makeText(fragmentContext, e.message, Toast.LENGTH_LONG).show()
//        }
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}