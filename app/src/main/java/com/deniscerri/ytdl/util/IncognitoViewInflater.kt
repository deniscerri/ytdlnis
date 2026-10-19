package com.deniscerri.ytdl.util

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * Makes every text input ask the keyboard not to learn from what is typed while incognito is on
 * (Gboard shows its incognito indicator for this flag).
 */
class IncognitoViewInflater : MaterialComponentsViewInflater() {

    override fun createEditText(context: Context, attrs: AttributeSet?): AppCompatEditText {
        return super.createEditText(context, attrs).also { applyIncognito(it) }
    }

    override fun createAutoCompleteTextView(context: Context, attrs: AttributeSet?): AppCompatAutoCompleteTextView {
        return super.createAutoCompleteTextView(context, attrs).also { applyIncognito(it) }
    }

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? {
        return when (name) {
            TextInputEditText::class.java.name -> TextInputEditText(context, attrs).also { applyIncognito(it) }
            MaterialAutoCompleteTextView::class.java.name -> MaterialAutoCompleteTextView(context, attrs).also { applyIncognito(it) }
            else -> super.createView(context, name, attrs)
        }
    }

    private fun applyIncognito(view: TextView) {
        if (Build.VERSION.SDK_INT < 26) return

        val incognito = runCatching {
            PreferenceManager.getDefaultSharedPreferences(view.context).getBoolean("incognito", false)
        }.getOrDefault(false)
        if (incognito) {
            view.imeOptions = view.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
    }
}
