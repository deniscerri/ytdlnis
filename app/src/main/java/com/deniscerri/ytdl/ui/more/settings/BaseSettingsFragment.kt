package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen


abstract class BaseSettingsFragment : PreferenceFragmentCompat(), SettingHost {
    abstract val title: Int
    override fun findPref(key: String) = findPreference<Preference>(key)
    @SuppressLint("NotifyDataSetChanged")
    override fun refreshUI() {
        listView.adapter?.notifyDataSetChanged()
    }
    override fun getHostContext() = requireActivity()
    override val activityResultDelegate = PreferenceActivityResultDelegate(this)
    override fun requestGetParentFragmentManager() = parentFragmentManager
    override fun requestRecreateActivity() = requireActivity().recreate()
    override fun requestNavigate(id: Int) = findNavController().navigate(id)
    override val hostViewModelStoreOwner by lazy {
        this
    }
    override val hostLifecycleOwner by lazy {
        this
    }
    override val hostView by lazy {
        requireView()
    }
    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    fun getPreferences(p: Preference, list: MutableList<Preference>) : List<Preference> {
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferences(pGroup.getPreference(i), list) // recursive call
            }
        } else {
            list.add(p)
        }
        return list
    }

    fun resetPreferences(editor: SharedPreferences.Editor, key: Int) {
        getPreferences(preferenceScreen, mutableListOf()).forEach {
            editor.remove(it.key)
        }
        editor.apply()
        PreferenceManager.setDefaultValues(requireActivity().applicationContext, key, true)
    }

    //Thanks libretube
    override fun onDisplayPreferenceDialog(preference: Preference) {
        val shownCustomDialog = DefaultPreferenceActions.onPreferenceDisplayDialog(requireActivity(), preference) {}
        if (!shownCustomDialog) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val keyToHighlight = arguments?.getString("highlight_key")
        if (keyToHighlight != null) {
            val adapter = listView.adapter as? PreferenceGroupAdapter
            val position = adapter?.getPreferenceAdapterPosition(keyToHighlight) ?: -1

            if (position != -1) {
                listView.postDelayed({
                    listView.smoothScrollToPosition(position + 1)

                    listView.postDelayed({
                        val holder = listView.findViewHolderForAdapterPosition(position)
                        holder?.itemView?.let { itemView ->
                            val originalColor = itemView.background
                            itemView.setBackgroundColor(requireContext().getColor(android.R.color.system_control_highlight_light))
                            itemView.postDelayed({
                                itemView.background = originalColor
                            }, 1000)
                        }
                    }, 300)
                }, 200)
            }
        }
    }
}