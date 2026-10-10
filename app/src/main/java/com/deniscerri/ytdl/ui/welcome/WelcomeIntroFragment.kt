package com.deniscerri.ytdl.ui.welcome

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.util.BackupSettingsUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WelcomeIntroFragment : Fragment(R.layout.fragment_welcome_intro) {
    private lateinit var getStarted: View
    private lateinit var restoreButton: View
    private lateinit var restoreProgress: View

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) restore(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getStarted = view.findViewById(R.id.get_started)
        restoreButton = view.findViewById(R.id.restore_backup)
        restoreProgress = view.findViewById(R.id.restore_progress)

        getStarted.setOnClickListener { (requireActivity() as WelcomeActivity).goTo(1) }
        restoreButton.setOnClickListener { restoreLauncher.launch(arrayOf("*/*")) }
    }

    private fun setBusy(busy: Boolean) {
        getStarted.isEnabled = !busy
        restoreButton.isEnabled = !busy
        restoreProgress.isInvisible = !busy
    }

    private fun restore(uri: Uri) {
        val context = requireContext().applicationContext
        val settingsViewModel = ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
        setBusy(true)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val parsed = BackupSettingsUtil.parse(context, uri)
                val restored = withContext(Dispatchers.IO) {
                    settingsViewModel.restoreData(parsed.data, context)
                }
                check(restored) { getString(R.string.welcome_restore_failed) }
                parsed.data.settings != null
            }.onSuccess { settingsRestored ->
                (activity as? WelcomeActivity)?.onBackupRestored(settingsRestored)
            }.onFailure {
                it.printStackTrace()
                setBusy(false)
                view?.let { v -> Snackbar.make(v, it.message ?: getString(R.string.welcome_restore_failed), Snackbar.LENGTH_LONG).show() }
            }
        }
    }
}
