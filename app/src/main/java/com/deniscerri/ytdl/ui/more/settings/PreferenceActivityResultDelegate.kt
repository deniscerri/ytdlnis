package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R

class PreferenceActivityResultDelegate(caller: ActivityResultCaller) {
    private var pendingCallback : ((result: ActivityResult) -> Unit)? = null

    private val launcher = caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingCallback?.invoke(result)
        }
    }

    fun launch(
        intent: Intent,
        callback: (result: ActivityResult) -> Unit
    ) {
        pendingCallback = callback
        launcher.launch(intent)
    }
}