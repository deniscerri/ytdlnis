package com.deniscerri.ytdl.ui.more.settings.ai

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.llm.LlmProviderConfig
import com.deniscerri.ytdl.core.llm.LlmSettings
import com.deniscerri.ytdl.ui.more.settings.BaseSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.SettingsRegistry
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AiAssistantSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.ai_assistant

    private lateinit var settings: LlmSettings

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceXMLRes = R.xml.ai_assistant_preferences
        setPreferencesFromResource(preferenceXMLRes, rootKey)
        SettingsRegistry.bindFragment(this, preferenceXMLRes)
        settings = LlmSettings(requireContext())

        findPreference<Preference>("llm_add_provider")?.setOnPreferenceClickListener {
            LlmProviderDialog.showAdd(requireContext(), lifecycleScope, settings) { refreshProviders() }
            true
        }
        refreshProviders()
    }

    private fun refreshProviders() {
        val category = findPreference<PreferenceCategory>("llm_providers") ?: return
        category.removeAll()
        val providers = settings.providers()
        val active = settings.activeProvider()
        if (providers.isEmpty()) {
            category.addPreference(Preference(requireContext()).apply {
                summary = getString(R.string.ai_no_providers)
                isSelectable = false
                isIconSpaceReserved = true
            })
            return
        }
        providers.sortedBy { it.name.lowercase() }.forEach { provider ->
            val isActive = provider.id == active?.id
            category.addPreference(Preference(requireContext()).apply {
                key = "llm_provider_${provider.id}"
                title = provider.name
                summary = listOfNotNull(
                    provider.model.ifBlank { null },
                    provider.baseUrl.toHttpUrlOrNull()?.host,
                    if (isActive) getString(R.string.ai_active) else null,
                ).joinToString(" · ")
                if (isActive) setIcon(R.drawable.ic_check) else isIconSpaceReserved = true
                setOnPreferenceClickListener {
                    showProviderActions(provider)
                    true
                }
            })
        }
    }

    private fun showProviderActions(provider: LlmProviderConfig) {
        val actions = arrayOf(
            getString(R.string.ai_use_provider),
            getString(R.string.ai_edit_provider),
            getString(R.string.ai_delete_provider),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(provider.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        settings.setActive(provider.id)
                        refreshProviders()
                    }
                    1 -> LlmProviderDialog.showEdit(requireContext(), lifecycleScope, settings, provider) { refreshProviders() }
                    2 -> UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.ai_delete_provider), provider.name) {
                        settings.delete(provider.id)
                        refreshProviders()
                    }
                }
            }
            .show()
    }
}
