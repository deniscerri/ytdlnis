package com.deniscerri.ytdl.update

import android.content.DialogInterface
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.NetworkMonitor
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The update prompt, attached once to [com.deniscerri.ytdl.MainActivity]. It owns a single dialog
 * and drives it off [UpdateRegistry], so a config change, a dismissal, or leaving the app never
 * loses an in-flight download: the foreground service keeps going and re-entering re-reads the same
 * flows and picks the dialog up where it left off.
 *
 * Whether it appears *on its own* is [UpdatePrefs.autoPrompt]'s to say. With that off the update is
 * still found, saved and staged exactly as before — this simply waits to be asked, which the
 * settings screen's manual check does through [UpdateRegistry.requestPrompt]. Being asked also
 * outranks a dismissal earlier in the session: asking twice should not be answered with silence.
 */
class UpdateGate(private val activity: AppCompatActivity) {

    private var dialog: AlertDialog? = null
    private var content: View? = null

    /** Answered "Later" this session. Reset the moment something asks for the prompt outright. */
    private var dismissed = false

    /** Gates the auto-install to downloads this session actually watched happen. */
    private var sawDownloading = false
    private var autoInstalled = false

    /**
     * Starts observing. The dialog is torn down whenever the activity stops and rebuilt on the next
     * start from the same state, which is what keeps it from outliving its window.
     */
    fun attach() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    combine(
                        UpdateRegistry.available,
                        UpdateRegistry.state,
                        UpdatePrefs.autoPrompt,
                        UpdateRegistry.promptRequest,
                        ::Snapshot,
                    ).collect(::render)
                } finally {
                    hide()
                }
            }
        }
    }

    private data class Snapshot(
        val update: AppUpdate?,
        val state: UpdateState,
        val autoPrompt: Boolean,
        val asked: Boolean,
    )

    private fun render(snapshot: Snapshot) {
        val (update, state, autoPrompt, asked) = snapshot
        if (asked) dismissed = false

        trackDownload(state)

        if (update == null || !(asked || (autoPrompt && !dismissed))) {
            hide()
            return
        }

        bindBody(content ?: show(update), state)
        bindButtons(state)
    }

    /**
     * Auto-launches the installer the instant a *fresh* download finishes, so the user never has to
     * tap Install twice. Gated on having seen the download in flight, so a relaunch that merely
     * restores a staged APK shows the dialog instead of installing unbidden — and on the install
     * grant, without which we fall through to the manual Install button.
     */
    private fun trackDownload(state: UpdateState) {
        when (state) {
            UpdateState.Connecting, is UpdateState.Downloading -> sawDownloading = true
            is UpdateState.Downloaded ->
                if (sawDownloading && !autoInstalled && UpdateInstaller.canInstall(activity)) {
                    autoInstalled = true
                    activity.startActivity(UpdateInstaller.installIntent(activity, state.apk))
                }
            else -> Unit
        }
    }

    private fun show(update: AppUpdate): View {
        val view = activity.layoutInflater.inflate(R.layout.dialog_app_update, null)
        view.findViewById<TextView>(R.id.update_message).text =
            activity.getString(R.string.update_message, update.versionName)
        view.findViewById<TextView>(R.id.update_notes).apply {
            text = update.notes
            isVisible = update.notes.isNotBlank()
        }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_title)
            .setIcon(R.drawable.ic_update_app)
            .setView(view)
            .setPositiveButton(R.string.update, null)   // wired in bindButtons, which owns the
            .setNegativeButton(R.string.update_later, null)    // label and whether it dismisses at all
            .show()
        content = view
        return view
    }

    private fun bindBody(view: View, state: UpdateState) {
        val progress = view.findViewById<LinearProgressIndicator>(R.id.update_progress)
        val log = view.findViewById<TextView>(R.id.update_log)

        when (state) {
            UpdateState.Connecting -> {
                progress.isVisible = true
                progress.isIndeterminate = true
                log.isVisible = true
                log.text = activity.getString(R.string.update_connecting)
                log.setTextColor(activity.colorOnSurfaceVariant())
            }
            is UpdateState.Downloading -> {
                progress.isVisible = true
                val fraction = state.progress
                if (fraction == null) {
                    progress.isIndeterminate = true
                } else {
                    progress.isIndeterminate = false
                    progress.setProgressCompat((fraction * 100).toInt(), true)
                }
                log.isVisible = state.log.isNotBlank()
                log.text = state.log
                log.setTextColor(activity.colorOnSurfaceVariant())
            }
            is UpdateState.Error -> {
                progress.isVisible = false
                log.isVisible = true
                log.text = state.message
                log.setTextColor(activity.colorError())
            }
            else -> {
                progress.isVisible = false
                log.isVisible = false
            }
        }
    }

    /**
     * One reading of the state decides every button, so the dialog can never offer two answers to
     * the same question. A download in flight offers none — the notification's Stop action is the
     * way out of it, and it keeps running whether this dialog is open or not.
     */
    private fun bindButtons(state: UpdateState) {
        val dialog = dialog ?: return
        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        val downloading = state is UpdateState.Connecting || state is UpdateState.Downloading

        // Only the buttons close this dialog: a stray tap outside must not look like an answer.
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        negative.isVisible = !downloading
        negative.setOnClickListener { dismiss() }

        positive.isVisible = !downloading
        when (state) {
            is UpdateState.Downloaded -> {
                positive.setText(R.string.update_install)
                positive.setOnClickListener { install(state) }
            }
            is UpdateState.Error -> {
                positive.setText(R.string.update_retry)
                positive.setOnClickListener { startDownload() }
            }
            else -> {
                positive.setText(R.string.update)
                positive.setOnClickListener { startDownload() }
            }
        }
    }

    /** Grant present → launch the installer; otherwise send the user to grant it once. */
    private fun install(state: UpdateState.Downloaded) {
        if (UpdateInstaller.canInstall(activity)) {
            activity.startActivity(UpdateInstaller.installIntent(activity, state.apk))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(UpdateInstaller.requestPermissionIntent(activity))
        }
    }

    /**
     * Starts the download only when online; otherwise surfaces "connect to the internet" through
     * the Error state, which is also what flips the button to Retry.
     */
    private fun startDownload() {
        if (NetworkMonitor.isOnline(activity)) UpdateService.start(activity)
        else UpdateRegistry.update(UpdateState.Error(activity.getString(R.string.update_offline)))
    }

    /** The user answered "Later" — which also answers whatever asked for the prompt. */
    private fun dismiss() {
        dismissed = true
        UpdateRegistry.clearPromptRequest()
        hide()
    }

    /** Tears the window down without answering anything, so the next start can rebuild it. */
    private fun hide() {
        dialog?.dismiss()
        dialog = null
        content = null
    }

    private fun AppCompatActivity.colorOnSurfaceVariant() =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

    private fun AppCompatActivity.colorError() =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0)
}
