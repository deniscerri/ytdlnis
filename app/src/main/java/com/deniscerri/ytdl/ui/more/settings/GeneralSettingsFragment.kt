package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.databinding.NavOptionsItemBinding
import com.deniscerri.ytdl.ui.adapter.IconsSheetAdapter
import com.deniscerri.ytdl.ui.adapter.NavBarOptionsAdapter
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Fragment for general/appearance settings (language, theme, navigation, etc.)
class GeneralSettingsFragment : SearchableSettingsFragment() {
    override val title: Int = R.string.general
    private lateinit var preferences: SharedPreferences
    private lateinit var resultViewModel: ResultViewModel

    private var updateUtil: UpdateUtil? = null

    @SuppressLint("BatteryLife")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_preferences, rootKey)
        buildPreferenceList(preferenceScreen)
        NavbarUtil.init(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        updateUtil = UpdateUtil(requireContext())
        val editor = preferences.edit()

        // Language selection
        findPreference<ListPreference>("app_language")?.apply {
            value = Locale.getDefault().language
            summary = Locale.getDefault().displayLanguage

            setOnPreferenceChangeListener { _, newValue ->
                if (newValue == "system") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(null))
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()))
                }
                summary = Locale.getDefault().displayLanguage
                true
            }
        }

        // Label visibility (bottom navigation labels) – hide on side‑nav devices
        findPreference<Preference>("label_visibility")?.apply {
            isVisible = !resources.getBoolean(R.bool.uses_side_nav)
            setOnPreferenceChangeListener { _, _ ->
                ThemeUtil.recreateMain()
                true
            }
        }

        // Navigation bar order and visibility
        findPreference<Preference>("navigation_bar")?.apply {
            isVisible = !resources.getBoolean(R.bool.uses_side_nav)
            if (isVisible) {
                summary = NavbarUtil.getNavBarItems(requireContext()).filter { it.isVisible }.map { it.title }.joinToString(", ")
            }
            setOnPreferenceClickListener {
                val binding = requireActivity().layoutInflater.inflate(R.layout.simple_options_recycler, null)
                val options = NavbarUtil.getNavBarItems(requireContext())

                val optionsRecycler = binding.findViewById<RecyclerView>(R.id.options_recycler)

                val onItemClick = object : NavBarOptionsAdapter.OnItemClickListener {
                    override fun onNavBarOptionDeselected(item: NavOptionsItemBinding) {
                        optionsRecycler.findViewHolderForLayoutPosition(0)?.apply {
                            (this as NavBarOptionsAdapter.NavBarOptionsViewHolder).apply {
                                this.binding.home.performClick()
                            }
                        }
                    }
                }

                val adapter = NavBarOptionsAdapter(
                    options.toMutableList(),
                    NavbarUtil.getStartFragmentId(requireContext()),
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

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                }

                optionsRecycler.layoutManager = LinearLayoutManager(context)
                optionsRecycler.adapter = adapter
                ItemTouchHelper(itemTouchCallback).attachToRecyclerView(optionsRecycler)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.navigation_bar)
                    .setView(binding)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        NavbarUtil.setNavBarItems(adapter.items, requireContext())
                        NavbarUtil.setStartFragment(adapter.selectedHomeTabId)
                        summary = adapter.items.filter { it.isVisible }.map { it.title }.joinToString(", ")
                        ThemeUtil.recreateMain()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }

        // Theme (light/dark/system) – shows confirmation dialog
        findPreference<ListPreference>("ytdlnis_theme")?.apply {
            summary = entry
            setOnPreferenceChangeListener { _, newValue ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.app_icon_change))
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                    .setPositiveButton(R.string.ok) { _, _ ->
                        summary = when (newValue) {
                            "System" -> getString(R.string.system)
                            "Dark"   -> getString(R.string.dark)
                            else     -> getString(R.string.light)
                        }
                        editor.putString("ytdlnis_theme", newValue.toString()).apply()
                        ThemeUtil.updateThemes()
                    }
                    .show()
                false
            }
        }

        // App icon – opens bottom sheet with grid of available icons
        findPreference<Preference>("ytdlnis_icon")?.apply {
            val currentValue = preferences.getString("ytdlnis_icon", "default")
            IconsSheetAdapter.availableIcons.firstOrNull { it.activityAlias == currentValue }?.let {
                summary = getString(it.nameResource)
            }

            setOnPreferenceClickListener {
                val bottomSheet = BottomSheetDialog(requireContext())
                bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                bottomSheet.setContentView(R.layout.generic_list)

                val recycler = bottomSheet.findViewById<RecyclerView>(R.id.download_recyclerview)!!
                recycler.layoutManager = GridLayoutManager(requireContext(), 3)
                recycler.adapter = IconsSheetAdapter(requireActivity())

                bottomSheet.show()
                val displayMetrics = DisplayMetrics()
                requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                bottomSheet.window!!.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                false
            }
        }

        // Accent color – applies immediately
        findPreference<ListPreference>("theme_accent")?.apply {
            summary = entry
            setOnPreferenceChangeListener { _, _ ->
                ThemeUtil.updateThemes()
                true
            }
        }

        // High contrast mode – applies immediately
        findPreference<SwitchPreferenceCompat>("high_contrast")?.apply {
            setOnPreferenceChangeListener { _, _ ->
                ThemeUtil.updateThemes()
                true
            }
        }

        // Show terminal share alias – enable/disable activity alias
        findPreference<SwitchPreferenceCompat>("show_terminal")?.apply {
            setOnPreferenceChangeListener { pref, _ ->
                val packageManager = requireContext().packageManager
                val aliasComponentName = ComponentName(requireContext(), "com.deniscerri.ytdl.terminalShareAlias")
                if ((pref as SwitchPreferenceCompat).isChecked) {
                    packageManager.setComponentEnabledSetting(
                        aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else {
                    packageManager.setComponentEnabledSetting(
                        aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                true
            }
        }

        // Show quick download share alias – enable/disable activity alias
        findPreference<SwitchPreferenceCompat>("show_quick_download_share")?.apply {
            setOnPreferenceChangeListener { pref, _ ->
                val packageManager = requireContext().packageManager
                val aliasComponentName = ComponentName(requireContext(), "com.deniscerri.ytdl.quickDownloadShareAlias")
                if ((pref as SwitchPreferenceCompat).isChecked) {
                    packageManager.setComponentEnabledSetting(
                        aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else {
                    packageManager.setComponentEnabledSetting(
                        aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                true
            }
        }

        // Display over apps permission – opens system settings
        findPreference<SwitchPreferenceCompat>("display_over_apps")?.apply {
            isChecked = Settings.canDrawOverlays(requireContext())
            setOnPreferenceChangeListener { _, _ ->
                runCatching {
                    val i = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireContext().packageName)
                    )
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                    displayOverAppsResultLauncher.launch(i)
                }
                true
            }
        }

        // Ignore battery optimizations – opens system settings
        findPreference<Preference>("ignore_battery")?.apply {
            setOnPreferenceClickListener {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:" + requireContext().packageName)
                }
                startActivity(intent)
                true
            }
        }

        // Hide thumbnails in selected screens
        findPreference<MultiSelectListPreference>("hide_thumbnails")?.apply {
            values.filter { it.isNotBlank() }.apply {
                summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
            }
            setOnPreferenceChangeListener { _, newValues ->
                (newValues as Set<*>).map { it as String }.filter { it.isNotBlank() }.apply {
                    summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
                }
                true
            }
        }

        // Modify download card – which buttons to show
        findPreference<MultiSelectListPreference>("modify_download_card")?.apply {
            values.filter { it.isNotBlank() }.apply {
                summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
            }
            setOnPreferenceChangeListener { _, newValues ->
                (newValues as Set<*>).map { it as String }.filter { it.isNotBlank() }.apply {
                    summary = joinToString(", ") { entries[entryValues.indexOf(it)] }
                }
                true
            }
        }

        // Home recommendations source
        findPreference<ListPreference>("recommendations_home")?.apply {
            val s = getString(R.string.video_recommendations_summary)
            summary = if (value.isNullOrBlank()) s else "${s}\n[${entries[entryValues.indexOf(value)]}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) {
                    s
                } else {
                    "${s}\n[${entries[entryValues.indexOf(newValue)]}]"
                }

                findPreference<EditTextPreference>("api_key")?.isVisible = newValue == "yt_api"
                findPreference<EditTextPreference>("custom_home_recommendation_url")?.isVisible = newValue == "custom"

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        resultViewModel.deleteAll()
                    }
                }
                true
            }
        }

        // Custom URL for home recommendations
        findPreference<EditTextPreference>("custom_home_recommendation_url")?.apply {
            title = "[${getString(R.string.video_recommendations)}] ${getString(R.string.custom)}"
            isVisible = preferences.getString("recommendations_home", "") == "custom"

            setOnPreferenceChangeListener { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        resultViewModel.deleteAll()
                    }
                }
                true
            }
        }

        // API key for YouTube recommendations
        findPreference<EditTextPreference>("api_key")?.apply {
            isVisible = preferences.getString("recommendations_home", "") == "yt_api"
            val s = getString(R.string.api_key_summary)
            summary = if (text.isNullOrBlank()) s else "${s}\n[${text}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${newValue}]"

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        resultViewModel.deleteAll()
                    }
                }
                true
            }
        }

        // Search engine preference
        findPreference<ListPreference>("search_engine")?.apply {
            val s = getString(R.string.preferred_search_engine_summary)
            summary = if (value.isNullOrBlank()) s else "${s}\n[${entries[entryValues.indexOf(value)]}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${entries[entryValues.indexOf(newValue)]}]"
                true
            }
        }

        // Swipe gestures – multi‑select
        findPreference<MultiSelectListPreference>("swipe_gesture")?.apply {
            val s = getString(R.string.swipe_gestures_summary)
            summary = when {
                values.size == entries.size -> "${s}\n[${getString(R.string.all)}]"
                values.isNotEmpty() -> {
                    val indexes = entryValues.mapIndexed { index, _ -> index }
                    "${s}\n[${entries.filterIndexed { index, _ -> indexes.contains(index) }.joinToString(", ")}]"
                }
                else -> s
            }
            setOnPreferenceChangeListener { _, newValue ->
                val newValues = newValue as Set<*>
                summary = when {
                    newValues.size == entries.size -> "${s}\n[${getString(R.string.all)}]"
                    newValues.isNotEmpty() -> {
                        val indexes = List(newValues.size) { it }
                        "${s}\n[${entries.filterIndexed { index, _ -> indexes.contains(index) }.joinToString(", ")}]"
                    }
                    else -> s
                }
                true
            }
        }

        // Reset all preferences in this screen
        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, R.xml.general_preferences)
                ThemeUtil.updateThemes()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!, true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }

    // When returning to the fragment, hide "ignore_battery" if already ignored.
    override fun onResume() {
        val packageName = requireContext().packageName
        val pm = requireContext().applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            findPreference<Preference>("ignore_battery")?.isVisible = false
        }
        super.onResume()
    }

    // Launcher for display over apps permission – returns to this fragment after granting/denying
    private var displayOverAppsResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        findNavController().popBackStack(R.id.appearanceSettingsFragment, false)
    }
}