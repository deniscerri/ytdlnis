package com.deniscerri.ytdl.ui.more.terminal.virtualkeys

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.GridLayout
import android.widget.PopupWindow
import com.google.android.material.color.MaterialColors
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * A [View] showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboard.
 */
class VirtualKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    companion object {
        const val DEFAULT_BUTTON_TEXT_COLOR = -0x1
        const val DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = -0xbc2c9
        const val DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000
        const val DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = -0x808081

        const val MIN_LONG_PRESS_DURATION = 200
        const val MAX_LONG_PRESS_DURATION = 3000
        const val FALLBACK_LONG_PRESS_DURATION = 400

        const val MIN_LONG_PRESS__REPEAT_DELAY = 5
        const val MAX_LONG_PRESS__REPEAT_DELAY = 2000
        const val DEFAULT_LONG_PRESS_REPEAT_DELAY = 80

        /** General util function to compute the longest column length in a matrix. */
        @JvmStatic
        fun maximumLength(matrix: Array<Array<VirtualKeyButton>>): Int {
            var m = 0
            for (row in matrix) {
                m = max(m, row.size)
            }
            return m
        }
    }

    var virtualKeysViewClient: IVirtualKeysView? = null

    private var _specialButtons: Map<SpecialButton, SpecialButtonState>? = null
    var specialButtons: Map<SpecialButton, SpecialButtonState>?
        get() = _specialButtons?.toMap()
        set(value) {
            _specialButtons = value
            specialButtonsKeys = value?.keys?.map { it.key }?.toSet()
        }

    var specialButtonsKeys: Set<String>? = null
        private set

    private var _repetitiveKeys: List<String>? = null
    var repetitiveKeys: List<String>?
        get() = _repetitiveKeys?.toList()
        set(value) {
            _repetitiveKeys = value
        }

    var buttonTextColor: Int = DEFAULT_BUTTON_TEXT_COLOR
    var buttonActiveTextColor: Int = DEFAULT_BUTTON_ACTIVE_TEXT_COLOR
    var buttonBackgroundColor: Int = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
    var buttonActiveBackgroundColor: Int = DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR
    var isButtonTextAllCaps: Boolean = true

    var longPressTimeout: Int = ViewConfiguration.getLongPressTimeout()
        set(value) {
            field = if (value in MIN_LONG_PRESS_DURATION..MAX_LONG_PRESS_DURATION) {
                value
            } else {
                FALLBACK_LONG_PRESS_DURATION
            }
        }

    var longPressRepeatDelay: Int = DEFAULT_LONG_PRESS_REPEAT_DELAY
        set(value) {
            field = if (value in MIN_LONG_PRESS__REPEAT_DELAY..MAX_LONG_PRESS__REPEAT_DELAY) {
                value
            } else {
                DEFAULT_LONG_PRESS_REPEAT_DELAY
            }
        }

    private var popupWindow: PopupWindow? = null
    private var scheduledExecutor: ScheduledExecutorService? = null
    private var handler: Handler? = null
    private var specialButtonsLongHoldRunnable: SpecialButtonsLongHoldRunnable? = null
    private var longPressCount: Int = 0

    init {
        repetitiveKeys = VirtualKeysConstants.PRIMARY_REPETITIVE_KEYS
        specialButtons = getDefaultSpecialButtons(this)
        setButtonColors(
            DEFAULT_BUTTON_TEXT_COLOR,
            DEFAULT_BUTTON_ACTIVE_TEXT_COLOR,
            DEFAULT_BUTTON_BACKGROUND_COLOR,
            DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR
        )
        longPressTimeout = ViewConfiguration.getLongPressTimeout()
        longPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY
    }

    fun setButtonColors(
        buttonTextColor: Int,
        buttonActiveTextColor: Int,
        buttonBackgroundColor: Int,
        buttonActiveBackgroundColor: Int
    ) {
        this.buttonTextColor = buttonTextColor
        this.buttonActiveTextColor = buttonActiveTextColor
        this.buttonBackgroundColor = buttonBackgroundColor
        this.buttonActiveBackgroundColor = buttonActiveBackgroundColor
    }

    fun getDefaultSpecialButtons(extraKeysView: VirtualKeysView): Map<SpecialButton, SpecialButtonState> {
        return mapOf(
            SpecialButton.CTRL to SpecialButtonState(extraKeysView),
            SpecialButton.ALT to SpecialButtonState(extraKeysView),
            SpecialButton.SHIFT to SpecialButtonState(extraKeysView),
            SpecialButton.FN to SpecialButtonState(extraKeysView)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun reload(extraKeysInfo: VirtualKeysInfo?) {
        if (extraKeysInfo == null) return

        _specialButtons?.values?.forEach { state ->
            state.buttons = ArrayList()
        }

        removeAllViews()

        val buttons = extraKeysInfo.matrix ?: return

        rowCount = buttons.size
        columnCount = maximumLength(buttons)

        for (row in buttons.indices) {
            for (col in buttons[row].indices) {
                val buttonInfo = buttons[row][col]

                val button: Button = if (isSpecialButton(buttonInfo)) {
                    createSpecialButton(buttonInfo.key, true) ?: return
                } else {
                    Button(context, null, R.attr.buttonBarButtonStyle)
                }

                button.text = buttonInfo.display
                button.setTextColor(buttonTextColor)
                button.isAllCaps = isButtonTextAllCaps
                button.setPadding(0, 0, 0, 0)

                button.setOnClickListener { view ->
                    performVirtualKeyButtonHapticFeedback(view, buttonInfo, button)
                    onAnyVirtualKeyButtonClick(view, buttonInfo, button)
                }

                button.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.setBackgroundColor(buttonActiveBackgroundColor)
                            startScheduledExecutors(view, buttonInfo, button)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (buttonInfo.popup != null) {
                                if (popupWindow == null && event.y < 0) {
                                    stopScheduledExecutors()
                                    view.setBackgroundColor(buttonBackgroundColor)
                                    showPopup(view, buttonInfo.popup!!)
                                }
                                if (popupWindow != null && event.y > 0) {
                                    view.setBackgroundColor(buttonActiveBackgroundColor)
                                    dismissPopup()
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            view.setBackgroundColor(buttonBackgroundColor)
                            stopScheduledExecutors()
                            if (longPressCount == 0 || popupWindow != null) {
                                if (popupWindow != null) {
                                    dismissPopup()
                                    buttonInfo.popup?.let { popup ->
                                        onAnyVirtualKeyButtonClick(view, popup, button)
                                    }
                                } else {
                                    view.performClick()
                                }
                            }
                            true
                        }

                        else -> true
                    }
                }

                val param = LayoutParams().apply {
                    width = 0
                    height = 0
                    setMargins(0, 0, 0, 0)
                    columnSpec = spec(col, FILL, 1f)
                    rowSpec = spec(row, FILL, 1f)
                }
                button.layoutParams = param

                addView(button)
            }
        }
    }

    private fun performVirtualKeyButtonHapticFeedback(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button
    ) {
        if (virtualKeysViewClient?.performVirtualKeyButtonHapticFeedback(view, buttonInfo, button) == true) {
            return
        }

        val hapticEnabled = Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            0
        ) != 0

        if (hapticEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                if (Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }

    private fun onAnyVirtualKeyButtonClick(
        view: View,
        buttonInfo: VirtualKeyButton,
        button: Button
    ) {
        if (isSpecialButton(buttonInfo)) {
            if (longPressCount > 0) return
            val specialButton = runCatching { SpecialButton.valueOf(buttonInfo.key) }.getOrNull() ?: return
            val state = _specialButtons?.get(specialButton) ?: return

            state.setIsActive(!state.isActive)
            if (!state.isActive) state.setIsLocked(false)
        } else {
            onVirtualKeyButtonClick(view, buttonInfo, button)
        }
    }

    private fun onVirtualKeyButtonClick(view: View, buttonInfo: VirtualKeyButton, button: Button) {
        virtualKeysViewClient?.onVirtualKeyButtonClick(view, buttonInfo, button)
    }

    private fun startScheduledExecutors(view: View, buttonInfo: VirtualKeyButton, button: Button) {
        stopScheduledExecutors()
        longPressCount = 0

        if (_repetitiveKeys?.contains(buttonInfo.key) == true) {
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor().apply {
                scheduleWithFixedDelay(
                    {
                        longPressCount++
                        onVirtualKeyButtonClick(view, buttonInfo, button)
                    },
                    longPressTimeout.toLong(),
                    longPressRepeatDelay.toLong(),
                    TimeUnit.MILLISECONDS
                )
            }
        } else if (isSpecialButton(buttonInfo)) {
            val specialButton = runCatching { SpecialButton.valueOf(buttonInfo.key) }.getOrNull() ?: return
            val state = _specialButtons?.get(specialButton) ?: return

            if (handler == null) handler = Handler(Looper.getMainLooper())
            specialButtonsLongHoldRunnable = SpecialButtonsLongHoldRunnable(state)
            handler?.postDelayed(specialButtonsLongHoldRunnable!!, longPressTimeout.toLong())
        }
    }

    private fun stopScheduledExecutors() {
        scheduledExecutor?.shutdownNow()
        scheduledExecutor = null

        specialButtonsLongHoldRunnable?.let { runnable ->
            handler?.removeCallbacks(runnable)
            specialButtonsLongHoldRunnable = null
        }
    }

    fun showPopup(view: View, extraButton: VirtualKeyButton) {
        val width = view.measuredWidth
        val height = view.measuredHeight

        val button: Button = if (isSpecialButton(extraButton)) {
            createSpecialButton(extraButton.key, false) ?: return
        } else {
            Button(context, null, R.attr.buttonBarButtonStyle).apply {
                setTextColor(buttonTextColor)
            }
        }

        button.apply {
            text = extraButton.display
            isAllCaps = isButtonTextAllCaps
            setPadding(0, 0, 0, 0)
            minHeight = 0
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            this.width = width
            this.height = height
            setBackgroundColor(buttonActiveBackgroundColor)
        }

        popupWindow = PopupWindow(this).apply {
            this.width = LayoutParams.WRAP_CONTENT
            this.height = LayoutParams.WRAP_CONTENT
            contentView = button
            isOutsideTouchable = true
            isFocusable = false
            showAsDropDown(view, 0, -2 * height)
        }
    }

    private fun dismissPopup() {
        popupWindow?.let { popup ->
            popup.contentView = null
            popup.dismiss()
        }
        popupWindow = null
    }

    fun isSpecialButton(button: VirtualKeyButton): Boolean {
        return specialButtonsKeys?.contains(button.key) == true
    }

    private fun createSpecialButton(buttonKey: String, needUpdate: Boolean): Button? {
        val specialButton = runCatching { SpecialButton.valueOf(buttonKey) }.getOrNull() ?: return null
        val state = _specialButtons?.get(specialButton) ?: return null

        state.setIsCreated(true)
        val button = Button(context, null, R.attr.buttonBarButtonStyle)
        button.setTextColor(if (state.isActive) buttonActiveTextColor else buttonTextColor)

        if (needUpdate) {
            state.buttons.add(button)
        }
        return button
    }

    fun readSpecialButton(specialButton: SpecialButton, autoSetInActive: Boolean): Boolean? {
        val state = _specialButtons?.get(specialButton) ?: return null

        if (!state.isCreated || !state.isActive) return false

        if (autoSetInActive && !state.isLocked) {
            state.setIsActive(false)
        }

        return true
    }

    private inner class SpecialButtonsLongHoldRunnable(
        private val state: SpecialButtonState
    ) : Runnable {
        override fun run() {
            state.setIsLocked(!state.isActive)
            state.setIsActive(!state.isActive)
            longPressCount++
        }
    }

    interface IVirtualKeysView {
        fun onVirtualKeyButtonClick(view: View, buttonInfo: VirtualKeyButton, button: Button)
        fun performVirtualKeyButtonHapticFeedback(view: View, buttonInfo: VirtualKeyButton, button: Button): Boolean
    }
}