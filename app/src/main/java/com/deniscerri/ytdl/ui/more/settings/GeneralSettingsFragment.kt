package com.deniscerri.ytdl.ui.more.settings

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
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.forEach
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.NavOptionsItemBinding
import com.deniscerri.ytdl.ui.adapter.NavBarOptionsAdapter
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale


class GeneralSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.general

    private var language: ListPreference? = null
    private var theme: ListPreference? = null
    private var accent: ListPreference? = null
    private var highContrast: SwitchPreferenceCompat? = null
    private var locale: ListPreference? = null
    private var showTerminalShareIcon: SwitchPreferenceCompat? = null
    private var ignoreBatteryOptimization: Preference? = null
    private var displayOverApps: SwitchPreferenceCompat? = null
    private lateinit var preferences: SharedPreferences

    private var updateUtil: UpdateUtil? = null
    private var activeDownloadCount = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_preferences, rootKey)
        NavbarUtil.init(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        updateUtil = UpdateUtil(requireContext())
        val editor = preferences.edit()

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }

        language = findPreference("app_language")
        theme = findPreference("ytdlnis_theme")
        accent = findPreference("theme_accent")
        highContrast = findPreference("high_contrast")
        locale = findPreference("locale")
        showTerminalShareIcon = findPreference("show_terminal")

        if(language!!.value == null) language!!.value = Locale.getDefault().language

        language!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()))
                true
            }

        findPreference<Preference>("label_visibility")?.apply {
            isVisible = !resources.getBoolean(R.bool.uses_side_nav)
            setOnPreferenceChangeListener { preference, newValue ->
                restartApp()
                true
            }
        }

        findPreference<Preference>("navigation_bar")?.apply {
            isVisible = !resources.getBoolean(R.bool.uses_side_nav)
            setOnPreferenceClickListener {
                val binding = requireActivity().layoutInflater.inflate(R.layout.simple_options_recycler, null)
                val options = NavbarUtil.getNavBarItems(requireContext())

                val optionsRecycler = binding.findViewById<RecyclerView>(R.id.options_recycler)
                var adapter : NavBarOptionsAdapter? = null;

                val onitemClick = object: NavBarOptionsAdapter.OnItemClickListener {
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
                    NavbarUtil.getStartFragmentId(requireContext()),
                    onitemClick
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

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.navigation_bar)
                    .setView(binding)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        NavbarUtil.setNavBarItems(adapter.items, requireContext())
                        NavbarUtil.setStartFragment(adapter.selectedHomeTabId)
                        restartApp()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }


        theme!!.summary = theme!!.entry
        theme!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                when(newValue){
                    "System" -> {
                        theme!!.summary = getString(R.string.system)
                    }
                    "Dark" -> {
                        theme!!.summary = getString(R.string.dark)
                    }
                    else -> {
                        theme!!.summary = getString(R.string.light)
                    }
                }
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        accent!!.summary = accent!!.entry
        accent!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        highContrast!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
                requireActivity().finishAffinity()

                true
            }

        showTerminalShareIcon!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { pref: Preference?, _: Any ->
                val packageManager = requireContext().packageManager
                val aliasComponentName = ComponentName(requireContext(), "com.deniscerri.ytdl.terminalShareAlias")
                if ((pref as SwitchPreferenceCompat).isChecked){
                    packageManager.setComponentEnabledSetting(aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP)
                }else{
                    packageManager.setComponentEnabledSetting(aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP)
                }
                true
            }

        displayOverApps = findPreference("display_over_apps")
        displayOverApps?.isChecked = Settings.canDrawOverlays(requireContext())
        displayOverApps?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
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

        ignoreBatteryOptimization = findPreference("ignore_battery")
        ignoreBatteryOptimization!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
                true
            }

        findPreference<Preference>("piped_instance")?.setOnPreferenceClickListener {
            UiUtil.showPipedInstancesDialog(requireActivity(), preferences.getString("piped_instance", "")!!){
                editor.putString("piped_instance", it)
                editor.apply()
            }
            true
        }
    }

    override fun onResume() {
        val packageName: String = requireContext().packageName
        val pm = requireContext().applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ignoreBatteryOptimization!!.isVisible = false
        }
        super.onResume()
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requireActivity().finishAffinity()
        activity?.finishAffinity()
    }

    private var displayOverAppsResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        findNavController().popBackStack(R.id.appearanceSettingsFragment, false)
    }


}