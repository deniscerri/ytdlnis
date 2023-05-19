package com.deniscerri.ytdlnis.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class HomeFragment : Fragment(), HomeAdapter.OnItemClickListener, OnClickListener {
    private var inputQueries: MutableList<String>? = null
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

    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var searchBar: SearchBar? = null
    private var searchView: SearchView? = null
    private var linkYouCopied: ConstraintLayout? = null
    private var queriesChipGroup: ChipGroup? = null
    private var recyclerView: RecyclerView? = null
    private var uiHandler: Handler? = null
    private var resultsList: List<ResultItem?>? = null
    private var selectedObjects: ArrayList<ResultItem>? = null
    private var fileUtil: FileUtil? = null
    private var quickLaunchSheet = false
    private var sharedPreferences: SharedPreferences? = null
    private var _binding : FragmentHomeBinding? = null
    private var actionMode: ActionMode? = null
    private var appBarLayout: AppBarLayout? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        mainActivity?.showBottomNavigation()
        quickLaunchSheet = false
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

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        //initViews
        shimmerCards = view.findViewById(R.id.shimmer_results_framelayout)
        searchBar = view.findViewById(R.id.search_bar)
        searchView = view.findViewById(R.id.search_view)
        linkYouCopied = searchView?.findViewById(R.id.link_you_copied)
        appBarLayout = view.findViewById(R.id.home_appbarlayout)
        queriesChipGroup = view.findViewById(R.id.queries)
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
        recyclerView?.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        recyclerView?.adapter = homeAdapter

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        resultViewModel.items.observe(viewLifecycleOwner) {
            homeAdapter!!.submitList(it)
            resultsList = it
            if(resultViewModel.itemCount.value!! > 1 || resultViewModel.itemCount.value!! == -1){
                if (it.size > 1 && it[0].playlistTitle.isNotEmpty() && it[0].playlistTitle != getString(R.string.trendingPlaylist)){
                    downloadAllFabCoordinator!!.visibility = VISIBLE
                }else{
                    downloadAllFabCoordinator!!.visibility = GONE
                }
            }else if (resultViewModel.itemCount.value!! == 1){
                if (sharedPreferences!!.getBoolean("download_card", true)){
                    if(it.size == 1 && quickLaunchSheet && parentFragmentManager.findFragmentByTag("downloadSingleSheet") == null){
                        showSingleDownloadSheet(it[0], DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!), false)
                    }
                }
            }else{
                downloadAllFabCoordinator!!.visibility = GONE
            }
            quickLaunchSheet = true
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

        if (arguments?.getString("url") != null){
            val url = requireArguments().getString("url")
            if (inputQueries == null) inputQueries = mutableListOf()
            searchBar?.text = url
            inputQueries!!.add(url!!)
        }

        if (inputQueries != null) {
            resultViewModel.deleteAll()
            lifecycleScope.launch(Dispatchers.IO){
                resultViewModel.parseQueries(inputQueries!!)
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

    }

    override fun onResume() {
        super.onResume()
        if(arguments?.getString("url") == null){
            resultViewModel.checkTrending()
        }else{
            arguments?.remove("url")
        }
        if (searchView?.currentTransitionState == SearchView.TransitionState.SHOWN){
            updateSearchViewItems(searchView?.editText?.text, linkYouCopied)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initMenu() {
        val queriesConstraint = requireView().findViewById<ConstraintLayout>(R.id.queries_constraint)
        val queriesInitStartBtn = queriesConstraint.findViewById<MaterialButton>(R.id.init_search_query)
        val isRightToLeft = resources.getBoolean(R.bool.is_right_to_left)


        infoUtil = InfoUtil(requireContext())
        searchView!!.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.SHOWN) {
                try{
                    val clipboard =
                        requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val regex =
                        "(https?://(?:www\\.|(?!www))[a-zA-Z\\d][a-zA-Z\\d-]+[a-zA-Z\\d]\\.\\S{2,}|www\\.[a-zA-Z\\d][a-zA-Z\\d-]+[a-zA-Z\\d]\\.\\S{2,}|https?://(?:www\\.|(?!www))[a-zA-Z\\d]+\\.\\S{2,}|www\\.[a-zA-Z\\d]+\\.\\S{2,})".toRegex()
                    val clip = clipboard.primaryClip!!.getItemAt(0).text
                    if (regex.containsMatchIn(clip.toString())) {
                        linkYouCopied!!.visibility = VISIBLE
                        val textView = linkYouCopied!!.findViewById<TextView>(R.id.suggestion_text)
                        textView.text = getString(R.string.link_you_copied)
                        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_language, 0, 0, 0)
                        val mb = linkYouCopied!!.findViewById<ImageButton>(R.id.set_search_query_button)
                        mb.setImageResource(R.drawable.ic_plus)

                        mb.setOnClickListener {
                            val present = queriesChipGroup!!.children.firstOrNull { (it as Chip).text.toString() == clip.toString() }
                            if (present == null) {
                                val chip = layoutinflater!!.inflate(R.layout.input_chip, queriesChipGroup, false) as Chip
                                chip.text = clip.toString()
                                chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.BLACK))
                                chip.setOnClickListener {
                                    queriesChipGroup!!.removeView(chip)
                                }
                                queriesChipGroup!!.addView(chip)
                            }
                            if (queriesChipGroup!!.childCount == 0) queriesConstraint.visibility = GONE
                            else queriesConstraint.visibility = VISIBLE
                            searchView!!.editText.setText("")
                            linkYouCopied!!.visibility = GONE
                        }

                        textView.setOnClickListener {
                            searchView!!.setText(clip.toString())
                            initSearch(searchView!!)
                        }
                    }else{
                        linkYouCopied!!.visibility = GONE
                    }
                    updateSearchViewItems(searchView!!.editText.text, linkYouCopied)
                }catch (e: Exception){
                    e.printStackTrace()
                    linkYouCopied!!.visibility = GONE
                }
            }
        }

        searchView!!.editText.doAfterTextChanged {
            if (searchView!!.currentTransitionState != SearchView.TransitionState.SHOWN) return@doAfterTextChanged
            updateSearchViewItems(it, linkYouCopied)
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
        queriesChipGroup!!.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (queriesChipGroup!!.childCount == 0) queriesConstraint.visibility = GONE
            else queriesConstraint.visibility = VISIBLE
            searchView!!.editText.setText("")
        }

        queriesInitStartBtn.setOnClickListener {
            initSearch(searchView!!)
        }
    }

    @SuppressLint("InflateParams")
    private fun updateSearchViewItems(it: Editable?, linkYouCopied: View?){
        lifecycleScope.launch {
            searchSuggestionsLinearLayout!!.removeAllViews()
            searchHistoryLinearLayout!!.removeAllViews()

            searchSuggestionsLinearLayout!!.visibility = GONE
            searchHistoryLinearLayout!!.visibility = GONE
            linkYouCopied!!.visibility = GONE

            if (searchView!!.editText.text.isEmpty()){
                searchView!!.editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }else{
                searchView!!.editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_plus, 0)
            }
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
                    textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_restore, 0, 0, 0)
                    Handler(Looper.getMainLooper()).post {
                        searchHistoryLinearLayout!!.addView(
                            v
                        )
                    }
                    textView.setOnClickListener {
                        searchView!!.setText(textView.text)
                        initSearch(searchView!!)
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
                        searchView!!.setText(textView.text)
                        searchView!!.editText.setSelection(searchView!!.editText.length())
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
                        searchView!!.setText(textView.text)
                        initSearch(searchView!!)
                    }
                    val mb = v.findViewById<ImageButton>(R.id.set_search_query_button)
                    mb.setOnClickListener {
                        searchView!!.setText(textView.text)
                        searchView!!.editText.setSelection(searchView!!.editText.length())
                    }
                }
                searchSuggestionsLinearLayout!!.visibility = VISIBLE
            }


        }
    }

    private fun initSearch(searchView: SearchView){

        val queryList = mutableListOf<String>()
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
            searchBar!!.text = searchView.text
        }

        searchView.hide()
        if(!sharedPreferences!!.getBoolean("incognito", false)){
            queryList.forEach { q ->
                resultViewModel.addSearchQueryToHistory(q)
            }
        }
        resultViewModel.deleteAll()
        lifecycleScope.launch(Dispatchers.IO){
            Thread.sleep(300)
            if(sharedPreferences!!.getBoolean("quick_download", false)){
                if (queryList.size == 1 && queryList.first().matches("(https?://(?:www\\.|(?!www))[a-zA-Z\\d][a-zA-Z\\d-]+[a-zA-Z\\d]\\.\\S{2,}|www\\.[a-zA-Z\\d][a-zA-Z\\d-]+[a-zA-Z\\d]\\.\\S{2,}|https?://(?:www\\.|(?!www))[a-zA-Z\\d]+\\.\\S{2,}|www\\.[a-zA-Z\\d]+\\.\\S{2,})".toRegex())){
                    val resultItem = downloadViewModel.createEmptyResultItem(queryList.first())
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        showSingleDownloadSheet(resultItem, DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!), true)
                    } else {
                        lifecycleScope.launch{
                            val downloadItem = withContext(Dispatchers.IO){
                                downloadViewModel.createDownloadItemFromResult(resultItem, DownloadViewModel.Type.valueOf(sharedPreferences!!.getString("preferred_download_type", "video")!!))
                            }
                            downloadViewModel.queueDownloads(listOf(downloadItem))
                        }
                    }

                }else{
                    resultViewModel.parseQueries(queryList)
                }
            }else{
                resultViewModel.parseQueries(queryList)
            }
        }
    }

    fun handleFileIntent(lines: List<String>) {
        inputQueries = lines.toMutableList()
    }

    fun scrollToTop() {
        recyclerView!!.scrollToPosition(0)
        (searchBar!!.parent as AppBarLayout).setExpanded(true, true)
    }

    @SuppressLint("ResourceType")
    override fun onButtonClick(videoURL: String, type: DownloadViewModel.Type?) {
        Log.e(TAG, type.toString() + " " + videoURL)
        val item = resultsList!!.find { it?.url == videoURL }
        Log.e(TAG, resultsList!![0].toString() + " " + videoURL)
        recyclerView!!.findViewWithTag<MaterialButton>("""${item?.url}##$type""")
        if (sharedPreferences!!.getBoolean("download_card", true)) {
            showSingleDownloadSheet(item!!, type!!, false)
        } else {
            lifecycleScope.launch{
                val downloadItem = withContext(Dispatchers.IO){
                    downloadViewModel.createDownloadItemFromResult(item!!, type!!)
                }
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
        }
    }

    override fun onLongButtonClick(videoURL: String, type: DownloadViewModel.Type?) {
        Log.e(TAG, type.toString() + " " + videoURL)
        val item = resultsList!!.find { it?.url == videoURL }
        showSingleDownloadSheet(item!!, type!!, false)
    }

    private fun showSingleDownloadSheet(resultItem : ResultItem, type: DownloadViewModel.Type, quickDownload: Boolean){
        val bottomSheet = DownloadBottomSheetDialog(resultItem, type, null, quickDownload)
        bottomSheet.show(parentFragmentManager, "downloadSingleSheet")
    }

    override fun onCardClick(videoURL: String, add: Boolean) {
        val item = resultsList?.find { it?.url == videoURL }
        if (add) {
            selectedObjects!!.add(item!!)
            if (actionMode == null){
                actionMode = (getActivity() as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)
            }else{
                actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            }
        } else {
            selectedObjects!!.remove(item)
            actionMode?.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            if (selectedObjects!!.isEmpty()){
                actionMode?.finish()
            }
        }


    }

    override fun onClick(v: View) {
        val viewIdName: String = try {
            v.tag.toString()
        } catch (e: Exception) {""}
        if (viewIdName.isNotEmpty()) {
            if (viewIdName == "downloadAll") {
                lifecycleScope.launch {
                    val downloadList = withContext(Dispatchers.IO){
                        downloadViewModel.turnResultItemsToDownloadItems(resultsList!!)
                    }
                    if (sharedPreferences!!.getBoolean("download_card", true)) {
                        val bottomSheet = DownloadMultipleBottomSheetDialog(resultsList!!, downloadList.toMutableList())
                        bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                    } else {
                        downloadViewModel.queueDownloads(downloadList)
                    }
                }
            }
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.main_menu_context, menu)
            mode.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            searchBar!!.isEnabled = false
            searchBar!!.menu.forEach { it.isEnabled = false }
            (activity as MainActivity).disableBottomNavigation()
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
                    val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        if (selectedObjects?.size == resultsList?.size){
                            resultViewModel.deleteAll()
                        }else{
                            resultViewModel.deleteSelected(selectedObjects!!.toList())
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.download -> {
                    lifecycleScope.launch {
                        val downloadList = withContext(Dispatchers.IO){
                            downloadViewModel.turnResultItemsToDownloadItems(selectedObjects!!)
                        }
                        clearCheckedItems()
                        actionMode?.finish()

                        if (sharedPreferences!!.getBoolean("download_card", true)) {
                            val bottomSheet = DownloadMultipleBottomSheetDialog(selectedObjects!!, downloadList.toMutableList())
                            bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                        } else {
                            downloadViewModel.queueDownloads(downloadList)
                        }
                    }
                    true
                }
                R.id.select_all -> {
                    homeAdapter?.checkAll(resultsList)
                    selectedObjects?.clear()
                    resultsList?.forEach { selectedObjects?.add(it!!) }
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    homeAdapter?.invertSelected(resultsList)
                    val invertedList = arrayListOf<ResultItem>()
                    resultsList?.forEach {
                        if (!selectedObjects?.contains(it)!!) invertedList.add(it!!)
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
            (activity as MainActivity).enableBottomNavigation()
            clearCheckedItems()
            searchBar!!.isEnabled = true
            searchBar!!.menu.forEach { it.isEnabled = true }
            searchBar?.expand(appBarLayout!!)
        }
    }

    private fun clearCheckedItems(){
        homeAdapter?.clearCheckedItems()
        selectedObjects?.forEach {
            homeAdapter?.notifyItemChanged(resultsList!!.indexOf(it))
        }
        selectedObjects?.clear()
    }


    override fun onStop() {
        actionMode?.finish()
        super.onStop()
    }


    companion object {
        private const val TAG = "HomeFragment"
    }
}