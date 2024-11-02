package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.LayoutDirection
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.text.layoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
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
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale


class MainSettingsFragment : PreferenceFragmentCompat() {
    private var backup : Preference? = null
    private var restore : Preference? = null

    private var updateUtil: UpdateUtil? = null
    private var activeDownloadCount = 0

    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var observeSourcesViewModel: ObserveSourcesViewModel


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val navController = findNavController()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val appearance = findPreference<Preference>("appearance")
        val separator = if (Locale(preferences.getString("app_language", "en")!!).layoutDirection == LayoutDirection.RTL) "،" else ","
        appearance?.summary = "${if (Build.VERSION.SDK_INT < 33) getString(R.string.language) + "$separator " else ""}${getString(R.string.Theme)}$separator ${getString(R.string.accents)}$separator ${getString(R.string.preferred_search_engine)}"
        appearance?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_appearanceSettingsFragment)
            true
        }

        val folders = findPreference<Preference>("folders")
        folders?.summary = "${getString(R.string.music_directory)}$separator ${getString(R.string.video_directory)}$separator ${getString(R.string.command_directory)}"
        folders?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_folderSettingsFragment)
            true
        }

        val downloading = findPreference<Preference>("downloading")
        downloading?.summary = "${getString(R.string.quick_download)}$separator ${getString(R.string.concurrent_downloads)}$separator ${getString(R.string.limit_rate)}"
        downloading?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_downloadSettingsFragment)
            true
        }

        val processing = findPreference<Preference>("processing")
        processing?.summary = "${getString(R.string.sponsorblock)}$separator ${getString(R.string.embed_subtitles)}$separator ${getString(R.string.add_chapters)}"
        processing?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_processingSettingsFragment)
            true
        }

        val updating = findPreference<Preference>("updating")
        updating?.summary = "${getString(R.string.update_ytdl)}$separator ${getString(R.string.format_source)}$separator ${getString(R.string.update_app)}"
        updating?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_updateSettingsFragment)
            true
        }

        val advanced = findPreference<Preference>("advanced")
        advanced?.summary = "PO Token$separator ${getString(R.string.other_youtube_extractor_args)}"
        advanced?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_advancedSettingsFragment)
            true
        }

        //about section -------------------------
        updateUtil = UpdateUtil(requireContext())

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        observeSourcesViewModel = ViewModelProvider(this)[ObserveSourcesViewModel::class.java]

        backup = findPreference("backup")
        restore = findPreference("restore")

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
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (checkedItems.all { !it }){
                            withContext(Dispatchers.Main){
                                Snackbar.make(requireView(), R.string.select_backup_categories, Snackbar.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        val json = JsonObject()
                        json.addProperty("app", "YTDLnis_backup")
                        for (i in 0 until checkedItems.size) {
                            if (checkedItems[i]) {
                                runCatching {
                                    when(values[i]){
                                        "settings" -> json.add("settings", backupSettings(preferences))
                                        "downloads" -> json.add("downloads", backupHistory())
                                        "queued" -> json.add("queued", backupQueuedDownloads() )
                                        "scheduled" -> json.add("scheduled", backupScheduledDownloads() )
                                        "cancelled" -> json.add("cancelled", backupCancelledDownloads() )
                                        "errored" -> json.add("errored", backupErroredDownloads() )
                                        "saved" -> json.add("saved", backupSavedDownloads() )
                                        "cookies" -> json.add("cookies", backupCookies() )
                                        "templates" -> json.add("templates", backupCommandTemplates() )
                                        "shortcuts" -> json.add("shortcuts", backupShortcuts() )
                                        "searchHistory" -> json.add("search_history", backupSearchHistory() )
                                        "observeSources" -> json.add("observe_sources", backupObserveSources() )
                                    }
                                }.onFailure {err ->
                                    withContext(Dispatchers.Main){
                                        val snack = Snackbar.make(requireView(), err.message ?: requireContext().getString(R.string.errored), Snackbar.LENGTH_LONG)
                                        val snackbarView: View = snack.view
                                        val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                                        snackTextView.maxLines = 9999999
                                        snack.setAction(android.R.string.copy){
                                            UiUtil.copyToClipboard(err.message ?: requireContext().getString(R.string.errored), requireActivity())
                                        }
                                        snack.show()
                                    }
                                }
                            }
                        }

                        val currentTime = Calendar.getInstance()
                        val saveFile = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS), "YTDLnis_${BuildConfig.VERSION_NAME}_${currentTime.get(
                                Calendar.YEAR)}-${currentTime.get(Calendar.MONTH) + 1}-${currentTime.get(
                                Calendar.DAY_OF_MONTH)} [${currentTime.get(Calendar.MILLISECOND)}.json")
                        saveFile.delete()
                        saveFile.createNewFile()
                        saveFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(json))
                        val s = Snackbar.make(requireView(), getString(R.string.backup_created_successfully), Snackbar.LENGTH_LONG)
                        s.setAction(R.string.Open_File){
                            FileUtil.openFileIntent(requireActivity(), saveFile.absolutePath)
                        }
                        s.show()
                    }
                }

                // handle the negative button of the alert dialog
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
                appRestoreResultLauncher.launch(intent)
                true
            }

    }

    private fun backupSettings(preferences: SharedPreferences) : JsonArray {
        runCatching {
            val prefs = preferences.all
            prefs.remove("app_language")
            val arr = JsonArray()
            prefs.forEach {
                val obj = JsonObject()
                obj.addProperty("key", it.key)
                obj.addProperty("value", it.value.toString())
                obj.addProperty("type", it.value!!::class.simpleName)
                arr.add(obj)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupHistory() : JsonArray {
        runCatching {
            val historyItems = withContext(Dispatchers.IO) {
                historyViewModel.getAll()
            }
            val arr = JsonArray()
            historyItems.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupQueuedDownloads() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadViewModel.getQueued()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupScheduledDownloads() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadViewModel.getScheduled()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupCancelledDownloads() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadViewModel.getCancelled()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupErroredDownloads() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadViewModel.getErrored()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupSavedDownloads() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadViewModel.getSaved()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupCookies() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                cookieViewModel.getAll()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupCommandTemplates() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                commandTemplateViewModel.getAll()
            }
            val arr = JsonArray()
            items.forEach {
                it.useAsExtraCommand = false
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupShortcuts() : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                commandTemplateViewModel.getAllShortcuts()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupSearchHistory() : JsonArray {
        runCatching {
            val historyItems = withContext(Dispatchers.IO) {
                resultViewModel.getSearchHistory()
            }
            val arr = JsonArray()
            historyItems.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    private suspend fun backupObserveSources() : JsonArray {
        runCatching {
            val observeSourcesItems = withContext(Dispatchers.IO) {
                observeSourcesViewModel.getAll()
            }
            val arr = JsonArray()
            observeSourcesItems.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
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

                    //PARSE RESTORE JSON
                    val json = Gson().fromJson(total.toString(), JsonObject::class.java)
                    val restoreData = RestoreAppDataItem()
                    val parsedDataMessage = StringBuilder()

                    if (json.has("settings")) {
                        restoreData.settings = json.getAsJsonArray("settings")
                        parsedDataMessage.appendLine("${getString(R.string.settings)}: ${restoreData.settings!!.size()}")
                    }

                    if (json.has("downloads")) {
                        restoreData.downloads = json.getAsJsonArray("downloads").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), HistoryItem::class.java)
                            item.id = 0L
                            item
                        }
                        parsedDataMessage.appendLine("${getString(R.string.downloads)}: ${restoreData.downloads!!.size}")

                    }

                    if (json.has("queued")) {
                        restoreData.queued = json.getAsJsonArray("queued").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            item
                        }
                        parsedDataMessage.appendLine("${getString(R.string.queue)}: ${restoreData.queued!!.size}")
                    }

                    if (json.has("scheduled")) {
                        restoreData.scheduled = json.getAsJsonArray("scheduled").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            item
                        }
                        parsedDataMessage.appendLine("${getString(R.string.scheduled)}: ${restoreData.scheduled!!.size}")
                    }

                    if (json.has("cancelled")) {
                        restoreData.cancelled = json.getAsJsonArray("cancelled").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            item
                        }
                        parsedDataMessage.appendLine("${getString(R.string.cancelled)}: ${restoreData.cancelled!!.size}")
                    }

                    if (json.has("errored")) {
                        restoreData.errored = json.getAsJsonArray("errored").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            item
                        }
                        parsedDataMessage.appendLine("${getString(R.string.errored)}: ${restoreData.errored!!.size}")
                    }

                    if (json.has("saved")) {
                        restoreData.saved = json.getAsJsonArray("saved").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            item
                        }
                        parsedDataMessage.appendLine("${getString(R.string.saved)}: ${restoreData.saved!!.size}")
                    }

                    if (json.has("cookies")) {
                        restoreData.cookies = json.getAsJsonArray("cookies").map {
                            val item =
                                Gson().fromJson(it.toString().replace("^\"|\"$", ""), CookieItem::class.java)
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
                            restoreData(restoreData,parsedDataMessage.toString())
                        },
                        onReset =  {
                            restoreData(restoreData,parsedDataMessage.toString(), true)
                        }
                    )

                }.onFailure {
                    it.printStackTrace()
                    Snackbar.make(requireView(), getString(R.string.couldnt_parse_file), Snackbar.LENGTH_LONG).show()
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

    private fun restoreData(data: RestoreAppDataItem, restoreDataMessage: String, resetData: Boolean = false) = lifecycleScope.launch {
        data.settings?.apply {
            val prefs = this
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true){
                clear()
                prefs.forEach {
                    val key : String = it.asJsonObject.get("key").toString().replace("\"", "")
                    when(it.asJsonObject.get("type").toString().replace("\"", "")){
                        "String" -> {
                            val value = it.asJsonObject.get("value").toString().replace("\"", "")
                            putString(key, value)
                        }
                        "Boolean" -> {
                            val value = it.asJsonObject.get("value").toString().replace("\"", "").toBoolean()
                            Log.e("REST", value.toString())
                            Log.e("REST", key)
                            putBoolean(key, value)
                        }
                        "Int" -> {
                            val value = it.asJsonObject.get("value").toString().replace("\"", "").toInt()
                            putInt(key, value)
                        }
                        "HashSet" -> {
                            val value = it.asJsonObject.get("value").toString().replace("(\")|(\\[)|(])|([ \\t])".toRegex(), "").split(",")
                            putStringSet(key, value.toHashSet())
                        }
                    }
                }
            }
        }


        data.downloads?.apply {
            withContext(Dispatchers.IO){
                if (resetData) historyViewModel.deleteAll(false)
                data.downloads!!.forEach {
                    historyViewModel.insert(it)
                }
            }
        }

        data.queued?.apply {
            withContext(Dispatchers.IO){
                if (resetData) downloadViewModel.deleteQueued()
                data.queued!!.forEach {
                    downloadViewModel.insert(it)
                }
                downloadViewModel.queueDownloads(listOf())
            }
        }

        data.cancelled?.apply {
            withContext(Dispatchers.IO){
                if (resetData) downloadViewModel.deleteCancelled()
                data.cancelled!!.forEach {
                    downloadViewModel.insert(it)
                }
            }
        }

        data.errored?.apply {
            withContext(Dispatchers.IO){
                if (resetData) downloadViewModel.deleteErrored()
                data.errored!!.forEach {
                    downloadViewModel.insert(it)
                }
            }
        }

        data.saved?.apply {
            withContext(Dispatchers.IO){
                if (resetData) downloadViewModel.deleteSaved()
                data.saved!!.forEach {
                    downloadViewModel.insert(it)
                }
            }
        }

        data.cookies?.apply {
            withContext(Dispatchers.IO){
                if (resetData) cookieViewModel.deleteAll()
                data.cookies!!.forEach {
                    cookieViewModel.insert(it)
                }
            }
        }

        data.templates?.apply {
            withContext(Dispatchers.IO){
                if (resetData) commandTemplateViewModel.deleteAll()
                data.templates!!.forEach {
                    commandTemplateViewModel.insert(it)
                }
            }
        }

        data.shortcuts?.apply {
            withContext(Dispatchers.IO){
                if (resetData) commandTemplateViewModel.deleteAllShortcuts()
                data.shortcuts!!.forEach {
                    commandTemplateViewModel.insertShortcut(it)
                }
            }
        }

        data.searchHistory?.apply {
            withContext(Dispatchers.IO){
                if (resetData) resultViewModel.deleteAllSearchQueryHistory()
                data.searchHistory!!.forEach {
                    resultViewModel.addSearchQueryToHistory(it.query)
                }
            }
        }

        data.observeSources?.apply {
            withContext(Dispatchers.IO){
                if (resetData) observeSourcesViewModel.deleteAll()
                data.observeSources!!.forEach {
                    observeSourcesViewModel.insert(it)
                }
            }
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage("${getString(R.string.restore_complete)}\n $restoreDataMessage")
        builder.setPositiveButton(
            getString(R.string.restart)
        ) { _: DialogInterface?, _: Int ->
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            requireActivity().finishAffinity()
            if(data.settings != null){
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(preferences.getString("app_language", "en")))
            }
            activity?.finishAffinity()
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            getString(R.string.cancel)
        ) { _: DialogInterface?, _: Int ->
            if(data.settings != null){
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(preferences.getString("app_language", "en")))
            }
        }

        val dialog = builder.create()
        dialog.show()
    }
}