package com.deniscerri.ytdl.ui.more.settings.folder.temporary

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.NonSearchablePreference
import com.google.android.material.button.MaterialButton

/**
 * Full width red button, uses the preference title as its text
 */
class DangerButtonPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs), NonSearchablePreference {

    var onButtonClick: (() -> Unit)? = null

    init {
        layoutResource = R.layout.preference_danger_button
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        (holder.findViewById(R.id.danger_button) as MaterialButton).apply {
            text = title
            isEnabled = this@DangerButtonPreference.isEnabled
            setOnClickListener { onButtonClick?.invoke() }
        }
    }
}