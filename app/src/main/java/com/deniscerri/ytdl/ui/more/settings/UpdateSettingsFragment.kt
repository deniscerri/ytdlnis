package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UiUtil.showShortcutsSheet
import com.deniscerri.ytdl.util.UpdateUtil
import com.deniscerri.ytdl.util.extractors.YTDLPUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


class UpdateSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.updating
    private var updateYTDL: Preference? = null
    private var ytdlVersion: Preference? = null
    private var ytdlSource: Preference? = null
    private var updateUtil: UpdateUtil? = null
    private var version: Preference? = null
    private lateinit var preferences: SharedPreferences
    private lateinit var ytdlpUtil: YTDLPUtil


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.updating_preferences, rootKey)
        updateUtil = UpdateUtil(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        updateYTDL = findPreference("update_ytdl")
        ytdlVersion = findPreference("ytdl-version")
        ytdlSource = findPreference("ytdlp_source_label")
        ytdlpUtil = YTDLPUtil(requireContext())
        setYTDLPVersion()

        ytdlSource?.apply {
            summary = preferences.getString("ytdlp_source_label", "")
            setOnPreferenceClickListener {
                UiUtil.showYTDLSourceBottomSheet(requireActivity(), preferences) { t, r ->
                    summary = t
                    preferences.edit().putString("ytdlp_source", r).apply()
                    preferences.edit().putString("ytdlp_source_label", t).apply()
                    initYTDLUpdate(r)
                }
                true
            }
        }

        ytdlVersion!!.setOnPreferenceClickListener {
            initYTDLUpdate()
            true
        }
        
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                initYTDLUpdate()
                true
            }


        findPreference<Preference>("changelog")?.setOnPreferenceClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                updateUtil?.showChangeLog(requireActivity())
            }
            true
        }


        version = findPreference("version")
        val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir
        version!!.summary = "${BuildConfig.VERSION_NAME} [${nativeLibraryDir?.split("/lib/")?.get(1)}]"
        version!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch{
                    withContext(Dispatchers.IO){
                        updateUtil!!.updateApp{ msg ->
                            lifecycleScope.launch(Dispatchers.Main){
                                view?.apply {
                                    Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                }
                true
            }

    }

    private fun setYTDLPVersion() {
        lifecycleScope.launch {
            ytdlVersion!!.summary = ""
            val version = withContext(Dispatchers.IO){
                ytdlpUtil.getVersion()
            }
            preferences.edit().apply {
                putString("ytdl-version", version)
                apply()
            }
            ytdlVersion!!.summary = version
        }
    }

    private fun initYTDLUpdate(channel: String? = null) = lifecycleScope.launch {
        Snackbar.make(requireView(),
            requireContext().getString(R.string.ytdl_updating_started),
            Snackbar.LENGTH_LONG).show()
        runCatching {
            val res = updateUtil!!.updateYoutubeDL(channel)
            when (res.status) {
                UpdateUtil.YTDLPUpdateStatus.DONE -> {
                    Snackbar.make(requireView(), res.message, Snackbar.LENGTH_LONG).show()
                    setYTDLPVersion()
                }
                UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE -> Snackbar.make(requireView(),
                    requireContext().getString(R.string.you_are_in_latest_version),
                    Snackbar.LENGTH_LONG).show()
                UpdateUtil.YTDLPUpdateStatus.ERROR -> {
                    val msg = res.message
                    view?.apply {
                        val snackBar = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
                        snackBar.setAction(R.string.copy_log){
                            UiUtil.copyToClipboard(msg, requireActivity())
                        }
                        val snackbarView: View = snackBar.view
                        val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                        snackTextView.maxLines = 9999999
                        snackBar.show()
                    }
                }
                else -> {

                }
            }
        }.onFailure {
            val msg = it.message ?: requireContext().getString(R.string.errored)
            view?.apply {
                val snackBar = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
                snackBar.setAction(R.string.copy_log){
                    UiUtil.copyToClipboard(msg, requireActivity())
                }
                val snackbarView: View = snackBar.view
                val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                snackTextView.maxLines = 9999999
                snackBar.show()
            }

        }
    }


}