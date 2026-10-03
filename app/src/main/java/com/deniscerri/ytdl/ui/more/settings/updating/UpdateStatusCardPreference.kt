package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.NonSearchablePreference
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator

class UpdateStatusCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs), NonSearchablePreference {

    /**
     * UP_TO_DATE (green) is also the resting state when auto updates are on,
     * UNKNOWN (grey) when they are off and nothing was checked yet
     */
    enum class Status { UNKNOWN, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, ERROR }

    var status: Status = Status.UNKNOWN
        set(value) { field = value; notifyChanged() }

    var version: String? = null
        set(value) { field = value; notifyChanged() }

    var buttonText: String? = null
        set(value) { field = value; notifyChanged() }

    var showButton: Boolean = true
        set(value) { field = value; notifyChanged() }

    var onButtonClick: (() -> Unit)? = null

    init {
        layoutResource = R.layout.preference_update_status_card
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        val card = holder.findViewById(R.id.status_card) as MaterialCardView
        val icon = holder.findViewById(R.id.status_icon) as ImageView
        val progress = holder.findViewById(R.id.status_progress) as CircularProgressIndicator
        val title = holder.findViewById(R.id.status_title) as TextView
        val versionText = holder.findViewById(R.id.status_version) as TextView
        val button = holder.findViewById(R.id.status_button) as MaterialButton

        val (containerColor, onContainerColor) = when (status) {
            Status.UP_TO_DATE -> Pair(
                MaterialColors.harmonizeWithPrimary(context, context.getColor(R.color.update_status_ok_container)),
                MaterialColors.harmonizeWithPrimary(context, context.getColor(R.color.update_status_on_ok_container))
            )
            Status.UPDATE_AVAILABLE -> Pair(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimaryContainer),
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimaryContainer)
            )
            Status.ERROR -> Pair(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorErrorContainer),
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnErrorContainer)
            )
            else -> Pair(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceVariant),
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
        }

        card.setCardBackgroundColor(containerColor)
        title.setTextColor(onContainerColor)
        versionText.setTextColor(onContainerColor)
        icon.imageTintList = ColorStateList.valueOf(onContainerColor)
        progress.setIndicatorColor(onContainerColor)

        val isChecking = status == Status.CHECKING
        progress.isVisible = isChecking
        icon.isVisible = !isChecking
        icon.setImageResource(
            when (status) {
                Status.UP_TO_DATE -> R.drawable.ic_check
                Status.UPDATE_AVAILABLE -> R.drawable.ic_update
                else -> R.drawable.baseline_error_24
            }
        )

        title.text = context.getString(
            when (status) {
                Status.UPDATE_AVAILABLE -> R.string.update_available
                Status.ERROR -> R.string.update_check_failed
                Status.CHECKING -> R.string.checking_for_updates
                else -> R.string.version
            }
        )

        val v = version
        versionText.text = if (v.isNullOrBlank()) context.getString(R.string.loading) else context.getString(R.string.version_installed, v)

        button.isVisible = showButton
        button.isEnabled = !isChecking
        button.alpha = if (isChecking) 0.5f else 1f
        button.text = buttonText ?: context.getString(R.string.check_now)
        button.backgroundTintList = ColorStateList.valueOf(onContainerColor)
        button.setTextColor(containerColor)
        button.setOnClickListener { onButtonClick?.invoke() }
    }
}
