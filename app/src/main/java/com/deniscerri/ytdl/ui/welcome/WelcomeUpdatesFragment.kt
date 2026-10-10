package com.deniscerri.ytdl.ui.welcome

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.WelcomeViewModel
import com.deniscerri.ytdl.ui.more.settings.updating.setUpChannels
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

class WelcomeUpdatesFragment : Fragment(R.layout.fragment_welcome_updates) {
    private lateinit var viewModel: WelcomeViewModel
    private lateinit var channelGroup: MaterialButtonToggleGroup
    private lateinit var customSourceRow: View
    private lateinit var builtInValues: Array<String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[WelcomeViewModel::class.java]

        val appRow = view.findViewById<View>(R.id.update_app)
        bindSwitch(appRow, R.drawable.ic_update_app, R.string.update_app, R.string.update_app_summary, viewModel.updateApp) {
            viewModel.updateApp = it
        }
        // app updates are only offered by the GitHub build
        appRow.isVisible = BuildConfig.FLAVOR == "github"

        bindSwitch(view.findViewById(R.id.update_ytdl), R.drawable.ic_update, R.string.auto_update_ytdlp, R.string.auto_update_ytdlp_summary, viewModel.autoUpdateYtdlp) {
            viewModel.autoUpdateYtdlp = it
        }

        channelGroup = view.findViewById<View>(R.id.channel_picker).findViewById(R.id.channel_group)
        customSourceRow = view.findViewById(R.id.custom_source)
        setupChannels()
    }

    private fun bindSwitch(
        row: View,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes summary: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val toggle = row.findViewById<MaterialSwitch>(R.id.preference_switch)
        row.findViewById<MaterialButton>(R.id.preference_icon).apply {
            this.icon = ContextCompat.getDrawable(requireContext(), icon)
            isVisible = true
        }
        row.findViewById<TextView>(R.id.preference_title).setText(title)
        row.findViewById<TextView>(R.id.preference_summary).apply {
            setText(summary)
            isVisible = true
        }
        toggle.isChecked = checked
        toggle.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
    }

    private fun setupChannels() {
        val titles = resources.getStringArray(R.array.ytdlp_source)
        builtInValues = resources.getStringArray(R.array.ytdlp_source_values)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // a custom source leaves the group without a selection, same as in the settings
        channelGroup.setUpChannels(builtInValues.zip(titles), { viewModel.ytdlpSource }) { value ->
            viewModel.ytdlpSource = value
            viewModel.ytdlpSourceLabel = titles[builtInValues.indexOf(value)]
            updateCustomSummary()
        }

        customSourceRow.apply {
            findViewById<MaterialButton>(R.id.preference_icon).apply {
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_source_24)
                isVisible = true
            }
            findViewById<TextView>(R.id.preference_title).setText(R.string.custom_channels)
            findViewById<TextView>(R.id.preference_summary).isVisible = true
            setOnClickListener {
                UiUtil.showYTDLCustomSourceBottomSheet(requireActivity(), preferences) { title, source ->
                    viewModel.ytdlpSource = source
                    viewModel.ytdlpSourceLabel = title
                    selectChannelButton(source)
                    updateCustomSummary()
                }
            }
        }
        updateCustomSummary()
    }

    /** Checks the button for [value], or clears the group when it's a custom source. */
    private fun selectChannelButton(value: String) {
        val button = (0 until channelGroup.childCount)
            .map { channelGroup.getChildAt(it) }
            .firstOrNull { it.tag == value }
        if (button != null) channelGroup.check(button.id) else channelGroup.clearChecked()
    }

    private fun updateCustomSummary() {
        val source = viewModel.ytdlpSource
        val summary = when {
            source in builtInValues -> getString(R.string.custom_channels_summary)
            viewModel.ytdlpSourceLabel.isBlank() -> source
            else -> "${viewModel.ytdlpSourceLabel}\n$source"
        }
        customSourceRow.findViewById<TextView>(R.id.preference_summary).text = summary
    }
}
