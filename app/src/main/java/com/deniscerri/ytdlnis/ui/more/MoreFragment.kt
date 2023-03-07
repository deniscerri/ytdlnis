package com.deniscerri.ytdlnis.ui.more

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.deniscerri.ytdlnis.R

class MoreFragment : PreferenceFragmentCompat() {
    private lateinit var mainSharedPreferences: SharedPreferences
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey)
        mainSharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
    }

    companion object {
        const val TAG = "MoreFragment"
    }

    override fun onResume() {
        super.onResume()
        findPreference<Preference>("download_logs")!!.isVisible =
            mainSharedPreferences.getBoolean("log_downloads", false)
    }
}