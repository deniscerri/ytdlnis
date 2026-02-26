package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.databinding.ActivitySettingsBinding
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.more.settings.search.SettingsSearchAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.lazy


class SettingsActivity : BaseActivity(), SettingHost {
    var context: Context? = null
    private lateinit var settingViewModel: SettingsViewModel
    private lateinit var navController: NavController
    private lateinit var searchAdapter: SettingsSearchAdapter

    override fun findPref(key: String): Preference? {
        return settingViewModel.settingsFlow.value.find { it.preference.key == key }?.preference
    }
    @SuppressLint("NotifyDataSetChanged")
    override fun refreshUI() {
        settingViewModel.indexSearchSettings()
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
        settingViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()

        val listener =
            NavController.OnDestinationChangedListener { controller, destination, arguments ->
                if (destination.id == R.id.mainSettingsFragment) {
                    changeTopAppbarTitle(getString(R.string.settings), false)
                    settingViewModel.indexSearchSettings()
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
            } else {
                navController.navigateUp()
            }
        }

        if (savedInstanceState == null) navController.navigate(R.id.mainSettingsFragment)

        //setup search
        val searchBar = binding.searchBar
        val searchView = binding.searchView
        searchView.setupWithSearchBar(searchBar)

        settingViewModel.indexSearchSettings()

        searchAdapter = SettingsSearchAdapter(emptyList(), this)
        binding.searchSuggestionsRecycler.layoutManager = LinearLayoutManager(context)
        binding.searchSuggestionsRecycler.adapter = searchAdapter
        binding.searchSuggestionsRecycler.itemAnimator = null

        binding.searchView.editText.addTextChangedListener { text ->
            settingViewModel.setSearchQuery(text.toString())
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingViewModel.settingsFlow.collect { allItems ->
                    allItems.forEach { item ->
                        if (!item.isHeader) {
                            item.module?.bindLogic(item.preference, this@SettingsActivity)
                        }
                    }
                    val visibleItems = allItems.filter { it.preference.isVisible }
                    searchAdapter.updateList(visibleItems)

                    val savedSearch = intent.getStringExtra("search_query")
                    if (!savedSearch.isNullOrBlank()) {
                        binding.searchBar.performClick()
                        intent.removeExtra("search_query")
                        settingViewModel.setSearchQuery(savedSearch)
                    }
                }
            }
        }
    }

    override fun onResume() {
        refreshUI()
        super.onResume()
    }

    fun closeSearchView() {
        binding.searchView.hide()
    }

    fun changeTopAppbarTitle(text: String, hideSearch: Boolean = true) {
        if (this::binding.isInitialized) binding.collapsingToolbar.title = text
        binding.searchBar.isVisible = !hideSearch
        binding.settingsToolbar.menu.findItem(R.id.search)?.isVisible =
            !binding.searchBar.isVisible && !hideSearch
    }
}