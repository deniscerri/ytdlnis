package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.databinding.ActivitySettingsBinding
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.more.settings.search.SettingsSearchAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.lazy


class SettingsActivity : BaseActivity(), SettingHost {
    var context: Context? = null
    private lateinit var navController: NavController
    private lateinit var searchAdapter: SettingsSearchAdapter
    private var allIndexedItems = listOf<SearchSettingsItem>()

    override fun findPref(key: String): Preference? {
        return allIndexedItems.find { it.preference.key == key }?.preference
    }
    @SuppressLint("NotifyDataSetChanged")
    override fun refreshUI() {
        binding.searchSuggestionsRecycler.post {
            if (!isFinishing && !isDestroyed) {
                searchAdapter.notifyDataSetChanged()
            }
        }
    }
    override fun getHostContext() = this
    override val activityResultDelegate = PreferenceActivityResultDelegate(this)
    override val hostViewModelStoreOwner by lazy {
        this
    }
    override val hostLifecycleOwner by lazy {
        this
    }
    override val hostView: View? by lazy {
        this.findViewById<View>(android.R.id.content)
    }
    override fun requestGetParentFragmentManager() = supportFragmentManager
    override fun requestRecreateActivity() = this.recreate()
    override fun requestNavigate(id: Int) {
        closeSearchView()
        navController.navigate(id)
    }

    private val xmlToNavId = mapOf(
        R.xml.general_preferences to R.id.appearanceSettingsFragment,
        R.xml.folders_preference to R.id.folderSettingsFragment,
        R.xml.downloading_preferences to R.id.downloadSettingsFragment,
        R.xml.processing_preferences to R.id.processingSettingsFragment,
        R.xml.updating_preferences to R.id.updateSettingsFragment,
        R.xml.advanced_preferences to R.id.advancedSettingsFragment,
    )
    fun getDestinationIdForXml(xmlRes: Int): Int = xmlToNavId[xmlRes] ?: R.id.mainSettingsFragment

    lateinit var binding: ActivitySettingsBinding
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = baseContext
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()

        val listener = NavController.OnDestinationChangedListener { controller, destination, arguments ->
            if (destination.id == R.id.mainSettingsFragment){
                changeTopAppbarTitle(getString(R.string.settings), false)
                indexSettings()
            }
        }

        navController.addOnDestinationChangedListener(listener)
        binding.settingsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.searchView.isShowing) {
                binding.searchView.hide()
            } else if (navController.currentDestination?.id == R.id.mainSettingsFragment) {
                navController.popBackStack()
                finishAndRemoveTask()
            }else{
                navController.navigateUp()
            }
        }

        if (savedInstanceState == null) navController.navigate(R.id.mainSettingsFragment)

        //setup search
        val appBar = binding.appBar
        val searchBar = binding.searchBar
        val toolbar = binding.settingsToolbar
        val searchView = binding.searchView
        searchView.setupWithSearchBar(searchBar)

        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (binding.collapsingToolbar.title != getString(R.string.settings)) return@addOnOffsetChangedListener

            val totalScrollRange = appBarLayout.totalScrollRange
            if (totalScrollRange == 0) return@addOnOffsetChangedListener

            val percentage = Math.abs(verticalOffset).toFloat() / totalScrollRange
            val isCollapsed = percentage > 0.8f

            // 1. Handle Menu Icon Visibility
            toolbar.menu.findItem(R.id.search)?.isVisible = isCollapsed

            // 2. Handle SearchBar
            when {
                percentage >= 1f -> {
                    // Fully collapsed — safe to GONE now, jump isn't visible
                    if (searchBar.visibility != View.GONE) searchBar.visibility = View.GONE
                }
                percentage > 0.8f -> {
                    // Mid-collapse — hide visually but keep space to avoid jump
                    searchBar.visibility = View.INVISIBLE
                    searchBar.alpha = 0f
                }
                else -> {
                    // Expanding — restore and fade in/out
                    if (searchBar.visibility != View.VISIBLE) searchBar.visibility = View.VISIBLE
                    searchBar.alpha = 1f - (percentage * 1.2f).coerceAtMost(1f)
                }
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.search) {
                searchView.show()
                true
            } else false
        }
        indexSettings {
            val savedSearch = intent.getStringExtra("search_query")
            if (!savedSearch.isNullOrBlank()) {
                binding.searchBar.performClick()
                filterSettings(savedSearch)
            }
        }

        searchAdapter = SettingsSearchAdapter(emptyList(), this)
        binding.searchSuggestionsRecycler.layoutManager = LinearLayoutManager(context)
        binding.searchSuggestionsRecycler.adapter = searchAdapter
        binding.searchSuggestionsRecycler.itemAnimator = null

        binding.searchView.editText.addTextChangedListener { text ->
            filterSettings(text.toString())
        }
    }

    override fun onResume() {
        refreshUI()
        super.onResume()
    }

    private fun indexSettings(cb: (() -> Unit)? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val indexedItems = SettingsRegistry.indexAll(this@SettingsActivity)
            withContext(Dispatchers.Main) {
                allIndexedItems = indexedItems
                allIndexedItems.forEach { item ->
                    if (!item.isHeader) {
                        item.module?.bindLogic(item.preference, this@SettingsActivity)
                    }
                    cb?.invoke()
                }
            }
        }
    }

    private fun filterSettings(query: String) {
        intent.putExtra("search_query", query)
        if (query.isBlank()) {
            searchAdapter.updateList(emptyList())
            return
        }

        val filtered = allIndexedItems.filter { item ->
            val titleMatch = item.preference.title?.toString()?.contains(query, ignoreCase = true) == true
            val summaryMatch = item.preference.summary?.toString()?.contains(query, ignoreCase = true) == true
            val groupMatch = item.groupTitle?.contains(query, ignoreCase = true) == true
            item.preference.isVisible && (titleMatch || summaryMatch || groupMatch)
        }

        searchAdapter.updateList(filtered)
    }

    fun closeSearchView() {
        binding.searchView.hide()
    }

    fun changeTopAppbarTitle(text: String, hideSearch: Boolean = true) {
        if (this::binding.isInitialized) binding.collapsingToolbar.title = text
        binding.searchBar.isVisible = !hideSearch
        binding.settingsToolbar.menu.findItem(R.id.search)?.isVisible = !binding.searchBar.isVisible && !hideSearch
    }
}