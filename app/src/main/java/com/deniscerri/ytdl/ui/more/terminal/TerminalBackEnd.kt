package com.deniscerri.ytdl.ui.more.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.SpecialButton
import com.termux.shared.view.KeyboardUtils
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.CoroutineScope

class TerminalBackEnd(
    private val terminal: TerminalView,
    private val activity: TerminalActivity,
    private val onFinished: (() -> Unit)? = null
) : TerminalViewClient, TerminalSessionClient {

    private val terminalViewModel by lazy { ViewModelProvider(activity)[TerminalViewModel::class.java] }

    override fun onTextChanged(changedSession: TerminalSession) {
        terminal.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {
        onFinished?.invoke()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard: ClipboardManager = activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard: ClipboardManager = activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            val clipText = clip.getItemAt(0).text.toString()
            if (clipText.trim().isNotEmpty() && terminal.mEmulator != null) {
                terminal.mEmulator.paste(clipText)
            }
        }
    }

    override fun onBell(session: TerminalSession) {
        return
    }

    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: "Terminal", message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "Terminal", message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "Terminal", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "Terminal", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "Terminal", message ?: "") }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "Terminal", message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: "Terminal", "Stack trace", e)
    }

    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(11f, 45f)
        terminal.setTextSize(fontScale.toInt())
        return fontScale
    }

    private val isHardwareKeyboardConnected: Boolean
        get() = Resources.getSystem().configuration.keyboard != Configuration.KEYBOARD_NOKEYS

    override fun onSingleTapUp(e: MotionEvent) {
        if (!(isHardwareKeyboardConnected)) {
            showSoftInput()
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        return KeyShortcutHandler.handle(keyCode, e, activity)
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.CTRL, true) == true

    override fun readAltKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.ALT, true) == true

    override fun readShiftKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.SHIFT, true) == true

    override fun readFnKey(): Boolean =
        terminalViewModel.virtualKeysView?.readSpecialButton(SpecialButton.FN, true) == true

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(true, true)
        }
    }

    private fun showSoftInput() {
        terminal.requestFocus()
        KeyboardUtils.showSoftKeyboard(App.instance,terminal)
    }
}