package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.LayoutDirection
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.text.layoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.BackupSettingsItem
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.RestoreAppDataItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.models.SearchHistoryItem
import com.deniscerri.ytdl.database.models.TemplateShortcut
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.adapter.SortableTextItemAdapter
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.deniscerri.ytdl.work.MoveCacheFilesWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

// Main settings screen – shows top‑level categories and handles global search across all settings.
// When searching, we clone preferences from other fragments and display them here,
// complete with live dependency updates (e.g. toggling a switch enables/disables related prefs).
class MainSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.settings

    // Top‑level category preferences
    private var appearance: Preference? = null
    private var folders: Preference? = null
    private var downloading: Preference? = null
    private var processing: Preference? = null
    private var updating: Preference? = null
    private var advanced: Preference? = null

    // Backup / restore preferences
    private var backup : Preference? = null
    private var restore : Preference? = null
    private var backupPath : Preference? = null

    private var updateUtil: UpdateUtil? = null

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var searchManager: SettingsSearchManager
    private var downloadViewModel: DownloadViewModel? = null
    private var ytdlpViewModel: YTDLPViewModel? = null

    // Registry of all clones built during a search pass, keyed by preference key.
    // Used to wire live dependency updates (e.g. toggling a switch enables/disables dependents).
    private val clonedPrefsRegistry = mutableMapOf<String, Preference>()

    // Track the currently-visible snackbar so we can dismiss it before any activity recreation
    // (theme/accent changes) to prevent the "Go →" action firing on a dead fragment.
    private var activeSnackbar: Snackbar? = null

    // Categories that appear during search (each contains cloned preferences from one sub‑screen)
    private val searchCategories = mutableMapOf<String, PreferenceCategory>()
    private var isSearchMode = false
    private var lastSearchQuery: String = ""

    // Maps category keys to the corresponding XML resource and title string
    private val categoryFragmentMap = mapOf(
        "appearance" to R.xml.general_preferences,
        "folders" to R.xml.folders_preference,
        "downloading" to R.xml.downloading_preferences,
        "processing" to R.xml.processing_preferences,
        "updating" to R.xml.updating_preferences,
        "advanced" to R.xml.advanced_preferences
    )

    private val categoryTitles = mapOf(
        "appearance" to R.string.general,
        "folders" to R.string.directories,
        "downloading" to R.string.downloads,
        "processing" to R.string.processing,
        "updating" to R.string.updating,
        "advanced" to R.string.advanced
    )
    
    // Navigation actions to open the real fragment for each category
    private val categoryNavigationActions = mapOf(
        "appearance" to R.id.action_mainSettingsFragment_to_appearanceSettingsFragment,
        "folders" to R.id.action_mainSettingsFragment_to_folderSettingsFragment,
        "downloading" to R.id.action_mainSettingsFragment_to_downloadSettingsFragment,
        "processing" to R.id.action_mainSettingsFragment_to_processingSettingsFragment,
        "updating" to R.id.action_mainSettingsFragment_to_updateSettingsFragment,
        "advanced" to R.id.action_mainSettingsFragment_to_advancedSettingsFragment
    )

    // Folder path keys that need live picker support in search results
    private val folderPathKeys = setOf("music_path", "video_path", "command_path", "cache_path")

    // Keys that should never appear in search (hidden/internal prefs)
    private val searchExcludedKeys = setOf(
        "start_destination",                // hidden duplicate of navigation_bar
        "last_used_download_type",          // internal hidden key
        "update_ytdlp_while_downloading",   // hidden
        "skip_updates",                     // hidden internal
        "useragent_header"                  // hidden internal
    )

    // Track which folder key is currently being picked so the result knows where to save
    private var pendingFolderPickKey: String? = null

    // Launcher for folder picker (ACTION_OPEN_DOCUMENT_TREE)
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val key = pendingFolderPickKey ?: return@registerForActivityResult
        pendingFolderPickKey = null
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            requireActivity().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putString(key, uri.toString()).apply()
        }
    }

    // Helper data class to keep preferences in a hierarchy during search
    private data class HierarchicalPreference(
        val preference: Preference,
        val parent: PreferenceGroup?,
        val depth: Int = 0,
        val isParent: Boolean = false
    )

    companion object {
        const val ARG_HIGHLIGHT_KEY = "highlight_preference_key"
        const val ARG_RETURN_TO_SEARCH = "return_to_search"
    }
    
    // Called when the user presses back while in search mode.
    // Returns true if the back press was handled (search exited).
    fun handleBackPressed(): Boolean {
        if (isSearchMode) {
            restoreNormalView()
            return true
        }
        return false
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        buildPreferenceList(preferenceScreen)

        val navController = findNavController()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()

        // Find top‑level category preferences
        appearance = findPreference("appearance")
        folders = findPreference("folders")
        downloading = findPreference("downloading")
        processing = findPreference("processing")
        updating = findPreference("updating")
        advanced = findPreference("advanced")

        // Build summaries for each category (list of key features)
        val separator = if (Locale(preferences.getString("app_language", "en")!!).layoutDirection == LayoutDirection.RTL) "،" else ","
        appearance?.summary = "${if (Build.VERSION.SDK_INT < 33) getString(R.string.language) + "$separator " else ""}${getString(R.string.Theme)}$separator ${getString(R.string.accents)}$separator ${getString(R.string.preferred_search_engine)}"
        appearance?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_appearanceSettingsFragment)
            true
        }

        folders?.summary = "${getString(R.string.music_directory)}$separator ${getString(R.string.video_directory)}$separator ${getString(R.string.command_directory)}"
        folders?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_folderSettingsFragment)
            true
        }

        downloading?.summary = "${getString(R.string.quick_download)}$separator ${getString(R.string.concurrent_downloads)}$separator ${getString(R.string.limit_rate)}"
        downloading?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_downloadSettingsFragment)
            true
        }

        processing?.summary = "${getString(R.string.sponsorblock)}$separator ${getString(R.string.embed_subtitles)}$separator ${getString(R.string.add_chapters)}"
        processing?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_processingSettingsFragment)
            true
        }

        updating?.summary = "${getString(R.string.update_ytdl)}$separator ${getString(R.string.update_app)}"
        updating?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_updateSettingsFragment)
            true
        }

        advanced?.summary = "PO Token$separator ${getString(R.string.other_youtube_extractor_args)}"
        advanced?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_advancedSettingsFragment)
            true
        }

        updateUtil = UpdateUtil(requireContext())
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]

        // Observe downloads only for the purpose of disabling cache cleanup when downloads are active
        // (used in clonePreference for the "clear_cache" action)
        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){ /* unused, but kept for future */ }

        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        
        // Initialize the search manager (caches all preferences for fast searching)
        searchManager = SettingsSearchManager(this, categoryFragmentMap, categoryTitles)
        searchManager.initializeCache()

        backup = findPreference("backup")
        restore = findPreference("restore")
        backupPath = findPreference("backup_path")

        // Package name – just shows the app ID, copies to clipboard on click
        findPreference<Preference>("package_name")?.apply {
            summary = BuildConfig.APPLICATION_ID
            setOnPreferenceClickListener {
                UiUtil.copyToClipboard(summary.toString(), requireActivity())
                true
            }
        }

        // Backup: multi‑choice dialog to select categories, then perform backup
        backup!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(getString(R.string.select_backup_categories))
                val values = resources.getStringArray(R.array.backup_category_values)
                val entries = resources.getStringArray(R.array.backup_category_entries)
                val checkedItems : ArrayList<Boolean> = arrayListOf()
                values.forEach { _ ->
                    checkedItems.add(true)
                }

                builder.setMultiChoiceItems(
                    entries,
                    checkedItems.toBooleanArray()
                ) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }

                builder.setPositiveButton(
                    getString(R.string.ok)
                ) { _: DialogInterface?, _: Int ->
                    lifecycleScope.launch {
                        if (checkedItems.all { !it }){
                            Snackbar.make(requireView(), R.string.select_backup_categories, Snackbar.LENGTH_SHORT).show()
                            return@launch
                        }

                        val selectedItems = values.mapIndexed { idx, it -> Pair<String, Boolean>(it, checkedItems[idx]) }.filter { it.second }.map { it.first }
                        val pathResult = withContext(Dispatchers.IO){
                            settingsViewModel.backup(selectedItems)
                        }

                        if (pathResult.isFailure) {
                            val errorMessage = pathResult.exceptionOrNull()?.message ?: requireContext().getString(R.string.errored)
                            val snack = Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG)
                            val snackbarView: View = snack.view
                            val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                            snackTextView.maxLines = 9999999
                            snack.setAction(android.R.string.copy){
                                UiUtil.copyToClipboard(errorMessage ?: requireContext().getString(R.string.errored), requireActivity())
                            }
                            snack.show()
                        }else {
                            val s = Snackbar.make(
                                requireView(),
                                getString(R.string.backup_created_successfully),
                                Snackbar.LENGTH_LONG
                            )
                            s.setAction(R.string.Open_File) {
                                FileUtil.openFileIntent(requireActivity(), pathResult.getOrNull()!!)
                            }
                            s.show()
                        }
                    }
                }

                builder.setNegativeButton(
                    getString(R.string.cancel)
                ) { _: DialogInterface?, _: Int -> }

                val dialog = builder.create()
                dialog.show()
                true
            }

        // Restore: open document picker, parse JSON, show confirmation dialog
        restore!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                appRestoreResultLauncher.launch(intent)
                true
            }

        // Backup path – opens a folder picker
        backupPath?.apply {
            summary = FileUtil.formatPath(FileUtil.getBackupPath(requireContext()))
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                backupPathResultLauncher.launch(intent)
                true
            }
        }
    }

    // Called from SettingsActivity when the search text changes
    override fun filterPreferences(query: String) {
        if (query.isBlank()) {
            restoreNormalView()
            return
        }

        lastSearchQuery = query

        // Use the search manager to get smart‑matched results, then build the search UI
        searchManager.searchWithDebounce(query) { smartMatches ->
            enterSearchModeEnhanced(query.lowercase(), smartMatches)
        }
    }
    
    // Build the search result screen using both simple text matching and smart scoring.
    // Hides the original categories and creates new ones with cloned preferences.
    private fun enterSearchModeEnhanced(query: String, smartMatches: List<SearchMatch>) {
        isSearchMode = true
        
        // Hide all top‑level category cards
        appearance?.isVisible = false
        folders?.isVisible = false
        downloading?.isVisible = false
        processing?.isVisible = false
        updating?.isVisible = false
        advanced?.isVisible = false
        
        // Remove any previously built search categories
        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()
        clonedPrefsRegistry.clear()

        // For each settings category, find preferences that match the query
        categoryFragmentMap.forEach { (categoryKey, xmlRes) ->
            val hierarchicalResults = findMatchingPreferencesWithHierarchy(xmlRes, query)
            
            if (hierarchicalResults.isNotEmpty()) {
                val mainCategory = PreferenceCategory(requireContext()).apply {
                    title = getString(categoryTitles[categoryKey] ?: R.string.settings)
                    key = "search_main_$categoryKey"
                }
                
                preferenceScreen.addPreference(mainCategory)
                
                // Sort results using smart scoring if available
                val sortedResults = sortWithSmartScoring(hierarchicalResults, smartMatches, categoryKey)
                buildHierarchicalPreferences(sortedResults, mainCategory, categoryKey, query)
                
                searchCategories[categoryKey] = mainCategory
            }
        }

        // Wire live dependency updates between cloned prefs now that all clones are registered
        wireLiveDependencies()

        super.filterPreferences(query)
        hideEmptyCategoriesInMain()
    }
    
    // Sort hierarchical results using the score from smart matches.
    // Preferences with a higher score appear first.
    private fun sortWithSmartScoring(
        results: List<HierarchicalPreference>,
        smartMatches: List<SearchMatch>,
        categoryKey: String
    ): List<HierarchicalPreference> {
        val scoreMap = smartMatches
            .filter { it.data.categoryKey == categoryKey }
            .associateBy({ it.data.key }, { it.score })
        
        return results.sortedByDescending { hierPref ->
            scoreMap[hierPref.preference.key] ?: 0f
        }
    }

    // Restore the normal settings view (hide all search‑generated categories,
    // show the original top‑level cards).
    private fun restoreNormalView() {
        isSearchMode = false
        lastSearchQuery = ""
        searchManager.cancelPendingSearch()

        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()
        clonedPrefsRegistry.clear()

        restoreAllPreferences()
        appearance?.isVisible = true
        folders?.isVisible = true
        downloading?.isVisible = true
        processing?.isVisible = true
        updating?.isVisible = true
        advanced?.isVisible = true
        
        findPreference<PreferenceCategory>("backup_restore")?.isVisible = true
    }

    // When we navigate to a sub‑fragment from search (changelog, player client, PO tokens)
    // we intentionally do NOT call restoreNormalView() so the search state is preserved.
    // On returning to this fragment, onResume re‑applies the last query so results are rebuilt.
    override fun onResume() {
        super.onResume()
        val query = lastSearchQuery
        if (query.isNotBlank()) {
            // Re‑enter search mode so the results are fresh (e.g. version string may have updated,
            // or the user may have changed a setting in the sub‑fragment we just returned from).
            filterPreferences(query)
        }
    }

    // Wires live enabled‑state propagation between cloned switch prefs and their dependents.
    // Called once after all clones for a search pass are registered in [clonedPrefsRegistry].
    //
    // Known pairs:
    //   use_scheduler          → schedule_start / schedule_end
    //   use_format_sorting     → format_importance_video / format_importance_audio
    //   use_sponsorblock       → sponsorblock_filters / sponsorblock_url
    //   embed_thumbnail        → crop_thumbnail
    //   no_part                → keep_cache
    //   aria2                  → concurrent_fragments (inverted: when aria2 is ON, dependent is enabled)
    //   compatible_video       → audio_codec / video_codec / video_format
    private fun wireLiveDependencies() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pairs = mapOf(
            "use_scheduler"     to listOf("schedule_start", "schedule_end"),
            "use_format_sorting" to listOf("format_importance_video", "format_importance_audio"),
            "use_sponsorblock"  to listOf("sponsorblock_filters", "sponsorblock_url"),
            "embed_thumbnail"   to listOf("crop_thumbnail"),
            "no_part"           to listOf("keep_cache"),
            "aria2"             to listOf("concurrent_fragments"),   // inverted: disable dependents when OFF
            "compatible_video"  to listOf("audio_codec", "video_codec", "video_format")
        )
        val invertedDeps = setOf("aria2", "compatible_video")  // these disable dependents when ON rather than OFF

        pairs.forEach { (parentKey, dependentKeys) ->
            val parentClone = clonedPrefsRegistry[parentKey] as? androidx.preference.SwitchPreferenceCompat
                ?: clonedPrefsRegistry[parentKey] as? androidx.preference.SwitchPreference

            dependentKeys.forEach { depKey ->
                val depClone = clonedPrefsRegistry[depKey] ?: return@forEach
                
                // Set initial enabled state
                // If parent is in search results, use its checked state
                // If parent is not in search results, check SharedPreferences
                val parentIsChecked = if (parentClone != null) {
                    when (parentClone) {
                        is androidx.preference.SwitchPreferenceCompat -> parentClone.isChecked
                        is androidx.preference.SwitchPreference -> parentClone.isChecked
                        else -> false
                    }
                } else {
                    // Parent not in search results, check actual saved value
                    sharedPrefs.getBoolean(parentKey, false)
                }
                depClone.isEnabled = if (parentKey in invertedDeps) !parentIsChecked else parentIsChecked

                // Only set up live propagation if parent is present in search results
                if (parentClone != null) {
                    val existing = parentClone.onPreferenceChangeListener
                    parentClone.setOnPreferenceChangeListener { pref, newValue ->
                        val isNowChecked = newValue as? Boolean ?: false
                        depClone.isEnabled = if (parentKey in invertedDeps) !isNowChecked else isNowChecked
                        existing?.onPreferenceChange(pref, newValue) ?: true
                    }
                }
            }
        }
    }

    // Scan the XML for a given category and collect all preferences (including those inside groups)
    // that match the query, preserving hierarchy.
    private fun findMatchingPreferencesWithHierarchy(xmlRes: Int, query: String): List<HierarchicalPreference> {
        val results = mutableListOf<HierarchicalPreference>()
        
        try {
            val preferenceManager = PreferenceManager(requireContext())
            val tempScreen = preferenceManager.inflateFromResource(requireContext(), xmlRes, null)
            collectMatchingWithHierarchy(tempScreen, query, results, null, 0)
        } catch (e: Exception) {
            Log.e("MainSettings", "Error loading preferences from XML", e)
        }
        
        return results
    }

    // Recursively collect preferences that match the query, preserving parent‑child relationships.
    private fun collectMatchingWithHierarchy(
        group: PreferenceGroup,
        query: String,
        results: MutableList<HierarchicalPreference>,
        parent: PreferenceGroup?,
        depth: Int
    ) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)

            // Skip internally‑hidden / duplicate preferences that should never surface in search.
            // We do NOT use pref.isVisible here because some valid prefs (e.g. display_over_apps)
            // are set invisible in XML but should still be searchable.
            val prefKey = pref.key ?: ""
            if (prefKey in searchExcludedKeys) continue

            val title = pref.title?.toString() ?: ""
            val summary = pref.summary?.toString() ?: ""
            val key = prefKey

            val matches = title.lowercase().contains(query) ||
                    summary.lowercase().contains(query) ||
                    key.lowercase().contains(query)

            if (pref is PreferenceGroup) {
                val childMatches = mutableListOf<HierarchicalPreference>()
                collectMatchingWithHierarchy(pref, query, childMatches, pref, depth + 1)
                
                if (childMatches.isNotEmpty()) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = true))
                    results.addAll(childMatches)
                } else if (matches) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = true))
                }
            } else {
                if (matches) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = false))
                }
            }
        }
    }

    // Build the actual preference hierarchy in the search screen.
    // Groups (PreferenceCategory) are created as needed, and clones are added under them.
    private fun buildHierarchicalPreferences(
        hierarchicalResults: List<HierarchicalPreference>,
        mainCategory: PreferenceCategory,
        categoryKey: String,
        query: String
    ) {
        var currentParentCategory: PreferenceCategory? = null
        var currentParentHasChildren = false
        
        hierarchicalResults.forEach { hierPref ->
            val pref = hierPref.preference
            
            if (pref is PreferenceCategory) {
                // Start a new sub‑category
                currentParentCategory = PreferenceCategory(requireContext()).apply {
                    title = pref.title
                    key = "search_sub_${pref.key}_${categoryKey}"
                }
                currentParentHasChildren = false
                
            } else {
                // Clone the actual preference
                val clonedPref = clonePreference(pref, categoryKey)
                if (clonedPref.key.isNotBlank()) {
                    clonedPrefsRegistry[clonedPref.key] = clonedPref
                }
                
                // If we have a current parent, add it to the main category first (if not already added)
                if (currentParentCategory != null && !currentParentHasChildren) {
                    mainCategory.addPreference(currentParentCategory!!)
                    currentParentHasChildren = true
                }
                
                if (currentParentCategory != null) {
                    currentParentCategory!!.addPreference(clonedPref)
                } else {
                    mainCategory.addPreference(clonedPref)
                }
            }
        }
    }

    // Create a copy of the given preference that behaves like the original but is independent.
    // Handles all preference types and attaches custom click listeners where needed.
    //
    // @param original    The preference from the original XML
    // @param categoryKey The category this preference belongs to (used for navigation)
    private fun clonePreference(original: Preference, categoryKey: String): Preference {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val key = original.key ?: ""

        val cloned: Preference = when (original) {
            is androidx.preference.SwitchPreferenceCompat -> {
                androidx.preference.SwitchPreferenceCompat(requireContext()).also { sw ->
                    // display_over_apps: real state comes from system, not SharedPreferences
                    sw.isChecked = when (key) {
                        "display_over_apps" -> Settings.canDrawOverlays(requireContext())
                        else -> sharedPrefs.getBoolean(key, false)
                    }
                    sw.summaryOn = original.summaryOn
                    sw.summaryOff = original.summaryOff
                    
                    when (key) {
                        "display_over_apps" -> {
                            sw.setOnPreferenceChangeListener { _, _ ->
                                runCatching {
                                    val i = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + requireContext().packageName)
                                    )
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(i)
                                }
                                false // don't persist; real state is from system
                            }
                        }
                        "high_contrast" -> {
                            sw.setOnPreferenceChangeListener { _, _ ->
                                activeSnackbar?.dismiss()
                                activeSnackbar = null
                                ThemeUtil.updateThemes()
                                true
                            }
                        }
                        "label_visibility" -> {
                            // Hide on devices that use side navigation
                            sw.isVisible = !resources.getBoolean(R.bool.uses_side_nav)
                        }
                        "compatible_video" -> {
                            val existingListener = sw.onPreferenceChangeListener
                            sw.setOnPreferenceChangeListener { pref, newValue ->
                                val newBool = newValue as Boolean
                                val editor = sharedPrefs.edit()
                                if (newBool) {
                                    // Turn off recode_video if it's on
                                    val recodeClone = clonedPrefsRegistry["recode_video"] as? androidx.preference.SwitchPreferenceCompat
                                    if (recodeClone?.isChecked == true) {
                                        recodeClone.performClick()
                                    }
                                    // Save current codec values
                                    val audioClone = clonedPrefsRegistry["audio_codec"] as? androidx.preference.ListPreference
                                    val videoClone = clonedPrefsRegistry["video_codec"] as? androidx.preference.ListPreference
                                    val formatClone = clonedPrefsRegistry["video_format"] as? androidx.preference.ListPreference
                                    editor.putString("audio_codec_tmp", audioClone?.value ?: "")
                                    editor.putString("video_codec_tmp", videoClone?.value ?: "")
                                    editor.putString("video_format_tmp", formatClone?.value ?: "")
                                    // Set forced values
                                    val audioCodecs = resources.getStringArray(R.array.audio_codec)
                                    val audioCodecValues = resources.getStringArray(R.array.audio_codec_values)
                                    val videoCodecs = resources.getStringArray(R.array.video_codec)
                                    val videoCodecValues = resources.getStringArray(R.array.video_codec_values)
                                    val newAudioCodec = "M4A"
                                    val newVideoCodec = "AVC (H264)"
                                    val newAudioValue = audioCodecValues[audioCodecs.indexOf(newAudioCodec)]
                                    val newVideoValue = videoCodecValues[videoCodecs.indexOf(newVideoCodec)]
                                    editor.putString("audio_codec", newAudioValue)
                                    editor.putString("video_codec", newVideoValue)
                                    editor.putString("video_format", "")
                                    editor.apply()
                                    // Update clones' UI
                                    audioClone?.value = newAudioValue
                                    audioClone?.summary = audioCodecs[audioCodecValues.indexOf(newAudioValue)]
                                    videoClone?.value = newVideoValue
                                    videoClone?.summary = videoCodecs[videoCodecValues.indexOf(newVideoValue)]
                                    formatClone?.value = ""
                                    // Set summary based on the value
                                    val formatIdx = formatClone?.entryValues?.indexOf("") ?: -1
                                    formatClone?.summary = if (formatIdx >= 0) {
                                        formatClone.entries?.get(formatIdx)
                                    } else {
                                        null
                                    }
                                } else {
                                    // Restore from temp
                                    val savedAudio = sharedPrefs.getString("audio_codec_tmp", "")
                                    val savedVideo = sharedPrefs.getString("video_codec_tmp", "")
                                    val savedFormat = sharedPrefs.getString("video_format_tmp", "")
                                    editor.putString("audio_codec", savedAudio)
                                    editor.putString("video_codec", savedVideo)
                                    editor.putString("video_format", savedFormat)
                                    editor.apply()
                                    // Update clones
                                    val audioClone = clonedPrefsRegistry["audio_codec"] as? androidx.preference.ListPreference
                                    val videoClone = clonedPrefsRegistry["video_codec"] as? androidx.preference.ListPreference
                                    val formatClone = clonedPrefsRegistry["video_format"] as? androidx.preference.ListPreference
                                    if (savedAudio?.isNotBlank() == true) {
                                        audioClone?.value = savedAudio
                                        val audioIdx = audioClone?.entryValues?.indexOf(savedAudio) ?: -1
                                        audioClone?.summary = if (audioIdx >= 0) {
                                            audioClone.entries?.get(audioIdx)
                                        } else {
                                            null
                                        }
                                    }
                                    if (savedVideo?.isNotBlank() == true) {
                                        videoClone?.value = savedVideo
                                        val videoIdx = videoClone?.entryValues?.indexOf(savedVideo) ?: -1
                                        videoClone?.summary = if (videoIdx >= 0) {
                                            videoClone.entries?.get(videoIdx)
                                        } else {
                                            null
                                        }
                                    }
                                    // Always update format, even if empty
                                    formatClone?.value = savedFormat ?: ""
                                    val formatIdx = formatClone?.entryValues?.indexOf(savedFormat ?: "") ?: -1
                                    formatClone?.summary = if (formatIdx >= 0) {
                                        formatClone.entries?.get(formatIdx)
                                    } else {
                                        null
                                    }
                                }
                                // Call existing listener if any
                                existingListener?.onPreferenceChange(pref, newValue)
                                true
                            }
                        }
                        "recode_video" -> {
                            val existingListener = sw.onPreferenceChangeListener
                            sw.setOnPreferenceChangeListener { pref, newValue ->
                                val newBool = newValue as Boolean
                                if (newBool) {
                                    // If compatible_video is on, turn it off
                                    val compatClone = clonedPrefsRegistry["compatible_video"] as? androidx.preference.SwitchPreferenceCompat
                                    if (compatClone?.isChecked == true) {
                                        compatClone.performClick()
                                    }
                                }
                                existingListener?.onPreferenceChange(pref, newValue)
                                true
                            }
                        }
                    }
                }
            }
            is androidx.preference.SwitchPreference -> {
                androidx.preference.SwitchPreference(requireContext()).also { sw ->
                    sw.isChecked = sharedPrefs.getBoolean(key, false)
                    sw.summaryOn = original.summaryOn
                    sw.summaryOff = original.summaryOff
                }
            }
            is androidx.preference.CheckBoxPreference -> {
                androidx.preference.CheckBoxPreference(requireContext()).also { cb ->
                    cb.isChecked = sharedPrefs.getBoolean(key, false)
                    cb.summaryOn = original.summaryOn
                    cb.summaryOff = original.summaryOff
                }
            }
            is androidx.preference.ListPreference -> {
                androidx.preference.ListPreference(requireContext()).also { lp ->
                    lp.entries = original.entries
                    lp.entryValues = original.entryValues
                    val savedValue = sharedPrefs.getString(key, null)
                    lp.value = savedValue
                    val idx = lp.entryValues?.indexOf(savedValue) ?: -1
                    if (idx >= 0) lp.summary = lp.entries[idx] else lp.summary = original.summary

                    // label_visibility: hide on side‑nav devices
                    if (key == "label_visibility") {
                        lp.isVisible = !resources.getBoolean(R.bool.uses_side_nav)
                    }

                    lp.setOnPreferenceChangeListener { pref, newValue ->
                        val newIdx = lp.entryValues?.indexOf(newValue) ?: -1
                        if (newIdx >= 0) pref.summary = lp.entries[newIdx]
                        
                        when (key) {
                            "ytdlnis_theme" -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(getString(R.string.app_icon_change))
                                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                                        sharedPrefs.edit().putString(key, newValue as String).apply()
                                        activeSnackbar?.dismiss()
                                        activeSnackbar = null
                                        ThemeUtil.updateThemes()
                                    }
                                    .setNegativeButton(getString(R.string.cancel), null)
                                    .show()
                                return@setOnPreferenceChangeListener false
                            }
                            "theme_accent" -> {
                                sharedPrefs.edit().putString(key, newValue as String).apply()
                                activeSnackbar?.dismiss()
                                activeSnackbar = null
                                ThemeUtil.updateThemes()
                                return@setOnPreferenceChangeListener false
                            }
                            "ytdlnis_icon" -> {
                                sharedPrefs.edit().putString(key, newValue as String).apply()
                                activeSnackbar?.dismiss()
                                activeSnackbar = null
                                ThemeUtil.recreateAllActivities()
                                return@setOnPreferenceChangeListener false
                            }
                            "app_language" -> {
                                sharedPrefs.edit().putString(key, newValue as String).apply()
                                if (newValue == "system") {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(null))
                                } else {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue as String))
                                }
                                activeSnackbar?.dismiss()
                                activeSnackbar = null
                                ThemeUtil.updateThemes()
                                return@setOnPreferenceChangeListener false
                            }
                        }
                        
                        true // Normal preferences persist automatically
                    }
                }
            }
            is androidx.preference.MultiSelectListPreference -> {
                androidx.preference.MultiSelectListPreference(requireContext()).also { ml ->
                    ml.entries = original.entries
                    ml.entryValues = original.entryValues
                    ml.values = sharedPrefs.getStringSet(key, emptySet())
                }
            }
            is androidx.preference.EditTextPreference -> {
                androidx.preference.EditTextPreference(requireContext()).also { et ->
                    val savedText = sharedPrefs.getString(key, "")
                    et.text = savedText
                    et.summary = if (savedText.isNullOrEmpty()) original.summary else savedText
                    et.dialogTitle = original.dialogTitle
                    et.dialogMessage = original.dialogMessage
                    et.setOnPreferenceChangeListener { pref, newValue ->
                        pref.summary = (newValue as String).ifEmpty { original.summary }
                        true
                    }
                }
            }
            is androidx.preference.SeekBarPreference -> {
                androidx.preference.SeekBarPreference(requireContext()).also { sb ->
                    sb.min = original.min
                    sb.max = original.max
                    sb.seekBarIncrement = original.seekBarIncrement
                    sb.showSeekBarValue = original.showSeekBarValue
                    sb.value = sharedPrefs.getInt(key, original.min)
                }
            }
            else -> {
                // Fallback for plain Preference (usually just a title with a click action)
                Preference(requireContext()).also { p ->
                    if (key in folderPathKeys) {
                        // Folder picker – show current path and launch folder picker on click
                        val savedPath = sharedPrefs.getString(key, "")!!
                        if (savedPath.isNotEmpty()) {
                            p.summary = FileUtil.formatPath(savedPath)
                        }
                        p.setOnPreferenceClickListener {
                            if (key == "cache_path") {
                                UiUtil.showGenericConfirmDialog(
                                    requireContext(),
                                    getString(R.string.cache_directory),
                                    getString(R.string.cache_directory_warning)
                                ) {
                                    pendingFolderPickKey = key
                                    folderPickerLauncher.launch(
                                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                        }
                                    )
                                }
                            } else {
                                pendingFolderPickKey = key
                                folderPickerLauncher.launch(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                    }
                                )
                            }
                            true
                        }
                    } else when (key) {
                        "ignore_battery" -> {
                            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
                            val alreadyIgnored = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
                            if (alreadyIgnored) {
                                p.isVisible = false
                            } else {
                                p.setOnPreferenceClickListener {
                                    val intent = Intent()
                                    intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    intent.data = Uri.parse("package:" + requireContext().packageName)
                                    startActivity(intent)
                                    true
                                }
                            }
                        }
                        "reset_preferences" -> {
                            val xmlRes = categoryFragmentMap[categoryKey]
                            p.setOnPreferenceClickListener {
                                UiUtil.showGenericConfirmDialog(
                                    requireContext(),
                                    getString(R.string.reset),
                                    getString(R.string.reset_preferences_in_screen)
                                ) {
                                    if (xmlRes != null) {
                                        val editor = sharedPrefs.edit()
                                        val preferenceManager = PreferenceManager(requireContext())
                                        val tempScreen = preferenceManager.inflateFromResource(
                                            requireContext(), xmlRes, null
                                        )
                                        fun collectAll(group: PreferenceGroup) {
                                            for (i in 0 until group.preferenceCount) {
                                                val pref2 = group.getPreference(i)
                                                if (pref2 is PreferenceGroup) collectAll(pref2)
                                                else if (!pref2.key.isNullOrEmpty()) editor.remove(pref2.key)
                                            }
                                        }
                                        collectAll(tempScreen)
                                        editor.apply()
                                        PreferenceManager.setDefaultValues(requireActivity().applicationContext, xmlRes, true)
                                    }
                                }
                                true
                            }
                        }
                        "move_cache" -> {
                            p.setOnPreferenceClickListener {
                                val workRequest = OneTimeWorkRequestBuilder<MoveCacheFilesWorker>()
                                    .addTag("cacheFiles")
                                    .build()
                                WorkManager.getInstance(requireContext()).beginUniqueWork(
                                    System.currentTimeMillis().toString(),
                                    ExistingWorkPolicy.KEEP,
                                    workRequest
                                ).enqueue()
                                Snackbar.make(
                                    requireActivity().window.decorView,
                                    getString(R.string.move_temporary_files),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }
                        "clear_cache" -> {
                            val cacheDir = java.io.File(FileUtil.getCachePath(requireContext()))
                            var cacheSize = cacheDir.walkBottomUp().fold(0L) { acc, f -> acc + f.length() }
                            val sizeStr = if (cacheSize < 10000) "0B" else FileUtil.convertFileSize(cacheSize)
                            p.summary = "${getString(R.string.clear_temporary_files_summary)} ($sizeStr)"
                            p.setOnPreferenceClickListener {
                                lifecycleScope.launch {
                                    val activeCount = withContext(Dispatchers.IO) {
                                        downloadViewModel?.getActiveDownloadsCount() ?: 0
                                    }
                                    if (activeCount == 0) {
                                        withContext(Dispatchers.IO) {
                                            fun clearDir(folder: java.io.File) {
                                                if (folder.exists() && folder.isDirectory) {
                                                    folder.listFiles()?.forEach { file ->
                                                        if (file.isDirectory) { clearDir(file); file.delete() }
                                                        else file.delete()
                                                    }
                                                }
                                            }
                                            clearDir(cacheDir)
                                        }
                                        Snackbar.make(
                                            requireActivity().window.decorView,
                                            getString(R.string.cache_cleared),
                                            Snackbar.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Snackbar.make(
                                            requireActivity().window.decorView,
                                            getString(R.string.downloads_running_try_later),
                                            Snackbar.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                true
                            }
                        }
                        "file_name_template" -> {
                            val currentVal = sharedPrefs.getString(key, "%(uploader).30B - %(title).170B") ?: ""
                            p.summary = currentVal
                            p.setOnPreferenceClickListener {
                                UiUtil.showFilenameTemplateDialog(
                                    requireActivity(),
                                    sharedPrefs.getString("file_name_template", "%(uploader).30B - %(title).170B") ?: "",
                                    "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"
                                ) { newVal ->
                                    sharedPrefs.edit().putString("file_name_template", newVal).apply()
                                    p.summary = newVal
                                }
                                true
                            }
                        }
                        "file_name_template_audio" -> {
                            val currentVal = sharedPrefs.getString(key, "%(uploader).30B - %(title).170B") ?: ""
                            p.summary = currentVal
                            p.setOnPreferenceClickListener {
                                UiUtil.showFilenameTemplateDialog(
                                    requireActivity(),
                                    sharedPrefs.getString("file_name_template_audio", "%(uploader).30B - %(title).170B") ?: "",
                                    "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
                                ) { newVal ->
                                    sharedPrefs.edit().putString("file_name_template_audio", newVal).apply()
                                    p.summary = newVal
                                }
                                true
                            }
                        }
                        "audio_bitrate" -> {
                            val currentVal = sharedPrefs.getString("audio_bitrate", "") ?: ""
                            val entries = requireContext().resources.getStringArray(R.array.audio_bitrate)
                            val entryValues = requireContext().resources.getStringArray(R.array.audio_bitrate_values)
                            p.summary = if (currentVal.isNotBlank() && entryValues.contains(currentVal)) {
                                entries[entryValues.indexOf(currentVal)]
                            } else {
                                getString(R.string.defaultValue)
                            }
                            p.setOnPreferenceClickListener {
                                val cur = sharedPrefs.getString("audio_bitrate", "") ?: ""
                                UiUtil.showAudioBitrateDialog(requireActivity(), cur) { newVal ->
                                    sharedPrefs.edit().putString("audio_bitrate", newVal).apply()
                                    p.summary = if (newVal.isNotBlank() && entryValues.contains(newVal)) {
                                        entries[entryValues.indexOf(newVal)]
                                    } else {
                                        getString(R.string.defaultValue)
                                    }
                                }
                                true
                            }
                        }
                        "subs_lang" -> {
                            p.summary = sharedPrefs.getString("subs_lang", "en.*,.*-orig") ?: "en.*,.*-orig"
                            p.setOnPreferenceClickListener {
                                UiUtil.showSubtitleLanguagesDialog(
                                    requireActivity(),
                                    listOf(),
                                    sharedPrefs.getString("subs_lang", "en.*,.*-orig") ?: "en.*,.*-orig"
                                ) { newVal ->
                                    sharedPrefs.edit().putString("subs_lang", newVal).apply()
                                    p.summary = newVal
                                }
                                true
                            }
                        }
                        "update_ytdl" -> {
                            p.setOnPreferenceClickListener {
                                lifecycleScope.launch {
                                    Snackbar.make(
                                        requireActivity().window.decorView,
                                        getString(R.string.ytdl_updating_started),
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                    runCatching {
                                        val res = updateUtil!!.updateYTDL(null)
                                        when (res.status) {
                                            UpdateUtil.YTDLPUpdateStatus.DONE ->
                                                Snackbar.make(requireActivity().window.decorView, res.message, Snackbar.LENGTH_LONG).show()
                                            UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE ->
                                                Snackbar.make(requireActivity().window.decorView, getString(R.string.you_are_in_latest_version), Snackbar.LENGTH_LONG).show()
                                            else ->
                                                Snackbar.make(requireActivity().window.decorView, res.message, Snackbar.LENGTH_LONG).show()
                                        }
                                    }.onFailure { e ->
                                        Snackbar.make(
                                            requireActivity().window.decorView,
                                            e.message ?: getString(R.string.errored),
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                true
                            }
                        }
                        "ytdlp_source_label" -> {
                            p.summary = sharedPrefs.getString("ytdlp_source_label", "")!!
                                .ifEmpty { getString(R.string.update_ytdl_stable) }
                            p.setOnPreferenceClickListener {
                                UiUtil.showYTDLSourceBottomSheet(requireActivity(), sharedPrefs) { label, source ->
                                    sharedPrefs.edit()
                                        .putString("ytdlp_source", source)
                                        .putString("ytdlp_source_label", label)
                                        .apply()
                                    p.summary = label
                                }
                                true
                            }
                        }
                        "changelog" -> {
                            p.setOnPreferenceClickListener {
                                hideKeyboard()
                                findNavController().navigate(R.id.changeLogFragment)
                                true
                            }
                        }
                        "yt_player_client" -> {
                            p.summary = original.summary
                            p.setOnPreferenceClickListener {
                                hideKeyboard()
                                findNavController().navigate(R.id.youtubePlayerClientFragment)
                                true
                            }
                        }
                        "generate_po_tokens" -> {
                            p.summary = original.summary
                            p.setOnPreferenceClickListener {
                                hideKeyboard()
                                findNavController().navigate(R.id.generateYoutubePoTokensFragment)
                                true
                            }
                        }
                        "format_importance_audio", "format_importance_video" -> {
                            val isAudio = key == "format_importance_audio"
                            val arrayRes = if (isAudio) R.array.format_importance_audio else R.array.format_importance_video
                            val arrayValRes = if (isAudio) R.array.format_importance_audio_values else R.array.format_importance_video_values
                            val items = requireContext().resources.getStringArray(arrayRes)
                            val itemValues = requireContext().resources.getStringArray(arrayValRes).toSet()
                            val savedPref = sharedPrefs.getString(key, itemValues.joinToString(",")) ?: itemValues.joinToString(",")
                            p.summary = savedPref.split(",")
                                .mapIndexed { index, s -> "${index + 1}. ${items.getOrNull(itemValues.indexOf(s)) ?: s}" }
                                .joinToString("\n")
                            p.setOnPreferenceClickListener {
                                val pref2 = sharedPrefs.getString(key, itemValues.joinToString(",")) ?: itemValues.joinToString(",")
                                val prefArr = pref2.split(",")
                                val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                                    Pair(it, items[itemValues.indexOf(it)])
                                }.toMutableList()

                                val builder = MaterialAlertDialogBuilder(requireContext())
                                builder.setTitle(p.title)
                                val adapter = SortableTextItemAdapter(itms)
                                val itemTouchCallback = object : ItemTouchHelper.Callback() {
                                    override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                                        makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                                    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                                        val item = adapter.items[vh.absoluteAdapterPosition]
                                        adapter.items.remove(item)
                                        adapter.items.add(target.absoluteAdapterPosition, item)
                                        adapter.notifyItemMoved(vh.absoluteAdapterPosition, target.absoluteAdapterPosition)
                                        return true
                                    }
                                    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
                                }
                                val linear = LinearLayout(requireActivity())
                                linear.orientation = LinearLayout.VERTICAL
                                val note = TextView(requireActivity())
                                note.text = getString(R.string.format_importance_note)
                                note.textSize = 16f
                                note.setTypeface(note.typeface, Typeface.BOLD)
                                note.setPadding(20, 20, 20, 20)
                                linear.addView(note)
                                val recycler = RecyclerView(requireContext())
                                recycler.layoutManager = LinearLayoutManager(requireContext())
                                recycler.adapter = adapter
                                linear.addView(recycler)
                                ItemTouchHelper(itemTouchCallback).attachToRecyclerView(recycler)
                                builder.setView(linear)
                                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                                    val newPref = adapter.items.joinToString(",") { it.first }
                                    sharedPrefs.edit().putString(key, newPref).apply()
                                    p.summary = adapter.items.mapIndexed { index, pair -> "${index + 1}. ${pair.second}" }.joinToString("\n")
                                }
                                builder.setNegativeButton(getString(R.string.cancel), null)
                                builder.create().show()
                                true
                            }
                        }
                        "ytdl-version" -> {
                            val cached = sharedPrefs.getString("ytdl-version", "").orEmpty()
                            p.summary = if (cached.isNotBlank()) cached else getString(R.string.loading)
                            lifecycleScope.launch {
                                val version = withContext(Dispatchers.IO) {
                                    ytdlpViewModel?.getVersion(
                                        sharedPrefs.getString("ytdlp_source", "stable") ?: "stable"
                                    ).orEmpty()
                                }
                                if (version.isNotBlank()) {
                                    sharedPrefs.edit().putString("ytdl-version", version).apply()
                                    p.summary = version
                                }
                            }
                        }
                        "version" -> {
                            val nativeLibDir = requireContext().applicationInfo?.nativeLibraryDir
                            val abi = nativeLibDir?.split("/lib/")?.getOrNull(1) ?: ""
                            p.summary = "${BuildConfig.VERSION_NAME} ($abi)"
                            p.setOnPreferenceClickListener {
                                lifecycleScope.launch {
                                    val res = withContext(Dispatchers.IO) { updateUtil!!.tryGetNewVersion() }
                                    if (res.isFailure) {
                                        Snackbar.make(requireActivity().window.decorView, res.exceptionOrNull()?.message ?: getString(R.string.network_error), Snackbar.LENGTH_LONG).show()
                                    } else {
                                        UiUtil.showNewAppUpdateDialog(res.getOrNull()!!, requireActivity(), sharedPrefs)
                                    }
                                }
                                true
                            }
                        }
                        else -> {
                            p.setOnPreferenceClickListener {
                                navigateToPreferenceLocation(original.key, categoryKey)
                                true
                            }
                        }
                    }
                }
            }
        }

        // Copy shared visual properties
        cloned.key = key
        cloned.title = when (key) {
            "embed_thumbnail"         -> "${original.title} (${getString(R.string.audio)})"
            "video_embed_thumbnail"   -> "${original.title} (${getString(R.string.video)})"
            "format_importance_audio" -> "${getString(R.string.format_importance)} [${getString(R.string.audio)}]"
            "format_importance_video" -> "${getString(R.string.format_importance)} [${getString(R.string.video)}]"
            "file_name_template"      -> "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"
            "file_name_template_audio"-> "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
            else -> original.title
        }
        if (cloned.summary.isNullOrEmpty()) cloned.summary = original.summary
        cloned.icon = original.icon
        cloned.isEnabled = when (key) {
            "cache_downloads"         -> FileUtil.hasAllFilesAccess()
            "schedule_start",
            "schedule_end"            -> sharedPrefs.getBoolean("use_scheduler", false)
            "format_importance_video",
            "format_importance_audio" -> sharedPrefs.getBoolean("use_format_sorting", false)
            else -> original.isEnabled
        }
        cloned.isSelectable = true
        cloned.isPersistent = true

        val noSnackbarKeys = folderPathKeys + setOf("changelog", "yt_player_client", "generate_po_tokens")

        if (key !in noSnackbarKeys) {
            val innerListener = cloned.onPreferenceClickListener
            cloned.setOnPreferenceClickListener { pref ->
                showNavigationPrompt(original, categoryKey)
                innerListener?.onPreferenceClick(pref) ?: false
            }
        }

        return cloned
    }


    // Show a snackbar with the preference title and category, offering a "Go →" action
    // that navigates to the real settings screen for that preference.
    private fun showNavigationPrompt(pref: Preference, categoryKey: String) {
        if (!isAdded || view == null) return

        val categoryName = getString(categoryTitles[categoryKey] ?: R.string.settings)
        val snackbar = Snackbar.make(requireActivity().window.decorView, "${pref.title} • $categoryName", Snackbar.LENGTH_LONG)
            .setAction("Go →") {
                if (isAdded && view != null) {
                    navigateToPreferenceLocation(pref.key, categoryKey)
                }
            }

        activeSnackbar?.dismiss()
        activeSnackbar = snackbar
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (activeSnackbar === snackbar) activeSnackbar = null
            }
        })

        val snackbarView = snackbar.view

        val bgAttr = intArrayOf(com.google.android.material.R.attr.colorSurface)
        val bgTypedArray = requireContext().obtainStyledAttributes(bgAttr)
        val bgColor = bgTypedArray.getColor(0, 0xFF1C1C1E.toInt())
        bgTypedArray.recycle()

        val accentAttr = intArrayOf(com.google.android.material.R.attr.colorPrimary)
        val accentTypedArray = requireContext().obtainStyledAttributes(accentAttr)
        val accentColor = accentTypedArray.getColor(0, 0xFF6750A4.toInt())
        accentTypedArray.recycle()

        val textAttr = intArrayOf(com.google.android.material.R.attr.colorOnSurface)
        val textTypedArray = requireContext().obtainStyledAttributes(textAttr)
        val textColor = textTypedArray.getColor(0, 0xFFFFFFFF.toInt())
        textTypedArray.recycle()

        snackbarView.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)

        snackbar.setTextColor(textColor)
        snackbar.setActionTextColor(accentColor)

        val params = snackbarView.layoutParams as? android.widget.FrameLayout.LayoutParams
        if (params != null) {
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            val statusBarHeight = requireContext().resources.getDimensionPixelSize(
                requireContext().resources.getIdentifier("status_bar_height", "dimen", "android")
            ).coerceAtLeast(48)
            params.topMargin = statusBarHeight + 8
            snackbarView.layoutParams = params
        }
        snackbar.show()
    }
    
    // Navigate to the real settings screen that contains the given preference,
    // and pass a highlight argument so the preference is scrolled to and highlighted.
    private fun navigateToPreferenceLocation(prefKey: String?, categoryKey: String) {
        if (!isAdded || view == null) return
        try {
            (activity as? SettingsActivity)?.clearSearchFocus()
            
            hideKeyboard()
            
            restoreNormalView()
            
            val navController = findNavController()
            val action = categoryNavigationActions[categoryKey]
            
            if (action != null) {
                val bundle = Bundle().apply {
                    putString(ARG_HIGHLIGHT_KEY, prefKey)
                    putBoolean(ARG_RETURN_TO_SEARCH, false)
                }
                navController.navigate(action, bundle)
            }
        } catch (e: Exception) {
            Log.e("MainSettings", "Error navigating to preference location", e)
            Toast.makeText(requireContext(), "Navigation failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideKeyboard() {
        val activity = requireActivity()
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        activity.currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // Hide the backup/restore category if it has no visible children after search filtering
    private fun hideEmptyCategoriesInMain() {
        findPreference<PreferenceCategory>("backup_restore")?.let { category ->
            val hasVisibleChildren = (0 until category.preferenceCount).any {
                category.getPreference(it).isVisible
            }
            category.isVisible = hasVisibleChildren
        }
    }
    
    // Launcher for backup path picker
    private val backupPathResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    activity?.contentResolver?.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    editor.putString("backup_path", it.toString())
                    editor.apply()
                    backupPath?.summary = FileUtil.formatPath(it.toString())
                }
            }
        }
    
    // Launcher for restore file picker
    private val appRestoreResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    val contentResolver = requireContext().contentResolver
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(it)))
                    val content = reader.readText()

                    runCatching {
                        val json: JsonObject = JsonParser.parseString(content).asJsonObject
                        val parsedDataMessage = StringBuilder()
                        val restoreData = RestoreAppDataItem()
                        
                        if (json.has("settings")) {
                            val settings = json.getAsJsonArray("settings")
                            restoreData.settings = settings.map {
                                Gson().fromJson(it.toString(), BackupSettingsItem::class.java)
                            }
                            parsedDataMessage.appendLine("${getString(R.string.settings)}: ${settings.size()}")
                        }

                        if (json.has("history")) {
                            restoreData.downloads = json.getAsJsonArray("history").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), HistoryItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.downloads)}: ${restoreData.downloads!!.size}")
                        }

                        if (json.has("queued")) {
                            restoreData.queued = json.getAsJsonArray("queued").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.queue)}: ${restoreData.queued!!.size}")
                        }

                        if (json.has("scheduled")) {
                            restoreData.scheduled = json.getAsJsonArray("scheduled").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.scheduled)}: ${restoreData.scheduled!!.size}")
                        }

                        if (json.has("cancelled")) {
                            restoreData.cancelled = json.getAsJsonArray("cancelled").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.cancelled)}: ${restoreData.cancelled!!.size}")
                        }

                        if (json.has("errored")) {
                            restoreData.errored = json.getAsJsonArray("errored").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.errored)}: ${restoreData.errored!!.size}")
                        }

                        if (json.has("saved")) {
                            restoreData.saved = json.getAsJsonArray("saved").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.saved)}: ${restoreData.saved!!.size}")
                        }

                        if (json.has("cookies")) {
                            restoreData.cookies = json.getAsJsonArray("cookies").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), CookieItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.cookies)}: ${restoreData.cookies!!.size}")
                        }

                        if (json.has("templates")) {
                            restoreData.templates = json.getAsJsonArray("templates").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    CommandTemplate::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.command_templates)}: ${restoreData.templates!!.size}")
                        }

                        if (json.has("shortcuts")) {
                            restoreData.shortcuts = json.getAsJsonArray("shortcuts").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    TemplateShortcut::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.shortcuts)}: ${restoreData.shortcuts!!.size}")
                        }

                        if (json.has("search_history")) {
                            restoreData.searchHistory = json.getAsJsonArray("search_history").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    SearchHistoryItem::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.search_history)}: ${restoreData.searchHistory!!.size}")
                        }

                        if (json.has("observe_sources")) {
                            restoreData.observeSources = json.getAsJsonArray("observe_sources").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    ObserveSourcesItem::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.observe_sources)}: ${restoreData.observeSources!!.size}")
                        }

                        showAppRestoreInfoDialog(
                            onMerge = {
                                lifecycleScope.launch {
                                    val res = withContext(Dispatchers.IO){
                                        settingsViewModel.restoreData(restoreData, requireContext())
                                    }
                                    if (res) {
                                        showRestoreFinishedDialog(restoreData, parsedDataMessage.toString())
                                    }else{
                                        throw Error()
                                    }
                                }
                            },
                            onReset =  {
                                lifecycleScope.launch {
                                    val res = withContext(Dispatchers.IO){
                                        settingsViewModel.restoreData(restoreData, requireContext(),true)
                                    }
                                    if (res) {
                                        showRestoreFinishedDialog(restoreData, parsedDataMessage.toString())
                                    }else{
                                        throw Error()
                                    }
                                }
                            }
                        )

                    }.onFailure {
                        it.printStackTrace()
                        Snackbar.make(requireView(), it.message.toString(), Snackbar.LENGTH_INDEFINITE).show()
                    }
                }

            }
        }

    // Dialog that asks the user whether to merge the restored data with existing data
    // or to reset (clear) existing data first.
    private fun showAppRestoreInfoDialog(onMerge: () -> Unit, onReset: () -> Unit){
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage(getString(R.string.restore_info))

        builder.setNegativeButton(getString(R.string.cancel)) { dialog : DialogInterface?, _: Int ->
            dialog?.dismiss()
        }

        builder.setPositiveButton(getString(R.string.restore)) { dialog : DialogInterface?, _: Int ->
            onMerge()
            dialog?.dismiss()
        }

        builder.setNeutralButton(getString(R.string.reset)) { dialog : DialogInterface?, _: Int ->
            onReset()
            dialog?.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }

    // Dialog shown after a successful restore, listing the categories that were restored.
    private fun showRestoreFinishedDialog(data: RestoreAppDataItem, restoreDataMessage: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage("${getString(R.string.restore_complete)}\n\n$restoreDataMessage")
        builder.setCancelable(false)
        builder.setPositiveButton(
            getString(R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            if(data.settings != null){
                val languageValue = preferences.getString("app_language", "en")
                if (languageValue == "system") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(null))
                }else{
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageValue.toString()))
                }
            }
            ThemeUtil.recreateAllActivities()
        }

        val dialog = builder.create()
        dialog.show()
    }
    
    override fun onDestroyView() {
        activeSnackbar?.dismiss()
        activeSnackbar = null
        super.onDestroyView()
        searchManager.clearCache()
    }
}
