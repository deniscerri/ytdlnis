package com.deniscerri.ytdl.ui.more.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.termux.terminal.TerminalSession

class VirtualKeysListener(
    private val session: TerminalSession?,
    private val virtualKeysView: VirtualKeysView? = null
) : VirtualKeysView.IVirtualKeysView {

    companion object {
        private val KEY_ESCAPE_SEQUENCES = mapOf(
            "UP" to "\u001B[A",
            "DOWN" to "\u001B[B",
            "LEFT" to "\u001B[D",
            "RIGHT" to "\u001B[C",
            "ENTER" to "\u000D",
            "PGUP" to "\u001B[5~",
            "PGDN" to "\u001B[6~",
            "TAB" to "\u0009",
            "HOME" to "\u001B[H",
            "END" to "\u001B[F",
            "ESC" to "\u001B",
            "DRAWER" to ""
        )
    }

    override fun onVirtualKeyButtonClick(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button
    ) {
        val activeSession = session ?: return
        val rawKey = buttonInfo.key.takeIf { it.isNotEmpty() } ?: return

        // Resolve special modifier states if VirtualKeysView context is available
        val isCtrlActive = virtualKeysView?.readSpecialButton(SpecialButton.CTRL, true) == true
        val isAltActive = virtualKeysView?.readSpecialButton(SpecialButton.ALT, true) == true
        val isFnActive = virtualKeysView?.readSpecialButton(SpecialButton.FN, true) == true

        var payload = KEY_ESCAPE_SEQUENCES[rawKey] ?: rawKey

        if (payload.isEmpty()) return

        // Handle CTRL modifier combination for standard ASCII characters (A-Z -> Control characters)
        if (isCtrlActive && payload.length == 1) {
            val char = payload[0]
            if (char in 'a'..'z') {
                payload = (char.code - 'a'.code + 1).toChar().toString()
            } else if (char in 'A'..'Z') {
                payload = (char.code - 'A'.code + 1).toChar().toString()
            }
        }

        // Handle ALT modifier prefix (send ESC before character sequence)
        if (isAltActive) {
            payload = "\u001B$payload"
        }

        activeSession.write(payload)
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button
    ): Boolean {
        // Return false to allow VirtualKeysView to fall back to standard system haptics
        return false
    }
}