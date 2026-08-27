package com.deniscerri.ytdl.ui.more.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.termux.terminal.TerminalSession

class VirtualKeyClient(
    private val session: TerminalSession?,
    private val virtualKeysView: VirtualKeysView? = null
) : VirtualKeysView.IVirtualKeysView {

    companion object {
        private val KEY_ESCAPE_SEQUENCES = mapOf(
            "ESC" to "\u001B",
            "TAB" to "\u0009",
            "HOME" to "\u001B[H",
            "UP" to "\u001B[A",
            "DOWN" to "\u001B[B",
            "LEFT" to "\u001B[D",
            "RIGHT" to "\u001B[C",
            "PGUP" to "\u001B[5~",
            "PGDN" to "\u001B[6~",
            "END" to "\u001B[4~",
            "ENTER" to "\u000D",
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

        // Read active modifier states if VirtualKeysView context is available
        val isCtrlActive = virtualKeysView?.readSpecialButton(SpecialButton.CTRL, true) == true
        val isAltActive = virtualKeysView?.readSpecialButton(SpecialButton.ALT, true) == true

        var payload = KEY_ESCAPE_SEQUENCES[rawKey] ?: rawKey

        if (payload.isEmpty()) return

        // Handle CTRL modifier transformations (e.g. CTRL + c -> ASCII 0x03)
        if (isCtrlActive && payload.length == 1) {
            val char = payload[0]
            if (char in 'a'..'z') {
                payload = (char.code - 'a'.code + 1).toChar().toString()
            } else if (char in 'A'..'Z') {
                payload = (char.code - 'A'.code + 1).toChar().toString()
            }
        }

        // Handle ALT modifier transformations (prepends ESC prefix)
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
        // Return false so VirtualKeysView handles standard system haptic feedback
        return false
    }
}