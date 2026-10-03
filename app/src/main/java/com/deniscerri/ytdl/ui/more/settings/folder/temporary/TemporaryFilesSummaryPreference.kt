package com.deniscerri.ytdl.ui.more.settings.folder.temporary

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.NonSearchablePreference

class TemporaryFilesSummaryPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs), NonSearchablePreference {

    var totalSize: String? = null
        set(value) { field = value; notifyChanged() }

    init {
        layoutResource = R.layout.preference_temporary_files_summary
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        (holder.findViewById(R.id.total_size) as TextView).text = totalSize ?: context.getString(R.string.loading)
    }
}
