package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.LayoutDirection
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.text.layoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.MainActivity
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
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ObserveSourcesViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.ui.more.settings.FolderSettingsFragment.Companion.COMMAND_PATH_CODE
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale

class MainSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.settings

    private var appearance: Preference? = null
    private var folders: Preference? = null
    private var downloading: Preference? = null
    private var processing: Preference? = null
    private var updating: Preference? = null
    private var advanced: Preference? = null

    private var backup : Preference? = null
    private var restore : Preference? = null
    private var backupPath : Preference? = null

    private var updateUtil: UpdateUtil? = null
    private var activeDownloadCount = 0

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var editor: SharedPreferences.Editor

    // Store dynamically created search categories
    private val searchCategories = mutableMapOf<String, PreferenceCategory>()
    private var isSearchMode = false
    
    // Store the last search query for returning from navigation
    private var lastSearchQuery: String = ""

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
    
    private val categoryNavigationActions = mapOf(
        "appearance" to R.id.action_mainSettingsFragment_to_appearanceSettingsFragment,
        "folders" to R.id.action_mainSettingsFragment_to_folderSettingsFragment,
        "downloading" to R.id.action_mainSettingsFragment_to_downloadSettingsFragment,
        "processing" to R.id.action_mainSettingsFragment_to_processingSettingsFragment,
        "updating" to R.id.action_mainSettingsFragment_to_updateSettingsFragment,
        "advanced" to R.id.action_mainSettingsFragment_to_advancedSettingsFragment
    )

    // Data class to represent a preference with its hierarchy
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        buildPreferenceList(preferenceScreen)

        val navController = findNavController()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()

        appearance = findPreference("appearance")
        folders = findPreference("folders")
        downloading = findPreference("downloading")
        processing = findPreference("processing")
        updating = findPreference("updating")
        advanced = findPreference("advanced")

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

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        backup = findPreference("backup")
        restore = findPreference("restore")
        backupPath = findPreference("backup_path")

        findPreference<Preference>("package_name")?.apply {
            summary = BuildConfig.APPLICATION_ID
            setOnPreferenceClickListener {
                UiUtil.copyToClipboard(summary.toString(), requireActivity())
                true
            }
        }

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

    override fun filterPreferences(query: String) {
        if (query.isEmpty()) {
            restoreNormalView()
            return
        }

        lastSearchQuery = query
        enterSearchMode(query.lowercase())
    }

    private fun restoreNormalView() {
        isSearchMode = false
        lastSearchQuery = ""
        
        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()

        restoreAllPreferences()
        appearance?.isVisible = true
        folders?.isVisible = true
        downloading?.isVisible = true
        processing?.isVisible = true
        updating?.isVisible = true
        advanced?.isVisible = true
        
        findPreference<PreferenceCategory>("backup_restore")?.isVisible = true
    }

    private fun enterSearchMode(query: String) {
        isSearchMode = true
        
        // Hide original category navigation preferences
        appearance?.isVisible = false
        folders?.isVisible = false
        downloading?.isVisible = false
        processing?.isVisible = false
        updating?.isVisible = false
        advanced?.isVisible = false
        
        // Clear existing search categories
        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()

        // Search through each nested settings page and build hierarchical results
        categoryFragmentMap.forEach { (categoryKey, xmlRes) ->
            val hierarchicalResults = findMatchingPreferencesWithHierarchy(xmlRes, query)
            
            if (hierarchicalResults.isNotEmpty()) {
                // Create a main category for this section
                val mainCategory = PreferenceCategory(requireContext()).apply {
                    title = getString(categoryTitles[categoryKey] ?: R.string.settings)
                    key = "search_main_$categoryKey"
                }
                
                preferenceScreen.addPreference(mainCategory)
                
                // Build the hierarchical structure with smart parent filtering
                buildHierarchicalPreferences(hierarchicalResults, mainCategory, categoryKey, query)
                
                searchCategories[categoryKey] = mainCategory
            }
        }

        // Also search current screen preferences
        super.filterPreferences(query)
        hideEmptyCategoriesInMain()
    }

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

    private fun collectMatchingWithHierarchy(
        group: PreferenceGroup,
        query: String,
        results: MutableList<HierarchicalPreference>,
        parent: PreferenceGroup?,
        depth: Int
    ) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)

            val title = pref.title?.toString() ?: ""
            val summary = pref.summary?.toString() ?: ""
            val key = pref.key ?: ""

            val matches = title.lowercase().contains(query) ||
                    summary.lowercase().contains(query) ||
                    key.lowercase().contains(query)

            if (pref is PreferenceGroup) {
                // For groups (categories), check if they or their children match
                val childMatches = mutableListOf<HierarchicalPreference>()
                collectMatchingWithHierarchy(pref, query, childMatches, pref, depth + 1)
                
                // Only add parent if it matches OR has matching children
                if (childMatches.isNotEmpty()) {
                    // Add the parent category
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = true))
                    // Then add all matching children
                    results.addAll(childMatches)
                } else if (matches) {
                    // Category itself matches but has no children - still add it
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = true))
                }
            } else {
                // Regular preference - only add if it matches
                if (matches) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = false))
                }
            }
        }
    }

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
                // Save previous parent if it had children
                if (currentParentCategory != null && currentParentHasChildren) {
                    // Keep the parent
                }
                
                // Start new parent category
                currentParentCategory = PreferenceCategory(requireContext()).apply {
                    title = pref.title
                    key = "search_sub_${pref.key}_${categoryKey}"
                }
                currentParentHasChildren = false
                
                // Don't add it yet - wait to see if it has matching children
            } else {
                // This is a child preference
                val clonedPref = clonePreference(pref, categoryKey)
                
                // If we have a pending parent and this is its first child, add the parent now
                if (currentParentCategory != null && !currentParentHasChildren) {
                    mainCategory.addPreference(currentParentCategory!!)
                    currentParentHasChildren = true
                }
                
                if (currentParentCategory != null) {
                    // Add to the current subcategory
                    currentParentCategory!!.addPreference(clonedPref)
                } else {
                    // No parent category, add directly to main
                    mainCategory.addPreference(clonedPref)
                }
            }
        }
    }

    private fun clonePreference(original: Preference, categoryKey: String): Preference {
        val cloned = try {
            original.javaClass.getConstructor(
                android.content.Context::class.java
            ).newInstance(requireContext())
        } catch (e: Exception) {
            Preference(requireContext())
        }

        cloned.apply {
            title = original.title
            summary = original.summary
            key = original.key
            icon = original.icon
            isEnabled = original.isEnabled
            
            setOnPreferenceClickListener {
                navigateToPreferenceLocation(original, categoryKey)
                true
            }
        }

        return cloned
    }

    private fun navigateToPreferenceLocation(pref: Preference, categoryKey: String) {
        val categoryName = getString(categoryTitles[categoryKey] ?: R.string.settings)
        val prefTitle = pref.title?.toString() ?: "setting"
        
        // Show toast above keyboard
        showToastAboveKeyboard("Go to $prefTitle")
        
        // Navigate with arguments to highlight the preference
        val navController = findNavController()
        val action = categoryNavigationActions[categoryKey]
        
        if (action != null) {
            val bundle = Bundle().apply {
                putString(ARG_HIGHLIGHT_KEY, pref.key)
                putBoolean(ARG_RETURN_TO_SEARCH, true)
            }
            navController.navigate(action, bundle)
        }
    }

    private fun showToastAboveKeyboard(message: String) {
        try {
            // Try Snackbar at top (more modern)
            val snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT)
            val view = snackbar.view
            
            // Move to top of screen
            val params = view.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            if (params != null) {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = 100
                view.layoutParams = params
                snackbar.show()
            } else {
                // Fallback to toast if not in CoordinatorLayout
                showSimpleTopToast(message)
            }
        } catch (e: Exception) {
            // Fallback to simple toast
            showSimpleTopToast(message)
        }
    }
    
    private fun showSimpleTopToast(message: String) {
        val toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 150)
        toast.show()
    }

    private fun hideEmptyCategoriesInMain() {
        findPreference<PreferenceCategory>("backup_restore")?.let { category ->
            val hasVisibleChildren = (0 until category.preferenceCount).any {
                category.getPreference(it).isVisible
            }
            category.isVisible = hasVisibleChildren
        }

        findPreference<PreferenceCategory>("about")?.let { category ->
            val hasVisibleChildren = (0 until category.preferenceCount).any {
                category.getPreference(it).isVisible
            }
            category.isVisible = hasVisibleChildren
        }
    }

    private var backupPathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val path = result.data!!.data.toString()
            backupPath!!.summary = FileUtil.formatPath(path)
            editor.putString("backup_path", path)
            editor.apply()
        }
    }

    private var appRestoreResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            lifecycleScope.launch {
                runCatching {
                    val ip = requireContext().contentResolver.openInputStream(result.data!!.data!!)
                    val r = BufferedReader(InputStreamReader(ip))
                    val total: java.lang.StringBuilder = java.lang.StringBuilder()
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        total.append(line).append('\n')
                    }

                    val json = Gson().fromJson(total.toString(), JsonObject::class.java)
                    val restoreData = RestoreAppDataItem()
                    val parsedDataMessage = StringBuilder()

                    if (json.has("settings")) {
                        restoreData.settings = json.getAsJsonArray("settings").map {
                            Gson().fromJson(it.toString().replace("^\"|\"$", ""), BackupSettingsItem::class.java)
                        }
                        parsedDataMessage.appendLine("${getString(R.string.settings)}: ${restoreData.settings!!.size}")
                    }

                    if (json.has("downloads")) {
                        restoreData.downloads = json.getAsJsonArray("downloads").map {
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

    @SuppressLint("RestrictedApi")
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
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }

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
}
