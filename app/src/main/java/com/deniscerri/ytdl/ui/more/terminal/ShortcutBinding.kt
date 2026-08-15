package com.deniscerri.ytdl.ui.more.terminal

import android.view.KeyEvent

/**
 * Represents a configurable keyboard shortcut binding.
 * Stored as a string in SharedPreferences: "modifiers|keyCode"
 * e.g. "CTRL|SHIFT|54" for Ctrl+Shift+V
 */
data class ShortcutBinding(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val keyCode: Int = 0,
) {
    /** Check if this binding is empty (no key assigned) */
    val isEmpty: Boolean get() = keyCode == 0

    /** Check if a KeyEvent matches this binding */
    fun matches(event: KeyEvent): Boolean {
        if (isEmpty) return false
        return event.keyCode == keyCode
                && event.isCtrlPressed == ctrl
                && event.isShiftPressed == shift
                && event.isAltPressed == alt
    }

    /** Serialize to string for SharedPreferences storage */
    fun serialize(): String {
        if (isEmpty) return ""
        val parts = mutableListOf<String>()
        if (ctrl) parts.add("CTRL")
        if (shift) parts.add("SHIFT")
        if (alt) parts.add("ALT")
        parts.add(keyCode.toString())
        return parts.joinToString("|")
    }

    /** Human-readable display string */
    fun toDisplayString(): String {
        if (isEmpty) return "Not set"
        val parts = mutableListOf<String>()
        if (ctrl) parts.add("Ctrl")
        if (shift) parts.add("Shift")
        if (alt) parts.add("Alt")
        parts.add(KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() })
        return parts.joinToString(" + ")
    }

    companion object {
        /** Deserialize from SharedPreferences string */
        fun deserialize(value: String): ShortcutBinding {
            if (value.isBlank()) return ShortcutBinding()
            val parts = value.split("|")
            var ctrl = false
            var shift = false
            var alt = false
            var keyCode = 0
            for (part in parts) {
                when (part) {
                    "CTRL" -> ctrl = true
                    "SHIFT" -> shift = true
                    "ALT" -> alt = true
                    else -> keyCode = part.toIntOrNull() ?: 0
                }
            }
            return ShortcutBinding(ctrl, shift, alt, keyCode)
        }

        /** Create from a KeyEvent (for capture dialog) */
        fun fromKeyEvent(event: KeyEvent): ShortcutBinding {
            return ShortcutBinding(
                ctrl = event.isCtrlPressed,
                shift = event.isShiftPressed,
                alt = event.isAltPressed,
                keyCode = event.keyCode,
            )
        }

        /** Keys that should not be used as shortcut targets */
        private val RESERVED_KEY_CODES = setOf(
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MENU,
        )

        /** Modifier-only key codes (should not finalize a binding) */
        val MODIFIER_KEY_CODES = setOf(
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
        )

        fun isReservedKey(keyCode: Int): Boolean = keyCode in RESERVED_KEY_CODES
        fun isModifierKey(keyCode: Int): Boolean = keyCode in MODIFIER_KEY_CODES
    }
}

/**
 * Defines all available shortcut actions with their default bindings and preference keys.
 */
enum class ShortcutAction(
    val prefKey: String,
    val default: ShortcutBinding,
) {
    PASTE(
        prefKey = "shortcut_paste",
        default = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_V),
    ),
//    NEW_SESSION(
//        prefKey = "shortcut_new_session",
//        default = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_N),
//    ),
//    CLOSE_SESSION(
//        prefKey = "shortcut_close_session",
//        default = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_W),
//    ),
//    SWITCH_SESSION_PREV(
//        prefKey = "shortcut_switch_prev",
//        default = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_DPAD_LEFT),
//    ),
//    SWITCH_SESSION_NEXT(
//        prefKey = "shortcut_switch_next",
//        default = ShortcutBinding(ctrl = true, shift = true, keyCode = KeyEvent.KEYCODE_DPAD_RIGHT),
//    ),
}