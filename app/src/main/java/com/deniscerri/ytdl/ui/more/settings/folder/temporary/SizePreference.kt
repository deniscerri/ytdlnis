package com.deniscerri.ytdl.ui.more.settings.folder.temporary

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.deniscerri.ytdl.R

/**
 * Regular preference row with a size label at the end
 */
class SizePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    var sizeText: String? = null
        set(value) { field = value; notifyChanged() }

    init {
        widgetLayoutResource = R.layout.preference_widget_size
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.size_text) as? TextView)?.text = sizeText ?: "…"
    }
}