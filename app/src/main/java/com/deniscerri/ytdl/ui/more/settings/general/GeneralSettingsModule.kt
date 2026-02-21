package com.deniscerri.ytdl.ui.more.settings.general

import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.databinding.NavOptionsItemBinding
import com.deniscerri.ytdl.ui.adapter.IconsSheetAdapter
import com.deniscerri.ytdl.ui.adapter.NavBarOptionsAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.collections.forEach

object GeneralSettingsModule : SettingModule {
    override fun bindLogic(
        pref: Preference,
        host: SettingHost
    ) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var activeDownloadCount = 0
        WorkManager.getInstance(context).getWorkInfosByTagLiveData("download").observe(host.hostLifecycleOwner){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }

        val resultViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[ResultViewModel::class.java]

        when(pref.key) {
            "app_language" -> {
                (pref as ListPreference).apply {
                    value = Locale.getDefault().language
                    summary = Locale.getDefault().displayLanguage

                    setOnPreferenceChangeListener { _, newValue ->
                        if (newValue == "system") {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(null))
                        }else{
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()))
                        }
                        summary = Locale.getDefault().displayLanguage
                        host.refreshUI()
                        true
                    }
                }
            }
            "label_visibility" -> {
                pref.apply {
                    isVisible = !context.resources.getBoolean(R.bool.uses_side_nav)
                    setOnPreferenceChangeListener { _, _ ->
                        ThemeUtil.recreateMain()
                        host.refreshUI()
                        true
                    }
                }
            }
            "navigation_bar" -> {
                pref.apply {
                    NavbarUtil.init(context)

                    isVisible = !context.resources.getBoolean(R.bool.uses_side_nav)
                    if (isVisible) {
                        summary = NavbarUtil.getNavBarItems(context).filter { it.isVisible }.map { it.title }.joinToString(", ")
                    }
                    setOnPreferenceClickListener {
                        val binding = host.getHostContext().layoutInflater.inflate(R.layout.simple_options_recycler, null)
                        val options = NavbarUtil.getNavBarItems(context)

                        val optionsRecycler = binding.findViewById<RecyclerView>(R.id.options_recycler)
                        val adapter : NavBarOptionsAdapter?

                        val onItemClick = object: NavBarOptionsAdapter.OnItemClickListener {
                            override fun onNavBarOptionDeselected(item: NavOptionsItemBinding) {
                                optionsRecycler.findViewHolderForLayoutPosition(0)?.apply {
                                    (this as NavBarOptionsAdapter.NavBarOptionsViewHolder).apply {
                                        this.binding.home.performClick()
                                    }
                                }
                            }
                        }

                        adapter = NavBarOptionsAdapter(
                            options.toMutableList(),
                            NavbarUtil.getStartFragmentId(context),
                            onItemClick
                        )

                        val itemTouchCallback = object : ItemTouchHelper.Callback() {
                            override fun getMovementFlags(
                                recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder
                            ): Int {
                                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                                return makeMovementFlags(dragFlags, 0)
                            }

                            override fun onMove(
                                recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder
                            ): Boolean {
                                val itemToMove = adapter.items[viewHolder.absoluteAdapterPosition]
                                adapter.items.remove(itemToMove)
                                adapter.items.add(target.absoluteAdapterPosition, itemToMove)

                                adapter.notifyItemMoved(
                                    viewHolder.absoluteAdapterPosition,
                                    target.absoluteAdapterPosition
                                )
                                return true
                            }

                            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                                // do nothing
                            }
                        }


                        optionsRecycler.layoutManager = LinearLayoutManager(context)
                        optionsRecycler.adapter = adapter

                        val itemTouchHelper = ItemTouchHelper(itemTouchCallback)
                        itemTouchHelper.attachToRecyclerView(optionsRecycler)

                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.navigation_bar)
                            .setView(binding)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                NavbarUtil.setNavBarItems(adapter.items, context)
                                NavbarUtil.setStartFragment(adapter.selectedHomeTabId)
                                summary = adapter.items.filter { it.isVisible }.map { it.title }.joinToString(", ")
                                ThemeUtil.recreateMain()
                                host.refreshUI()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                        true
                    }
                }
            }
            "ytdlnis_theme" -> {
                (pref as ListPreference).apply {
                    summary = entry
                    setOnPreferenceChangeListener { _, newValue ->
                        val dialog = MaterialAlertDialogBuilder(context)
                        dialog.setTitle(context.getString(R.string.app_icon_change))
                        dialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        dialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            summary = when(newValue){
                                "System" -> {
                                    context.getString(R.string.system)
                                }

                                "Dark" -> {
                                    context.getString(R.string.dark)
                                }

                                else -> {
                                    context.getString(R.string.light)
                                }
                            }
                            preferences.edit(commit = true){
                                putString("ytdlnis_theme", newValue.toString())
                            }
                            ThemeUtil.updateThemes()
                            host.refreshUI()
                        }
                        dialog.show()
                        false
                    }
                }
            }
            "ytdlnis_icon" -> {
                pref.apply {
                    val currentValue = preferences.getString("ytdlnis_icon", "default")
                    IconsSheetAdapter.availableIcons.firstOrNull { it.activityAlias == currentValue }?.let {
                        summary = context.getString(it.nameResource)
                    }

                    setOnPreferenceClickListener {
                        val bottomSheet = BottomSheetDialog(context)
                        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                        bottomSheet.setContentView(R.layout.generic_list)

                        val recycler = bottomSheet.findViewById<RecyclerView>(R.id.download_recyclerview)!!
                        recycler.layoutManager = GridLayoutManager(context, 3)
                        recycler.adapter = IconsSheetAdapter(host)

                        bottomSheet.show()
                        val displayMetrics = DisplayMetrics()
                        host.getHostContext().windowManager.defaultDisplay.getMetrics(displayMetrics)
                        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                        bottomSheet.window!!.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        false
                    }
                }
            }
            "theme_accent" -> {
                (pref as ListPreference).apply {
                    summary = entry
                    setOnPreferenceChangeListener { _, _ ->
                        ThemeUtil.updateThemes()
                        host.refreshUI()
                        true
                    }
                }
            }
            "high_contrast" -> {
                (pref as SwitchPreferenceCompat).apply {
                    setOnPreferenceChangeListener { _, _ ->
                        ThemeUtil.updateThemes()
                        host.refreshUI()
                        true
                    }
                }
            }
            "show_terminal" -> {
                (pref as SwitchPreferenceCompat).apply {
                    setOnPreferenceChangeListener { pref, _ ->
                        val packageManager = context.packageManager
                        val aliasComponentName =
                            ComponentName(context, "com.deniscerri.ytdl.terminalShareAlias")
                        if ((pref as SwitchPreferenceCompat).isChecked){
                            packageManager.setComponentEnabledSetting(aliasComponentName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP)
                        }else{
                            packageManager.setComponentEnabledSetting(aliasComponentName,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP)
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "show_quick_download_share" -> {
                (pref as SwitchPreferenceCompat).apply {
                    setOnPreferenceChangeListener { pref, _ ->
                        val packageManager = context.packageManager
                        val aliasComponentName =
                            ComponentName(context, "com.deniscerri.ytdl.quickDownloadShareAlias")
                        if ((pref as SwitchPreferenceCompat).isChecked){
                            packageManager.setComponentEnabledSetting(aliasComponentName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP)
                        }else{
                            packageManager.setComponentEnabledSetting(aliasComponentName,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP)
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "display_over_apps" -> {
                (pref as SwitchPreferenceCompat).apply {
                    isChecked = Settings.canDrawOverlays(context)
                    setOnPreferenceChangeListener { _, _ ->
                        runCatching {
                            val i = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            host.getHostContext().startActivity(i)
                            host.activityResultDelegate.launch(i) {
                                host.refreshUI()
                            }
                        }
                        true
                    }
                }
            }
            "ignore_battery" -> {
                pref.apply {
                    setOnPreferenceClickListener {
                        val intent = Intent()
                        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        intent.data = Uri.parse("package:" + context.packageName)
                        host.getHostContext().startActivity(intent)
                        true
                    }
                }
            }
            "hide_thumbnails" -> {
                (pref as MultiSelectListPreference).apply {
                    values.filter { it.isNotBlank() }.apply {
                        summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
                    }
                    setOnPreferenceChangeListener { _, newValues ->
                        (newValues as Set<*>).map { it as String }.filter { it.isNotBlank() }.apply {
                            summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "modify_download_card" -> {
                (pref as MultiSelectListPreference).apply {
                    values.filter { it.isNotBlank() }.apply {
                        summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
                    }
                    setOnPreferenceChangeListener { _, newValues ->
                        (newValues as Set<*>).map { it as String }.filter { it.isNotBlank() }.apply {
                            summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "recommendations_home" -> {
                (pref as ListPreference).apply {
                    val s = context.getString(R.string.video_recommendations_summary)
                    summary = if (value.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${entries[entryValues.indexOf(value)]}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        host.hostLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                resultViewModel.deleteAll()
                            }

                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "custom_home_recommendation_url" -> {
                (pref as EditTextPreference).apply {
                    title = "[${context.getString(R.string.video_recommendations)}] ${context.getString(R.string.custom)}"
                    isVisible = preferences.getString("recommendations_home", "") == "custom"

                    setOnPreferenceChangeListener { preference, newValue ->
                        host.hostLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                resultViewModel.deleteAll()
                            }
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "api_key" -> {
                (pref as EditTextPreference).apply {
                    isVisible = preferences.getString("recommendations_home", "") == "yt_api"
                    val s = context.getString(R.string.api_key_summary)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        host.hostLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                resultViewModel.deleteAll()
                            }
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "search_engine" -> {
                (pref as ListPreference).apply {
                    val s = context.getString(R.string.preferred_search_engine_summary)
                    summary = if (value.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${entries[entryValues.indexOf(value)]}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        host.refreshUI()
                        true
                    }
                }
            }
            "swipe_gesture" -> {
                (pref as MultiSelectListPreference).apply {
                    val s = context.getString(R.string.swipe_gestures_summary)
                    if (values.size == entries.size) {
                        summary = "${s}\n[${context.getString(R.string.all)}]"
                    }else if (values.size > 0) {
                        val indexes = entryValues.mapIndexed { index, _ -> index }
                        summary = "${s}\n[${entries.filterIndexed { index, _ -> indexes.contains(index) }.joinToString(", ")}]"
                    }else{
                        summary = s
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        val newValues = newValue as Set<*>
                        if (newValues.size == entries.size) {
                            summary = "${s}\n[${context.getString(R.string.all)}]"
                        }else if (newValues.isNotEmpty()) {
                            val indexes = List(newValues.size) { index -> index }
                            summary = "${s}\n[${entries.filterIndexed { index, _ -> indexes.contains(index) }.joinToString(", ")}]"
                        }else{
                            summary = s
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
        }
    }
}