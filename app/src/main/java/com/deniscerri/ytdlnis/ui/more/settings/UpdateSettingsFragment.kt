package com.deniscerri.ytdlnis.ui.more.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.launch


class UpdateSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.updating
    private var updateYTDL: Preference? = null
    private var ytdlVersion: Preference? = null
    private var updateUtil: UpdateUtil? = null


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.updating_preferences, rootKey)
        updateUtil = UpdateUtil(requireContext())
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = preferences.edit()
        updateYTDL = findPreference("update_ytdl")
        ytdlVersion = findPreference("ytdl-version")

        YoutubeDL.getInstance().version(context)?.let {
            editor.putString("ytdl-version", it)
            editor.apply()
            ytdlVersion!!.summary = it
        }
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    Snackbar.make(requireView(),
                        requireContext().getString(R.string.ytdl_updating_started),
                        Snackbar.LENGTH_LONG).show()
                    when (updateUtil!!.updateYoutubeDL()) {
                        YoutubeDL.UpdateStatus.DONE -> {
                            Snackbar.make(requireView(),
                                requireContext().getString(R.string.ytld_update_success),
                                Snackbar.LENGTH_LONG).show()

                            YoutubeDL.getInstance().version(context)?.let {
                                editor.putString("ytdl-version", it)
                                editor.apply()
                                ytdlVersion!!.summary = it
                            }
                        }
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> Snackbar.make(requireView(),
                            requireContext().getString(R.string.you_are_in_latest_version),
                            Snackbar.LENGTH_LONG).show()
                        else -> {
                            Snackbar.make(requireView(),
                                requireContext().getString(R.string.errored),
                                Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                true
            }

    }


}