package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.preference.Preference

interface SettingHost {
    fun findPref(key: String): Preference?
    fun refreshUI()
    fun getHostContext(): Activity
    val hostLifecycleOwner: LifecycleOwner
    val hostViewModelStoreOwner: ViewModelStoreOwner
    val activityResultDelegate: PreferenceActivityResultDelegate
    val hostView: View?
    fun requestGetParentFragmentManager(): FragmentManager
    fun requestRecreateActivity()
    fun requestNavigate(id: Int)
}