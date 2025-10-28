package com.deniscerri.ytdl.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.*
import android.view.View.*
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.SearchSuggestionItem
import com.deniscerri.ytdl.database.models.SearchSuggestionType
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.HomeAdapter
import com.deniscerri.ytdl.ui.adapter.SearchSuggestionsAdapter
import com.deniscerri.ytdl.ui.more.WebViewActivity
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.collections.ArrayList


class HomeFragment : Fragment(), HomeAdapter.OnItemClickListener, SearchSuggestionsAdapter.OnItemClickListener, OnClickListener {
    private var inputQueries: MutableList<String>? = null
    private var homeAdapter: HomeAdapter? = null
    private var searchSuggestionsAdapter: SearchSuggestionsAdapter? = null
    private var queriesConstraint: ConstraintLayout? = null

    private var downloadSelectedFab: ExtendedFloatingActionButton? = null
    private var downloadAllFab: ExtendedFloatingActionButton? = null
    private var clipboardFab: ExtendedFloatingActionButton? = null
    private var homeFabs: LinearLayout? = null
    private var notificationUtil: NotificationUtil? = null
    private var downloadQueue: ArrayList<ResultItem>? = null

    private lateinit var playlistNameFilterScrollView: HorizontalScrollView
    private lateinit var playlistNameFilterChipGroup: ChipGroup

    private lateinit var resultViewModel : ResultViewModel
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var historyViewModel : HistoryViewModel

    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var searchBar: SearchBar? = null
    private var searchView: SearchView? = null
    private var providersChipGroup: ChipGroup? = null
    private var chipGroupDivider: View? = null
    private var queriesChipGroup: ChipGroup? = null
    private var recyclerView: RecyclerView? = null
    private var searchSuggestionsRecyclerView: RecyclerView? = null
    private var uiHandler: Handler? = null
    private var resultsList: List<ResultItem?>? = null
    private lateinit var selectedObjects: ArrayList<ResultItem>
    private var quickLaunchSheet = false
    private var sharedPreferences: SharedPreferences? = null
    private var actionMode: ActionMode? = null
    private var appBarLayout: AppBarLayout? = null
    private var materialToolbar: MaterialToolbar? = null
    private var loadingItems: Boolean = false
    private var queryList = mutableListOf<String>()

    private var showDownloadAllFab: Boolean = false
    private var showClipboardFab: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        quickLaunchSheet = false
        notificationUtil = NotificationUtil(requireContext())
        selectedObjects = arrayListOf()
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        uiHandler = Handler(Looper.getMainLooper())
        selectedObjects = ArrayList()

        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        downloadQueue = ArrayList()
        resultsList = mutableListOf()
        selectedObjects = ArrayList()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        //initViews
        searchBar = view.findViewById(R.id.search_bar)
        searchView = view.findViewById(R.id.search_view)
        appBarLayout = view.findViewById(R.id.home_appbarlayout)
        materialToolbar = view.findViewById(R.id.home_toolbar)
        queriesChipGroup = view.findViewById(R.id.queries)
        queriesConstraint = view.findViewById(R.id.queries_constraint)
        homeFabs = view.findViewById(R.id.home_fabs)
        downloadSelectedFab = homeFabs!!.findViewById(R.id.download_selected_fab)
        downloadAllFab = homeFabs!!.findViewById(R.id.download_all_fab)
        clipboardFab = homeFabs!!.findViewById(R.id.copied_url_fab)
        playlistNameFilterScrollView = view.findViewById(R.id.playlist_selection_chips_scrollview)
        playlistNameFilterChipGroup = view.findViewById(R.id.playlist_selection_chips)

        runCatching { materialToolbar!!.title = ThemeUtil.getStyledAppName(requireContext()) }

        homeAdapter =
            HomeAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerViewHome)
        recyclerView?.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        recyclerView?.adapter = homeAdapter
        recyclerView?.enableFastScroll()

        shimmerCards = view.findViewById(R.id.shimmer_results_framelayout)



        searchSuggestionsAdapter = SearchSuggestionsAdapter(
                this,
                requireActivity()
            )
        searchSuggestionsRecyclerView = view.findViewById(R.id.search_suggestions_recycler)
        searchSuggestionsRecyclerView?.layoutManager = LinearLayoutManager(context)
        searchSuggestionsRecyclerView?.adapter = searchSuggestionsAdapter
        searchSuggestionsRecyclerView?.itemAnimator = null

        val progressBar = view.findViewById<View>(R.id.progress)

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        resultViewModel.getFilteredList().observe(requireActivity()) {
            kotlin.runCatching {
                homeAdapter!!.submitList(it)
                resultsList = it
                progressBar.isVisible = loadingItems && resultsList!!.isNotEmpty()
                if(resultViewModel.repository.itemCount.value > 1 || resultViewModel.repository.itemCount.value == -1){
                    showDownloadAllFab = it.size > 1 && it[0].playlistTitle.isNotEmpty() && !loadingItems
                    downloadAllFab!!.isVisible = showDownloadAllFab
                }else if (resultViewModel.repository.itemCount.value == 1){
                    if (sharedPreferences!!.getBoolean("download_card", true)){
                        if(it.size == 1 && quickLaunchSheet && parentFragmentManager.findFragmentByTag("downloadSingleSheet") == null){
                            showSingleDownloadSheet(
                                it[0],
                                DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!)
                            )
                        }
                    }
                }else{
                    showDownloadAllFab = false
                    downloadAllFab!!.visibility = GONE
                }
                quickLaunchSheet = true
            }
        }

        resultViewModel.items.observe(requireActivity()) {
            updateMultiplePlaylistResults(it
                .filter { it2 -> it2.playlistTitle != "" && it2.playlistTitle != "YTDLNIS_SEARCH" }
                .map { it2 -> it2.playlistTitle }
                .distinct()
            )
        }

        initMenu()
        downloadSelectedFab?.tag = "downloadSelected"
        downloadSelectedFab?.setOnClickListener(this)
        downloadAllFab?.tag = "downloadAll"
        downloadAllFab?.setOnClickListener(this)

        if (arguments?.getString("url") != null){
            val url = requireArguments().getString("url")
            if (inputQueries == null) inputQueries = mutableListOf()
            searchBar?.setText(url)
            val argList = url!!.split("\n").filter { it.isURL() }.toMutableList()
            argList.removeAll(listOf("", null))
            inputQueries!!.addAll(argList)
        }

        if (inputQueries != null) {
            lifecycleScope.launch(Dispatchers.IO){
                resultViewModel.deleteAll()
                resultViewModel.parseQueries(inputQueries!!){}
                inputQueries = null
            }
        }

        searchView?.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.SHOWING){
                mainActivity?.hideBottomNavigation()
            }else if (newState == SearchView.TransitionState.HIDING){
                mainActivity?.showBottomNavigation()
            }
        }

        mainActivity?.onBackPressedDispatcher?.addCallback(this) {
            if (searchView?.isShowing == true) {
                searchView?.hide()
            }else{
                mainActivity?.finishAffinity()
            }
        }

        lifecycleScope.launch {
            launch{
                resultViewModel.uiState.collectLatest { res ->
                    if (res.errorMessage != null){
                        val isSingleQueryAndURL = queryList.size == 1 && Patterns.WEB_URL.matcher(queryList.first()).matches()

                        kotlin.runCatching {
                            UiUtil.handleNoResults(requireActivity(), res.errorMessage!!,
                                url = if (isSingleQueryAndURL) queryList.first() else null,
                                continueAnyway = isSingleQueryAndURL,
                                continued = {
                                    lifecycleScope.launch {
                                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                                        withContext(Dispatchers.Main){
                                            showSingleDownloadSheet(
                                                resultItem = downloadViewModel.createEmptyResultItem(queryList.first()),
                                                type = DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!),
                                                disableUpdateData = true
                                            )
                                        }
                                    } else {
                                        val downloadItem = downloadViewModel.createDownloadItemFromResult(
                                            result = downloadViewModel.createEmptyResultItem(queryList.first()),
                                            givenType = DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!)
                                        )
                                        downloadViewModel.queueDownloads(listOf(downloadItem))
                                    }
                                }
                                },
                                cookieFetch = {
                                    val myIntent = Intent(requireContext(), WebViewActivity::class.java)
                                    myIntent.putExtra("url", "https://${URL(queryList.first()).host}")
                                    cookiesFetchedResultLauncher.launch(myIntent)
                                },
                                closed = {}
                            )
                        }
                        resultViewModel.uiState.update {it.copy(errorMessage  = null) }
                    }

                    loadingItems = res.processing
                    progressBar.isVisible = loadingItems && resultsList!!.isNotEmpty()
                    if (res.processing){
                        recyclerView?.setPadding(0,0,0,0)
                        shimmerCards!!.startShimmer()
                        shimmerCards!!.visibility = VISIBLE
                    }else{
                        recyclerView?.setPadding(0,0,0,100)
                        shimmerCards!!.stopShimmer()
                        shimmerCards!!.visibility = GONE

                        showDownloadAllFab = resultsList!!.size > 1 && resultsList!![0]!!.playlistTitle.isNotEmpty()
                        downloadAllFab!!.isVisible = showDownloadAllFab
                    }
                }
            }
        }

        lifecycleScope.launch {
            launch{
                downloadViewModel.alreadyExistsUiState.collectLatest { res ->
                    if (res.isNotEmpty()){
                        withContext(Dispatchers.Main){
                            val bundle = bundleOf(
                                Pair("duplicates", ArrayList(res))
                            )
                            delay(500)
                            findNavController().navigate(R.id.downloadsAlreadyExistDialog, bundle)
                        }
                        downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                    }
                }
            }
        }

    }

    private var cookiesFetchedResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            sharedPreferences?.edit()?.putBoolean("use_cookies", true)?.apply()
            startSearch()
        }
    }

    override fun onResume() {
        super.onResume()
        if(arguments?.getString("url") == null){
            if (!resultViewModel.uiState.value.processing){
                resultViewModel.checkTrending()
            }
        }else{
            arguments?.remove("url")
        }

        if (arguments?.getBoolean("showDownloadsWithUpdatedFormats") == true){
            notificationUtil?.cancelDownloadNotification(NotificationUtil.FORMAT_UPDATING_FINISHED_NOTIFICATION_ID)
            arguments?.remove("showDownloadsWithUpdatedFormats")
            CoroutineScope(Dispatchers.IO).launch {
                val ids = arguments?.getLongArray("downloadIds") ?: return@launch
                downloadViewModel.turnDownloadItemsToProcessingDownloads(ids.toList(), deleteExisting = true)
                withContext(Dispatchers.Main){
                    findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                }
            }
        }
        
        if(arguments?.getBoolean("search") == true){
            arguments?.remove("search")
            requireView().post {
                searchBar?.performClick()
            }
        }

        if (searchView?.currentTransitionState == SearchView.TransitionState.SHOWN){
            updateSearchViewItems(searchView?.editText?.text.toString())
        }

        requireView().post {
            checkClipboard().apply {
                this?.apply {
                    showClipboardFab = this.isNotEmpty()
                    clipboardFab?.isVisible = showClipboardFab
                    clipboardFab?.setOnClickListener {
                        if (this.size == 1){
                            searchView?.setText(this.first())
                            showClipboardFab = false
                            clipboardFab?.isVisible = false
                            initSearch(searchView!!)
                        }else{
                            searchBar?.performClick()
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO){
                                    delay(500)
                                }
                                this@apply.forEach {
                                    onSearchSuggestionAdd(it)
                                }
                            }
                        }
                    }
                }

                lifecycleScope.launch {
                    clipboardFab?.extend()
                    delay(1000)
                    clipboardFab?.shrink()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initMenu() {
        val queriesConstraint = requireView().findViewById<ConstraintLayout>(R.id.queries_constraint)
        val queriesInitStartBtn = queriesConstraint.findViewById<MaterialButton>(R.id.init_search_query)
        val isRightToLeft = resources.getBoolean(R.bool.is_right_to_left)

        providersChipGroup = searchView!!.findViewById(R.id.providers)
        val providers = resources.getStringArray(R.array.search_engines)
        val providersValues = resources.getStringArray(R.array.search_engines_values).toMutableList()

        for(i in providersValues.indices){
            val provider = providers[i]
            val providerValue = providersValues[i]
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, providersChipGroup, false) as Chip
            tmp.text = provider
            tmp.id = i
            tmp.tag = providersValues[i]

            tmp.setOnClickListener {
                val editor = sharedPreferences?.edit()
                editor?.putString("search_engine", providerValue)
                editor?.apply()

            }

            providersChipGroup?.addView(tmp)
        }

        chipGroupDivider = requireView().findViewById(R.id.chipGroupDivider)

        searchView!!.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.SHOWN) {
                val currentProvider = sharedPreferences?.getString("search_engine", "ytsearch")
                providersChipGroup?.children?.forEach {
                    val tmp = providersChipGroup?.findViewById<Chip>(it.id)
                    if (tmp?.tag?.equals(currentProvider) == true) {
                        tmp.isChecked = true
                        return@forEach
                    }
                }

                if (Patterns.WEB_URL.matcher(searchBar!!.text.toString()).matches() && searchBar!!.text.isNotBlank()){
                    providersChipGroup?.visibility = GONE
                    chipGroupDivider?.visibility = GONE
                }else{
                    providersChipGroup?.visibility = VISIBLE
                    chipGroupDivider?.visibility = VISIBLE
                }

                updateSearchViewItems(searchView!!.editText.text.toString())
            }
        }

        searchView!!.editText.doAfterTextChanged {
            if (searchView!!.currentTransitionState != SearchView.TransitionState.SHOWN) return@doAfterTextChanged
            updateSearchViewItems(it.toString())
        }

        searchView!!.editText.setOnTouchListener(OnTouchListener { _, event ->
            try{
                val drawableLeft = 0
                val drawableRight = 2
                if (event.action == MotionEvent.ACTION_UP) {
                    if (
                        (isRightToLeft && (event.x < (searchView!!.editText.left - searchView!!.editText.compoundDrawables[drawableLeft].bounds.width()))) ||
                        (!isRightToLeft && (event.x > (searchView!!.editText.right - searchView!!.editText.compoundDrawables[drawableRight].bounds.width())))
                        ){

                        val present = queriesChipGroup!!.children.firstOrNull { (it as Chip).text.toString() == searchView!!.editText.text.toString() }
                        if (present == null) {
                            val chip = layoutinflater!!.inflate(R.layout.input_chip, queriesChipGroup, false) as Chip
                            chip.text = searchView!!.editText.text
                            chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
                            chip.setOnClickListener {
                                queriesChipGroup!!.removeView(chip)
                                if (queriesChipGroup!!.childCount == 0) queriesConstraint.visibility = GONE
                            }
                            queriesChipGroup!!.addView(chip)
                        }
                        if (queriesChipGroup!!.childCount == 0) queriesConstraint.visibility = GONE
                        else queriesConstraint.visibility = VISIBLE
                        searchView!!.editText.setText("")
                        return@OnTouchListener true

                    }
                }
            }catch (ignored: Exception){}
            false
        })

        searchView!!.editText.setOnEditorActionListener { _, _, _ ->
            initSearch(searchView!!)
            true
        }

        searchBar!!.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.delete_results -> {
                    resultsList = listOf()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO){
                            resultViewModel.cancelParsingQueries()
                        }
                    }
                    resultViewModel.getHomeRecommendations()
                    selectedObjects = ArrayList()
                    searchBar!!.setText("")
                    showDownloadAllFab = false
                    downloadAllFab!!.visibility = GONE
                    downloadSelectedFab!!.visibility = GONE
                }
                R.id.delete_search -> {
                    resultViewModel.deleteAllSearchQueryHistory()
                    searchSuggestionsAdapter?.submitList(listOf())
                }
            }
            true
        }
        queriesChipGroup!!.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (queriesChipGroup!!.childCount == 0) queriesConstraint.visibility = GONE
            else queriesConstraint.visibility = VISIBLE
        }

        queriesInitStartBtn.setOnClickListener {
            initSearch(searchView!!)
        }
    }

    @SuppressLint("InflateParams")
    private fun updateSearchViewItems(searchQuery: String) = lifecycleScope.launch(Dispatchers.Main) {
        lifecycleScope.launch {
            if (searchView!!.editText.text.isEmpty()){
                searchView!!.editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }else{
                searchView!!.editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_plus, 0)
            }

            val combinedList = mutableListOf<SearchSuggestionItem>()

            val history = withContext(Dispatchers.IO){
                resultViewModel.getSearchHistory().map { it.query }.filter { it.contains(searchQuery, ignoreCase = true) }
            }.map {
                SearchSuggestionItem(it, SearchSuggestionType.HISTORY)
            }
            val suggestions = if (sharedPreferences!!.getBoolean("search_suggestions", false)){
                withContext(Dispatchers.IO){
                    resultViewModel.getSearchSuggestions(searchQuery)
                }
            }else{
                emptyList()
            }.map {
                SearchSuggestionItem(it, SearchSuggestionType.SUGGESTION)
            }

            combinedList.addAll(history)
            combinedList.addAll(suggestions)

            val url = checkClipboard()
            url?.apply {
                var alreadyHasThem = this.all { queriesChipGroup?.children?.any { c -> (c as Chip).text.contains(it) } == true }
                if (this.isNotEmpty() && !alreadyHasThem){
                    combinedList.add(0, SearchSuggestionItem(this.joinToString("\n"), SearchSuggestionType.CLIPBOARD))
                }
            }

            searchSuggestionsAdapter?.submitList(combinedList)

//            history.forEach { s ->
//                val v = LayoutInflater.from(fragmentContext).inflate(R.layout.search_suggestion_item, null)
//                val textView = v.findViewById<TextView>(R.id.suggestion_text)
//                textView.text = s
//                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_restore, 0, 0, 0)
//                Handler(Looper.getMainLooper()).post {
//                    searchHistoryLinearLayout!!.addView(
//                        v
//                    )
//                }
//                textView.setOnClickListener {
//                    searchView!!.setText(s)
//                    initSearch(searchView!!)
//                }
//                textView.setOnLongClickListener {
//                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
//                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + s + "\"!")
//                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
//                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
//                        searchHistoryLinearLayout!!.removeView(v)
//                        resultViewModel.removeSearchQueryFromHistory(s)
//                    }
//                    deleteDialog.show()
//                    true
//                }
//
//                val mb = v.findViewById<ImageButton>(R.id.set_search_query_button)
//                mb.setOnClickListener {
//                    searchView!!.editText.setText(s)
//                    searchView!!.editText.setSelection(searchView!!.editText.length())
//                }
//            }
//            searchHistoryLinearLayout!!.isVisible = history.isNotEmpty()
//            if (linkYouCopied.findViewById<TextView>(R.id.suggestion_text).text.isNotEmpty()){
//                linkYouCopied.visibility = VISIBLE
//                clipboardFab?.isVisible = true
//            }
//
//            suggestions.forEach { s ->
//                val v = LayoutInflater.from(fragmentContext)
//                    .inflate(R.layout.search_suggestion_item, null)
//                val textView = v.findViewById<TextView>(R.id.suggestion_text)
//                textView.text = s
//                Handler(Looper.getMainLooper()).post {
//                    searchSuggestionsLinearLayout!!.addView(
//                        v
//                    )
//                }
//                textView.setOnClickListener {
//                    searchView!!.setText(s)
//                    initSearch(searchView!!)
//                }
//                val mb = v.findViewById<ImageButton>(R.id.set_search_query_button)
//                mb.setOnClickListener {
//                    searchView!!.editText.setText(s)
//                    searchView!!.editText.setSelection(searchView!!.editText.length())
//                }
//            }
//            searchSuggestionsLinearLayout!!.isVisible = suggestions.isNotEmpty()

            if (Patterns.WEB_URL.matcher(searchView!!.editText.text).matches()){
                providersChipGroup?.visibility = GONE
                chipGroupDivider?.visibility = GONE
            }else{
                providersChipGroup?.visibility = VISIBLE
                chipGroupDivider?.visibility = VISIBLE
            }
        }

    }

    private fun initSearch(searchView: SearchView){

        queryList = mutableListOf()
        if (queriesChipGroup!!.childCount > 0){
            queriesChipGroup!!.children.forEach {
                val query = (it as Chip).text.toString().trim {it2 -> it2 <= ' '}
                if (query.isNotEmpty()){
                    queryList.add(query)
                }
            }
            queriesChipGroup!!.removeAllViews()
        }
        if (searchView.editText.text.isNotBlank()) {
            queryList.add(searchView.editText.text.toString())
        }

        if (queryList.isEmpty()) return
        if (queryList.size == 1){
            searchBar!!.setText(searchView.text)
        }

        searchView.hide()
        if(!sharedPreferences!!.getBoolean("incognito", false)){
            queryList.forEach { q ->
                resultViewModel.addSearchQueryToHistory(q)
            }
        }
        startSearch()
    }

    private fun startSearch() {
        lifecycleScope.launch(Dispatchers.IO){
            resultViewModel.deleteAll()
            if(sharedPreferences!!.getBoolean("quick_download", false) || sharedPreferences!!.getString("preferred_download_type", "video") == "command"){
                if (queryList.size == 1 && Patterns.WEB_URL.matcher(queryList.first()).matches()){
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        withContext(Dispatchers.Main){
                            showSingleDownloadSheet(
                                resultItem = downloadViewModel.createEmptyResultItem(queryList.first()),
                                type = DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!)
                            )
                        }
                    } else {
                        val downloadItem = downloadViewModel.createDownloadItemFromResult(
                            result = downloadViewModel.createEmptyResultItem(queryList.first()),
                            givenType = DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!)
                        )
                        downloadViewModel.queueDownloads(listOf(downloadItem))
                    }

                }else{
                    resultViewModel.parseQueries(queryList){}
                }
            }else{
                resultViewModel.parseQueries(queryList){}
            }
        }
    }

    fun scrollToTop() {
        recyclerView!!.scrollToPosition(0)
        runCatching { (searchBar!!.parent as AppBarLayout).setExpanded(true, true) }
    }

    @SuppressLint("ResourceType")
    override fun onButtonClick(videoURL: String, type: DownloadViewModel.Type?) {
        Log.e(TAG, type.toString() + " " + videoURL)
        val item = resultsList!!.find { it?.url == videoURL }
        Log.e(TAG, resultsList!![0].toString() + " " + videoURL)
        recyclerView!!.findViewWithTag<MaterialButton>("""${item?.url}##$type""")
        if (sharedPreferences!!.getBoolean("download_card", true)) {
            showSingleDownloadSheet(item!!, type!!)
        } else {
            lifecycleScope.launch{
                val downloadItem = withContext(Dispatchers.IO){
                    downloadViewModel.createDownloadItemFromResult(
                        result = item!!,
                        givenType = type!!)
                }
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
        }
    }

    override fun onLongButtonClick(videoURL: String, type: DownloadViewModel.Type?) {
        Log.e(TAG, type.toString() + " " + videoURL)
        val item = resultsList!!.find { it?.url == videoURL }
        showSingleDownloadSheet(item!!, type!!)
    }

    @SuppressLint("RestrictedApi")
    private fun showSingleDownloadSheet(
        resultItem: ResultItem,
        type: DownloadViewModel.Type,
        disableUpdateData : Boolean = false
    ){
        if(findNavController().currentBackStack.value.firstOrNull {it.destination.id == R.id.downloadBottomSheetDialog} == null &&
            findNavController().currentDestination?.id == R.id.homeFragment
            ){
            //show the fragment if its not in the backstack
            val bundle = Bundle()
            bundle.putParcelable("result", resultItem)
            bundle.putSerializable("type", downloadViewModel.getDownloadType(type, resultItem.url))
            if (disableUpdateData) {
                bundle.putBoolean("disableUpdateData", true)
            }
            findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
        }
    }

    override fun onCardClick(videoURL: String, add: Boolean) {
        lifecycleScope.launch {
            val item = resultsList?.find { it?.url == videoURL }
            if (actionMode == null) actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)
            actionMode?.apply {
                if (add) selectedObjects.add(item!!)
                else selectedObjects.remove(item!!)

                if (selectedObjects.size == 0){
                    this.finish()
                }else{
                    actionMode?.title = "${selectedObjects.size} ${getString(R.string.selected)}"
                    this.menu.findItem(R.id.select_between).isVisible = false
                    if(selectedObjects.size == 2){
                        val selectedIDs = selectedObjects.sortedBy { it.id }
                        val resultsInMiddle = withContext(Dispatchers.IO){
                            resultViewModel.getResultsBetweenTwoItems(selectedIDs.first().id, selectedIDs.last().id)
                        }.toMutableList()
                        this.menu.findItem(R.id.select_between).isVisible = resultsInMiddle.isNotEmpty()
                    }
                }
            }
        }
    }

    override fun onCardDetailsClick(videoURL: String) {
        if (parentFragmentManager.findFragmentByTag("resultDetails") == null && resultsList != null && resultsList!!.isNotEmpty()){
            val bundle = Bundle()
            bundle.putParcelable("result", resultsList!!.first{it!!.url == videoURL}!!)
            findNavController().navigate(R.id.resultCardDetailsDialog, bundle)
        }
    }

    override fun onClick(v: View) {
        val viewIdName: String = try {
            v.tag.toString()
        } catch (e: Exception) {""}
        if (viewIdName.isNotEmpty()) {
            if (viewIdName == "downloadAll") {
                val showDownloadCard = sharedPreferences!!.getBoolean("download_card", true)
                downloadViewModel.turnResultItemsToProcessingDownloads(resultsList!!.map { it!!.id }, downloadNow = !showDownloadCard)
                if (showDownloadCard){
                    findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                }
            }
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.main_menu_context, menu)
            mode.title = "${selectedObjects.size} ${getString(R.string.selected)}"
            searchBar!!.isEnabled = false
            playlistNameFilterChipGroup.children.forEach { it.isEnabled = false }
            searchBar!!.menu.forEach { it.isEnabled = false }
            (activity as MainActivity).disableBottomNavigation()
            downloadAllFab!!.isVisible = false
            clipboardFab!!.isVisible = false
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
                        val selectedIDs = selectedObjects.sortedBy { it.id }
                        val resultsInMiddle = withContext(Dispatchers.IO){
                            resultViewModel.getResultsBetweenTwoItems(selectedIDs.first().id, selectedIDs.last().id)
                        }.toMutableList()
                        if (resultsInMiddle.isNotEmpty()){
                            selectedObjects.addAll(resultsInMiddle)
                            homeAdapter?.checkMultipleItems(selectedObjects.map { it.url })
                            actionMode?.title = "${selectedObjects.count()} ${getString(R.string.selected)}"
                        }
                        mode?.menu?.findItem(R.id.select_between)?.isVisible = false
                    }
                    true
                }
                R.id.delete_results -> {
                    val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        if (selectedObjects.size == resultsList?.size){
                            lifecycleScope.launch(Dispatchers.IO){
                                resultViewModel.deleteAll()
                            }
                        }else{
                            resultViewModel.deleteSelected(selectedObjects.toList())
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.download -> {
                    lifecycleScope.launch {
                        val showDownloadCard = sharedPreferences!!.getBoolean("download_card", true)
                        if (showDownloadCard && selectedObjects.size == 1) {
                            showSingleDownloadSheet(
                                selectedObjects[0],
                                downloadViewModel.getDownloadType(url = selectedObjects[0].url)
                            )
                        }else{
                            downloadViewModel.turnResultItemsToProcessingDownloads(selectedObjects.map { it.id }, downloadNow = !showDownloadCard)
                            if (showDownloadCard){
                                findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                            }
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    true
                }
                R.id.select_all -> {
                    homeAdapter?.checkAll(resultsList)
                    selectedObjects.clear()
                    resultsList?.forEach { selectedObjects.add(it!!) }
                    mode?.title = "(${selectedObjects.size}) ${resources.getString(R.string.all_items_selected)}"
                    true
                }
                R.id.invert_selected -> {
                    homeAdapter?.invertSelected(resultsList)
                    val invertedList = arrayListOf<ResultItem>()
                    resultsList?.forEach {
                        if (!selectedObjects.contains(it)) invertedList.add(it!!)
                    }
                    selectedObjects.clear()
                    selectedObjects.addAll(invertedList)
                    actionMode!!.title = "${selectedObjects.size} ${getString(R.string.selected)}"
                    if (invertedList.isEmpty()) actionMode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            (activity as MainActivity).enableBottomNavigation()
            clearCheckedItems()
            searchBar!!.isEnabled = true
            playlistNameFilterChipGroup.children.forEach { it.isEnabled = true }
            searchBar!!.menu.forEach { it.isEnabled = true }
            searchBar?.expand(appBarLayout!!)

            downloadAllFab!!.isVisible = showDownloadAllFab
            clipboardFab!!.isVisible = showClipboardFab
        }
    }

    private fun clearCheckedItems(){
        homeAdapter?.clearCheckedItems()
        selectedObjects.forEach {
            homeAdapter?.notifyItemChanged(resultsList!!.indexOf(it))
        }
        selectedObjects.clear()
    }


    private fun checkClipboard(): List<String>?{
        return kotlin.runCatching {
            val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip!!.getItemAt(0).text
            return clip.split("\r","\n").map { it.trim() }.filter { Patterns.WEB_URL.matcher(it).matches() }
        }.getOrNull()
    }

    override fun onStop() {
        actionMode?.finish()
        super.onStop()
    }


    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onSearchSuggestionClick(text: String) {
        val res = text.split("\n")
        if (res.size == 1){
            showClipboardFab = false
            clipboardFab?.isVisible = false
            searchView!!.setText(text)
            initSearch(searchView!!)
        }else{
            res.forEach {
                onSearchSuggestionAdd(it)
            }
        }

    }

    override fun onSearchSuggestionAdd(text: String) {
        val items = text.split("\n")

        items.forEach {t ->
            val present = queriesChipGroup!!.children.firstOrNull { (it as Chip).text.toString() == t }
            if (present == null) {
                val chip = layoutinflater!!.inflate(R.layout.input_chip, queriesChipGroup, false) as Chip
                chip.text = t
                chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
                chip.setOnClickListener {
                    if (queriesChipGroup!!.childCount == 1) queriesConstraint!!.visibility = View.GONE
                    queriesChipGroup!!.removeView(chip)
                }
                queriesChipGroup!!.addView(chip)
            }

        }

        searchView!!.editText.setText("")
        queriesConstraint?.isVisible = queriesChipGroup?.childCount!! > 0

        searchSuggestionsAdapter?.getList()?.apply {
            if (this.isNotEmpty()) {
                if (this.first().type == SearchSuggestionType.CLIPBOARD){
                    val newList = this.toMutableList().drop(1)
                    searchSuggestionsAdapter?.submitList(newList)
                }
            }

        }
    }

    override fun onSearchSuggestionLongClick(text: String, position: Int) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + text + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            resultViewModel.removeSearchQueryFromHistory(text)
            updateSearchViewItems(searchView!!.editText.text.toString())
        }
        deleteDialog.show()
    }

    override fun onSearchSuggestionAddToSearchBar(text: String) {
          searchView!!.editText.setText(text)
          searchView!!.editText.setSelection(searchView!!.editText.length())
    }


    private fun updateMultiplePlaylistResults(playlistTitles: List<String>) {
        playlistNameFilterChipGroup.children.filter { it.tag != "all" }.forEach {
            playlistNameFilterChipGroup.removeView(it)
        }

        if (playlistTitles.isEmpty() || playlistTitles.size == 1) {
            playlistNameFilterScrollView.isVisible = false
            return
        }

        playlistNameFilterChipGroup.children.first().setOnClickListener {
            resultViewModel.setPlaylistFilter("")
        }

        for (t in playlistTitles) {
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, playlistNameFilterChipGroup, false) as Chip
            tmp.text = t
            tmp.tag = t
            tmp.setOnClickListener {
                resultViewModel.setPlaylistFilter(t)
            }

            playlistNameFilterChipGroup.addView(tmp)
        }

        if (playlistNameFilterChipGroup.children.all { !(it as Chip).isChecked }) {
            (playlistNameFilterChipGroup.children.first() as Chip).isChecked = true
        }

        playlistNameFilterScrollView.isVisible = true
    }
}