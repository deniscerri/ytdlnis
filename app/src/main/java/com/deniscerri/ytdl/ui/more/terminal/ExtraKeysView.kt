package com.deniscerri.ytdl.ui.more.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.ToggleButton
import com.deniscerri.ytdl.R
import com.termux.view.TerminalView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * A view showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboard.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    open class CleverMap<K, V> : HashMap<K, V>() {
        fun get(key: K, defaultValue: V): V {
            return super.get(key) ?: defaultValue
        }
    }

    class CharDisplayMap : CleverMap<String, String>()

    enum class SpecialButton {
        CTRL, ALT, FN
    }

    private class SpecialButtonState {
        var isOn: Boolean = false
        var button: ToggleButton? = null
    }

    private val specialButtons: Map<SpecialButton, SpecialButtonState> = mapOf(
        SpecialButton.CTRL to SpecialButtonState(),
        SpecialButton.ALT to SpecialButtonState(),
        SpecialButton.FN to SpecialButtonState()
    )

    private var scheduledExecutor: ScheduledExecutorService? = null
    private var popupWindow: PopupWindow? = null
    private var longPressCount = 0

    fun readSpecialButton(name: SpecialButton): Boolean {
        val state = specialButtons[name] ?: throw IllegalArgumentException("Must be a valid special button")

        if (!state.isOn) return false
        val button = state.button ?: return false

        if (button.isPressed) return true
        if (!button.isChecked) return false

        button.isChecked = false
        button.setTextColor(TEXT_COLOR)
        return true
    }

    fun popup(view: View, text: String) {
        val width = view.measuredWidth
        val height = view.measuredHeight
        val button = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            setText(text)
            setTextColor(TEXT_COLOR)
            setPadding(0, 0, 0, 0)
            minHeight = 0
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            this.width = width
            this.height = height
            setBackgroundColor(BUTTON_PRESSED_COLOR)
        }

        popupWindow = PopupWindow(this).apply {
            setWidth(LayoutParams.WRAP_CONTENT)
            setHeight(LayoutParams.WRAP_CONTENT)
            contentView = button
            isOutsideTouchable = true
            isFocusable = false
            showAsDropDown(view, 0, -2 * height)
        }
    }

    /**
     * Applies the 'controlCharsAliases' mapping to all the strings in *buttons*
     */
    fun replaceAliases(buttons: Array<Array<String>>) {
        for (i in buttons.indices) {
            for (j in buttons[i].indices) {
                buttons[i][j] = controlCharsAliases.get(buttons[i][j], buttons[i][j])
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun reload(buttons: Array<Array<String>>, charDisplayMap: CharDisplayMap) {
        for (state in specialButtons.values) {
            state.button = null
        }

        removeAllViews()
        replaceAliases(buttons)

        val rows = buttons.size
        val cols = maximumLength(buttons)

        rowCount = rows
        columnCount = cols

        val specialKeys = setOf("CTRL", "ALT", "FN")
        val arrowKeys = setOf("UP", "DOWN", "LEFT", "RIGHT")
        val popupKeys = setOf("/", "-")

        for (row in 0 until rows) {
            for (col in buttons[row].indices) {
                val buttonText = buttons[row][col]

                val button: Button
                if (specialKeys.contains(buttonText)) {
                    val state = specialButtons[SpecialButton.valueOf(buttonText)]!!
                    state.isOn = true
                    state.button = ToggleButton(context, null, android.R.attr.buttonBarButtonStyle)
                    button = state.button!!
                    button.isClickable = true
                } else {
                    button = Button(context, null, android.R.attr.buttonBarButtonStyle)
                }

                val displayedText = charDisplayMap.get(buttonText, buttonText)
                button.text = displayedText
                button.setTextColor(TEXT_COLOR)
                button.setPadding(0, 0, 0, 0)

                button.setOnClickListener {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val root = rootView
                    if (specialKeys.contains(buttonText)) {
                        val self = button as ToggleButton
                        self.setTextColor(if (self.isChecked) INTERESTING_COLOR else TEXT_COLOR)
                    } else {
                        sendKey(root, buttonText)
                    }
                }

                button.setOnTouchListener { v, event ->
                    val root = rootView
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            longPressCount = 0
                            v.setBackgroundColor(BUTTON_PRESSED_COLOR)
                            if (arrowKeys.contains(buttonText)) {
                                scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
                                scheduledExecutor?.scheduleWithFixedDelay({
                                    longPressCount++
                                    // Ensure UI actions run on Main Thread
                                    post { sendKey(root, buttonText) }
                                }, 400, 80, TimeUnit.MILLISECONDS)
                            }
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (popupKeys.contains(buttonText)) {
                                if (popupWindow == null && event.y < 0) {
                                    v.setBackgroundColor(BUTTON_COLOR)
                                    val text = if ("-" == buttonText) "|" else "\\"
                                    popup(v, text)
                                }
                                if (popupWindow != null && event.y > 0) {
                                    v.setBackgroundColor(BUTTON_PRESSED_COLOR)
                                    popupWindow?.dismiss()
                                    popupWindow = null
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.setBackgroundColor(BUTTON_COLOR)
                            scheduledExecutor?.shutdownNow()
                            scheduledExecutor = null

                            if (longPressCount == 0) {
                                if (popupWindow != null && popupKeys.contains(buttonText)) {
                                    popupWindow?.contentView = null
                                    popupWindow?.dismiss()
                                    popupWindow = null
                                    sendKey(root, if ("-" == buttonText) "|" else "\\")
                                } else {
                                    v.performClick()
                                }
                            }
                            true
                        }

                        else -> true
                    }
                }

                val param = LayoutParams().apply {
                    width = 0
                    height = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                        (37.5 * resources.displayMetrics.density + 0.5).toInt()
                    } else {
                        0
                    }
                    setMargins(0, 0, 0, 0)
                    columnSpec = spec(col, FILL, 1f)
                    rowSpec = spec(row, FILL, 1f)
                }

                button.layoutParams = param
                addView(button)
            }
        }
    }

    companion object {
        private const val TEXT_COLOR = -0x1
        private const val BUTTON_COLOR = 0x00000000
        private const val INTERESTING_COLOR = -0x7f2116
        private const val BUTTON_PRESSED_COLOR = 0x7FFFFFFF

        val controlCharsAliases: CharDisplayMap = CharDisplayMap().apply {
            put("ESCAPE", "ESC")
            put("CONTROL", "CTRL")
            put("RETURN", "ENTER")
            put("FUNCTION", "FN")
            put("LT", "LEFT")
            put("RT", "RIGHT")
            put("DN", "DOWN")
            put("PAGEUP", "PGUP")
            put("PAGE_UP", "PGUP")
            put("PAGE UP", "PGUP")
            put("PAGE-UP", "PGUP")
            put("PAGEDOWN", "PGDN")
            put("PAGE_DOWN", "PGDN")
            put("PAGE-DOWN", "PGDN")
            put("DELETE", "DEL")
            put("BACKSPACE", "BKSP")
            put("BACKSLASH", "\\")
            put("QUOTE", "\"")
            put("APOSTROPHE", "'")
        }

        val keyCodesForString: Map<String, Int> = mapOf(
            "ESC" to KeyEvent.KEYCODE_ESCAPE,
            "TAB" to KeyEvent.KEYCODE_TAB,
            "HOME" to KeyEvent.KEYCODE_MOVE_HOME,
            "END" to KeyEvent.KEYCODE_MOVE_END,
            "PGUP" to KeyEvent.KEYCODE_PAGE_UP,
            "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN,
            "INS" to KeyEvent.KEYCODE_INSERT,
            "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
            "BKSP" to KeyEvent.KEYCODE_DEL,
            "UP" to KeyEvent.KEYCODE_DPAD_UP,
            "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
            "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
            "ENTER" to KeyEvent.KEYCODE_ENTER
        )

        fun sendKey(view: View, keyName: String) {
            val terminalView = view.findViewById<TerminalView>(R.id.terminal_view) ?: return
            val keyCode = keyCodesForString[keyName]

            if (keyCode != null) {
                terminalView.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            } else {
                val session = terminalView.currentSession
                if (session != null && keyName.isNotEmpty()) {
                    session.write(keyName)
                }
            }
        }

        val classicArrowsDisplay: CharDisplayMap = CharDisplayMap().apply {
            put("LEFT", "←")
            put("RIGHT", "→")
            put("UP", "↑")
            put("DOWN", "↓")
        }

        val wellKnownCharactersDisplay: CharDisplayMap = CharDisplayMap().apply {
            put("ENTER", "↲")
            put("TAB", "↹")
            put("BKSP", "⌫")
            put("DEL", "⌦")
        }

        val lessKnownCharactersDisplay: CharDisplayMap = CharDisplayMap().apply {
            put("HOME", "⇱")
            put("END", "⇲")
            put("PGUP", "⇑")
            put("PGDN", "⇓")
        }

        val arrowTriangleVariationDisplay: CharDisplayMap = CharDisplayMap().apply {
            put("LEFT", "◀")
            put("RIGHT", "▶")
            put("UP", "▲")
            put("DOWN", "▼")
        }

        val notKnownIsoCharacters: CharDisplayMap = CharDisplayMap().apply {
            put("CTRL", "⎈")
            put("ALT", "⎇")
            put("ESC", "⎋")
        }

        val nicerLookingDisplay: CharDisplayMap = CharDisplayMap().apply {
            put("-", "―")
        }

        val defaultCharDisplay: CharDisplayMap = CharDisplayMap().apply {
            putAll(classicArrowsDisplay)
            putAll(wellKnownCharactersDisplay)
            putAll(nicerLookingDisplay)
        }

        val lotsOfArrowsCharDisplay: CharDisplayMap = CharDisplayMap().apply {
            putAll(classicArrowsDisplay)
            putAll(wellKnownCharactersDisplay)
            putAll(lessKnownCharactersDisplay)
            putAll(nicerLookingDisplay)
        }

        val arrowsOnlyCharDisplay: CharDisplayMap = CharDisplayMap().apply {
            putAll(classicArrowsDisplay)
            putAll(nicerLookingDisplay)
        }

        val fullIsoCharDisplay: CharDisplayMap = CharDisplayMap().apply {
            putAll(classicArrowsDisplay)
            putAll(wellKnownCharactersDisplay)
            putAll(lessKnownCharactersDisplay)
            putAll(nicerLookingDisplay)
            putAll(notKnownIsoCharacters)
        }

        fun maximumLength(matrix: Array<Array<String>>): Int {
            var m = 0
            for (aMatrix in matrix) m = max(m, aMatrix.size)
            return m
        }
    }
}