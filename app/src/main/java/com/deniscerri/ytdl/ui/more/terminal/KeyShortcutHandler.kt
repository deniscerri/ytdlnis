package com.deniscerri.ytdl.ui.more.terminal

import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel

object KeyShortcutHandler {

    fun handle(keyCode: Int, event: KeyEvent, activity: TerminalActivity): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance)
        for (action in ShortcutAction.entries) {
            val raw = preferences.getString(action.prefKey, action.default.serialize())!!
            val binding = ShortcutBinding.deserialize(raw)
            if (binding.matches(event)) {
                return dispatch(action, activity)
            }
        }
        return false
    }

    private fun dispatch(action: ShortcutAction, activity: TerminalActivity): Boolean {
        val terminalViewModel = ViewModelProvider(activity)[TerminalViewModel::class.java]

        return when (action) {
            ShortcutAction.PASTE -> handlePaste(terminalViewModel)
//            ShortcutAction.NEW_SESSION -> handleNewSession(activity, terminalViewModel)
//            ShortcutAction.CLOSE_SESSION -> handleCloseSession(activity, terminalViewModel)
//            ShortcutAction.SWITCH_SESSION_PREV -> handleSwitchSession(activity, terminalViewModel, forward = false)
//            ShortcutAction.SWITCH_SESSION_NEXT -> handleSwitchSession(activity, terminalViewModel, forward = true)
        }
    }


    private fun handlePaste(viewModel: TerminalViewModel): Boolean {
        val clipboard: ClipboardManager = App.instance.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            val clipText = clip.toString()
            if (clipText.trim().isNotEmpty()) {
                viewModel.terminalView?.mEmulator?.paste(clipText)
            }
        }
        return true
    }

    fun generateUniqueSessionId(activity: TerminalActivity): String {
        val binder = activity.terminalViewModel.sessionBinder ?: return ""
        val service = binder.getService()
        val existingIds = service.sessionList.keys.toList()
        var index = 1
        var newId: String
        do {
            newId = "main$index"
            index++
        } while (newId in existingIds)
        return newId
    }
}