package com.deniscerri.ytdl.ui.more.settings.advanced

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.BaseSettingsFragment
import com.deniscerri.ytdl.util.UiUtil


class AdvancedSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.advanced
    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)
        findPreference<Preference>("yt_player_client")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.youtubePlayerClientFragment)
            false
        }
    }
}