package com.deniscerri.ytdlnis.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class HomeFragment : Fragment(), HomeAdapter.OnItemClickListener, View.OnClickListener {
    private var inputQuery: String? = null
    private var inputQueries: LinkedList<String?>? = null
    private var inputQueriesLength = 0
    private var homeAdapter: HomeAdapter? = null

    private var searchSuggestions: ScrollView? = null
    private var searchSuggestionsLinearLayout: LinearLayout? = null
    private var searchHistory: ScrollView? = null
    private var searchHistoryLinearLayout: LinearLayout? = null

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
    private var searchBar: SearchBar? = null
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
        searchBar = view.findViewById(R.id.search_bar)
        searchSuggestions = view.findViewById(R.id.search_suggestions_scroll_view)
        searchSuggestionsLinearLayout = view.findViewById(R.id.search_suggestions_linear_layout)
        searchHistory = view.findViewById(R.id.search_history_scroll_view)
        searchHistoryLinearLayout = view.findViewById(R.id.search_history_linear_layout)

        homeAdapter =
            HomeAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerViewHome)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || resources.getBoolean(R.bool.isTablet)){
            recyclerView?.layoutManager = GridLayoutManager(context, 2)
        }else{
            recyclerView?.layoutManager = LinearLayoutManager(context)
        }
        recyclerView?.adapter = homeAdapter

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        resultViewModel.items.observe(viewLifecycleOwner) {
            homeAdapter!!.submitList(it)
            resultsList = it
            if(resultViewModel.itemCount.value!! > 1 || resultViewModel.itemCount.value!! == -1){
                if (it[0].playlistTitle.isNotEmpty() && it[0].playlistTitle != getString(R.string.trendingPlaylist) && it.size > 1){
                    downloadAllFabCoordinator!!.visibility = VISIBLE
                }else{
                    downloadAllFabCoordinator!!.visibility = GONE
                }
            }else if (resultViewModel.itemCount.value!! == 1){
                if (sharedPreferences!!.getBoolean("download_card", true)){
                    if(it.size == 1 && !firstBoot && parentFragmentManager.findFragmentByTag("downloadSingleSheet") == null){
                        showSingleDownloadSheet(it[0], DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!))
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
            lifecycleScope.launch(){
                withContext(Dispatchers.IO){
                    resultViewModel.parseQuery(inputQueries!!.pop()!!, true)
                }

                while (!inputQueries!!.isEmpty()) {
                    inputQuery = inputQueries!!.pop()
                    withContext(Dispatchers.IO){
                        resultViewModel.parseQuery(inputQuery!!, false)
                    }
                }
                try {
                    if (resultsList!!.size > 1 || inputQueriesLength > 1) {
                        downloadAllFabCoordinator!!.visibility = VISIBLE
                    }
                } catch (ignored: Exception) {
                }
            }
        } else {
            resultViewModel.checkTrending()
        }
    }

    private fun initMenu() {
        val searchView = requireView().findViewById<SearchView>(R.id.search_view)
        infoUtil = InfoUtil(requireContext())
        val linkYouCopied = searchView.findViewById<ConstraintLayout>(R.id.link_you_copied)
        searchView.addTransitionListener { view, previousState, newState ->
            if (newState == SearchView.TransitionState.SHOWN) {
                try{
                    val clipboard =
                        requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val regex =
                        "(https?://(?:www\\.|(?!www))[a-zA-Z\\d][a-zA-Z\\d-]+[a-zA-Z\\d]\\.\\S{2,}|www\\.[a-zA-Z\\d][a-zA-Z\\d-]+[a-zA-Z\\d]\\.\\S{2,}|https?://(?:www\\.|(?!www))[a-zA-Z\\d]+\\.\\S{2,}|www\\.[a-zA-Z\\d]+\\.\\S{2,})".toRegex()
                    val clip = clipboard.primaryClip!!.getItemAt(0).text
                    if (regex.containsMatchIn(clip.toString())) {
                        linkYouCopied.visibility = VISIBLE
                        val textView = linkYouCopied.findViewById<TextView>(R.id.suggestion_text)
                        textView.text = getString(R.string.link_you_copied)
                        textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_language, 0, 0, 0)
                        val mb = linkYouCopied.findViewById<ImageButton>(R.id.set_search_query_button)
                        mb.visibility = INVISIBLE

                        textView.setOnClickListener {
                            searchView.setText(clip.toString())
                            initSearch(searchView)
                        }
                    }else{
                        linkYouCopied.visibility = GONE
                    }
                }catch (e: Exception){
                    e.printStackTrace()
                    linkYouCopied.visibility = GONE
                }
            }
        }

        searchView.editText.addTextChangedListener {
            searchSuggestionsLinearLayout!!.removeAllViews()
            searchHistoryLinearLayout!!.removeAllViews()

            searchSuggestionsLinearLayout!!.visibility = GONE
            searchHistoryLinearLayout!!.visibility = GONE
            linkYouCopied!!.visibility = GONE

            lifecycleScope.launch {
                val suggestions = withContext(Dispatchers.IO){
                    if (it!!.isEmpty()) {
                        resultViewModel.getSearchHistory().map { it.query }
                    }else{
                        infoUtil!!.getSearchSuggestions(it.toString())
                    }
                }

                if (it!!.isEmpty()){
                    for (i in suggestions.indices) {
                        val v = LayoutInflater.from(fragmentContext)
                            .inflate(R.layout.search_suggestion_item, null)
                        val textView = v.findViewById<TextView>(R.id.suggestion_text)
                        textView.text = suggestions[i]
                        textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_restore, 0, 0, 0)
                        Handler(Looper.getMainLooper()).post {
                            searchHistoryLinearLayout!!.addView(
                                v
                            )
                        }
                        textView.setOnClickListener {
                            searchView.setText(textView.text)
                            initSearch(searchView)
                        }
                        textView.setOnLongClickListener {
                            val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                            deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + textView.text + "\"!")
                            deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                            deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                                searchHistoryLinearLayout!!.removeView(v)
                                resultViewModel.removeSearchQueryFromHistory(textView.text.toString())
                            }
                            deleteDialog.show()
                            true
                        }

                        val mb = v.findViewById<ImageButton>(R.id.set_search_query_button)
                        mb.setOnClickListener {
                            searchView.setText(textView.text)
                            searchView.editText.setSelection(searchView.editText.length())
                        }
                    }
                    searchHistoryLinearLayout!!.visibility = VISIBLE
                    if (linkYouCopied.findViewById<TextView>(R.id.suggestion_text).text.isNotEmpty()){
                        linkYouCopied.visibility = VISIBLE
                    }
                }else{
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
                        textView.setOnClickListener {
                            searchView.setText(textView.text)
                            initSearch(searchView)
                        }
                        val mb = v.findViewById<ImageButton>(R.id.set_search_query_button)
                        mb.setOnClickListener {
                            searchView.setText(textView.text)
                            searchView.editText.setSelection(searchView.editText.length())
                        }
                    }
                    searchSuggestionsLinearLayout!!.visibility = VISIBLE
                }


            }
            false
        }

        searchView.editText.setOnEditorActionListener { textView, i, keyEvent ->
            initSearch(searchView)
            true
        }

        searchBar!!.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.delete_results -> {
                    resultViewModel.getTrending()
                    selectedObjects = ArrayList()
                    searchBar!!.text = ""
                    downloadAllFabCoordinator!!.visibility = GONE
                    downloadFabs!!.visibility = GONE
                }
                R.id.delete_search -> {
                    resultViewModel.deleteAllSearchQueryHistory()
                    searchSuggestionsLinearLayout!!.removeAllViews()
                }
            }
            true
        }
    }

    private fun initSearch(searchView: SearchView){
        val inputQuery = searchView.text!!.trim { it <= ' '}
        if (inputQuery.isEmpty()) return
        searchBar!!.text = searchView.text
        searchView.hide()
        if(!sharedPreferences!!.getBoolean("incognito", false)){
            resultViewModel.addSearchQueryToHistory(inputQuery.toString())
        }
        resultViewModel.deleteAll()
        lifecycleScope.launch(Dispatchers.IO){
            resultViewModel.parseQuery(inputQuery.toString(), true)
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
        (searchBar!!.parent as AppBarLayout).setExpanded(true, true)
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
        if (sharedPreferences!!.getBoolean("download_card", true)) {
            showSingleDownloadSheet(item!!, type!!)
        } else {
            lifecycleScope.launch(Dispatchers.IO){
                val downloadItem = downloadViewModel.createDownloadItemFromResult(item!!, type!!)
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
        }
    }

    override fun onLongButtonClick(videoURL: String, type: DownloadViewModel.Type?) {
        Log.e(TAG, type.toString() + " " + videoURL)
        val item = resultsList!!.find { it?.url == videoURL }
        showSingleDownloadSheet(item!!, type!!)
    }

    private fun showSingleDownloadSheet(resultItem : ResultItem, type: DownloadViewModel.Type){
        val bottomSheet = DownloadBottomSheetDialog(resultItem, type)
        bottomSheet.show(parentFragmentManager, "downloadSingleSheet")
    }

    override fun onCardClick(videoURL: String, add: Boolean) {
        val item = resultsList?.find { it -> it?.url == videoURL }
        if (add) selectedObjects!!.add(item!!) else selectedObjects!!.remove(item)
        if (selectedObjects!!.size > 1) {
            downloadAllFabCoordinator!!.visibility = GONE
            downloadFabs!!.visibility = VISIBLE
        } else {
            downloadFabs!!.visibility = GONE
            if(resultsList!!.size > 1){
                if (resultsList!![1]!!.playlistTitle.isNotEmpty() && resultsList!![1]!!.playlistTitle != getString(R.string.trendingPlaylist)){
                    downloadAllFabCoordinator!!.visibility = VISIBLE
                }else{
                    downloadAllFabCoordinator!!.visibility = GONE
                }
            }
        }
    }

    override fun onClick(v: View) {
        val viewIdName: String = try {
            v.tag.toString()
        } catch (e: Exception) {""}
        if (viewIdName.isNotEmpty()) {
            if (viewIdName == "downloadSelected") {
                lifecycleScope.launch {
                    val downloadList = withContext(Dispatchers.IO){
                        downloadViewModel.turnResultItemsToDownloadItems(selectedObjects!!)
                    }
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        val bottomSheet = DownloadMultipleBottomSheetDialog(downloadList.toMutableList())
                        bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                    } else {
                        downloadViewModel.queueDownloads(downloadList)
                    }
                }
            }
            if (viewIdName == "downloadAll") {
                lifecycleScope.launch {
                    val downloadList = withContext(Dispatchers.IO){
                        downloadViewModel.turnResultItemsToDownloadItems(resultsList!!)
                    }
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        val bottomSheet = DownloadMultipleBottomSheetDialog(downloadList.toMutableList())
                        bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                    } else {
                        downloadViewModel.queueDownloads(downloadList)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}