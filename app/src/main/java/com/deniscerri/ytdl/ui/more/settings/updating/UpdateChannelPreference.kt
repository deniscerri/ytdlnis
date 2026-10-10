package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.NonSearchablePreference
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Connected single-selection button group for picking an update channel.
 * It does not persist anything itself, the owner decides what a selection means through [onChannelSelected].
 * If [selectedChannel] is not one of the options, nothing is shown as selected
 */
class UpdateChannelPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs), NonSearchablePreference {

    private var channels: List<Pair<String, String>> = emptyList()

    private var selected: String? = null

    var selectedChannel: String?
        get() = selected
        set(value) {
            if (selected == value) return
            selected = value
            notifyChanged()
        }

    var onChannelSelected: ((String) -> Unit)? = null

    init {
        layoutResource = R.layout.preference_update_channel
        isSelectable = false
        isPersistent = false
    }

    /** @param options pairs of (value, label) */
    fun setChannels(options: List<Pair<String, String>>) {
        channels = options
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        val group = holder.findViewById(R.id.channel_group) as MaterialButtonToggleGroup
        group.setUpChannels(channels, { selectedChannel }) { value ->
            // the group already shows the new selection, no need to rebind
            selected = value
            onChannelSelected?.invoke(value)
        }
    }
}

/**
 * Fills a [MaterialButtonToggleGroup] with one outlined button per channel. Shared by the update settings
 * and the welcome screen so both look the same.
 * @param channels pairs of (value, label)
 * @param selected the value currently active, which may not be one of the options (a custom source)
 */
fun MaterialButtonToggleGroup.setUpChannels(
    channels: List<Pair<String, String>>,
    selected: () -> String?,
    onSelected: (String) -> Unit
) {
    clearOnButtonCheckedListeners()
    removeAllViews()

    channels.forEach { (value, label) ->
        val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            tag = value
            text = label
            maxLines = 1
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            val horizontalPadding = (8 * resources.displayMetrics.density).toInt()
            setPaddingRelative(horizontalPadding, paddingTop, horizontalPadding, paddingBottom)
        }
        addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (value == selected()) check(button.id)
    }

    addOnButtonCheckedListener { g, checkedId, isChecked ->
        val value = g.findViewById<View>(checkedId)?.tag as? String ?: return@addOnButtonCheckedListener
        if (!isChecked) {
            // the group allows an empty selection (e.g. a value outside of these options is active)
            // but tapping the active option should not deselect it
            if (value == selected() && g.checkedButtonId == View.NO_ID) g.check(checkedId)
            return@addOnButtonCheckedListener
        }
        if (value == selected()) return@addOnButtonCheckedListener
        onSelected(value)
    }
}
