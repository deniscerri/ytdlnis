package com.deniscerri.ytdlnis.ui.more.settings

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.LayoutDirection
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.CookieItem
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.SearchHistoryItem
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
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

        backup = findPreference("backup")
        restore = findPreference("restore")

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
                                        "cancelled" -> json.add("cancelled", backupCancelledDownloads() )
                                        "errored" -> json.add("errored", backupErroredDownloads() )
                                        "saved" -> json.add("saved", backupSavedDownloads() )
                                        "cookies" -> json.add("cookies", backupCookies() )
                                        "templates" -> json.add("templates", backupCommandTemplates() )
                                        "shortcuts" -> json.add("shortcuts", backupShortcuts() )
                                        "searchHistory" -> json.add("search_history", backupSearchHistory() )
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
                            UiUtil.openFileIntent(requireActivity(), saveFile.absolutePath)
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
                val preferences =
                    PreferenceManager.getDefaultSharedPreferences(requireContext())

                runCatching {
                    val ip = requireContext().contentResolver.openInputStream(result.data!!.data!!)
                    val r = BufferedReader(InputStreamReader(ip))
                    val total: java.lang.StringBuilder = java.lang.StringBuilder()
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        total.append(line).append('\n')
                    }

                    val json = Gson().fromJson(total.toString(), JsonObject::class.java)
                    val finalMessage = StringBuilder("${getString(R.string.restore_complete)}\n")

                    //preference restore
                    if(json.has("settings")){
                        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true){
                            clear()
                            val prefs = json.getAsJsonArray("settings")
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
                            finalMessage.append("${getString(R.string.settings)}: ${prefs.count()}\n")
                        }
                    }

                    //history restore
                    if(json.has("downloads")){
                        val items = json.getAsJsonArray("downloads")
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), HistoryItem::class.java)
                            item.id = 0L
                            withContext(Dispatchers.IO){
                                historyViewModel.insert(item)
                            }
                        }
                        finalMessage.append("${getString(R.string.downloads)}: ${items.count()}\n")
                    }

                    //queued downloads restore
                    if(json.has("queued")){
                        val items = json.getAsJsonArray("queued")
                        val queued = mutableListOf<DownloadItem>()
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            queued.add(item)
                        }
                        if (queued.isNotEmpty()) {
                            finalMessage.append("${getString(R.string.in_queue)}: ${queued.count()}\n")
                            withContext(Dispatchers.IO){downloadViewModel.queueDownloads(queued)}
                        }
                    }

                    //cancelled downloads restore
                    if(json.has("cancelled")){
                        val items = json.getAsJsonArray("cancelled")
                        val cancelled = mutableListOf<DownloadItem>()
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            cancelled.add(item)

                        }

                        cancelled.asReversed().forEach { f ->
                            withContext(Dispatchers.IO){
                                downloadViewModel.insert(f)
                            }
                        }

                        if(cancelled.isNotEmpty()){
                            finalMessage.append("${getString(R.string.cancelled)}: ${cancelled.count()}\n")
                        }
                    }

                    //erorred downloads restore
                    if(json.has("errored")){
                        val items = json.getAsJsonArray("errored")
                        val errored = mutableListOf<DownloadItem>()
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            errored.add(item)

                        }

                        errored.asReversed().forEach { f ->
                            withContext(Dispatchers.IO){
                                downloadViewModel.insert(f)
                            }
                        }

                        if(errored.isNotEmpty()){
                            finalMessage.append("${getString(R.string.errored)}: ${errored.count()}\n")
                        }
                    }

                    //erorred downloads restore
                    if(json.has("saved")){
                        val items = json.getAsJsonArray("saved")
                        val saved = mutableListOf<DownloadItem>()
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                            item.id = 0L
                            saved.add(item)

                        }

                        saved.asReversed().forEach { f ->
                            withContext(Dispatchers.IO){
                                downloadViewModel.insert(f)
                            }
                        }

                        if(saved.isNotEmpty()){
                            finalMessage.append("${getString(R.string.saved)}: ${saved.count()}\n")
                        }
                    }

                    //cookies restore
                    if(json.has("cookies")){
                        val items = json.getAsJsonArray("cookies")
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), CookieItem::class.java)
                            item.id = 0L
                            withContext(Dispatchers.IO){
                                cookieViewModel.insert(item)
                            }
                        }
                        if(items.count() > 0){
                            finalMessage.append("${getString(R.string.cookies)}: ${items.count()}\n")
                        }
                    }

                    //command template restore
                    if(json.has("templates")){
                        val items = json.getAsJsonArray("templates")
                        val templates = mutableListOf<CommandTemplate>()
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), CommandTemplate::class.java)
                            item.id = 0L
                            templates.add(item)

                        }

                        templates.asReversed().forEach { t ->
                            withContext(Dispatchers.IO){
                                commandTemplateViewModel.insert(t)
                            }
                        }

                        if(items.count() > 0){
                            finalMessage.append("${getString(R.string.command_templates)}: ${items.count()}\n")
                        }
                    }

                    //shortcuts restore
                    if(json.has("shortcuts")){
                        val items = json.getAsJsonArray("shortcuts")
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), TemplateShortcut::class.java)
                            item.id = 0L
                            withContext(Dispatchers.IO){
                                commandTemplateViewModel.insertShortcut(item)
                            }
                        }
                        if(items.count() > 0){
                            finalMessage.append("${getString(R.string.shortcuts)}: ${items.count()}\n")
                        }
                    }


                    //search history restore
                    if(json.has("search_history")){
                        val items = json.getAsJsonArray("search_history")
                        items.forEach {
                            val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), SearchHistoryItem::class.java)
                            withContext(Dispatchers.IO){
                                resultViewModel.addSearchQueryToHistory(item.query)
                            }
                        }
                        if(items.count() > 0){
                            finalMessage.append("${getString(R.string.search_history)}: ${items.count()}\n")
                        }
                    }

                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle(getString(R.string.restore))
                    builder.setMessage(finalMessage)
                    builder.setPositiveButton(
                        getString(R.string.restart)
                    ) { _: DialogInterface?, _: Int ->
                        val intent = Intent(requireContext(), MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        requireActivity().finishAffinity()
                        if(json.has("settings")){
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(preferences.getString("app_language", "en")))
                        }
                        activity?.finishAffinity()
                    }

                    // handle the negative button of the alert dialog
                    builder.setNegativeButton(
                        getString(R.string.cancel)
                    ) { _: DialogInterface?, _: Int ->
                        if(json.has("settings")){
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(preferences.getString("app_language", "en")))
                        }
                    }

                    val dialog = builder.create()
                    dialog.show()
                }.onFailure {
                    it.printStackTrace()
                    Snackbar.make(requireView(), getString(R.string.couldnt_parse_file), Snackbar.LENGTH_LONG).show()
                }
            }

        }
    }

}