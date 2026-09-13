package com.deniscerri.ytdl.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.databinding.ItemThemePresetBinding
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.util.ThemeUtil
import com.google.android.material.R as MaterialR

/**
 * Grid adapter for the 12 ported theme presets. Two modes, mirroring the split
 * BAI has between ThemeAdapter/ThemeSelectionDialogFragment's MODE_APPLY and
 * MODE_CHOOSE:
 *  - [Mode.APPLY]: tapping a card saves it as the concrete preset immediately
 *    and recreates. Used by the top-level "Theme preset" picker.
 *  - [Mode.CHOOSE]: tapping a card just reports it back through [onChosen]
 *    instead of persisting anything. Used when this adapter is hosted inside
 *    the light/dark pairing dialog, which decides whether the pick belongs in
 *    the light slot or the dark slot.
 *
 * Each card previews its preset the same way [AccentAdapter] does: wrap the
 * preset's style in a [ContextThemeWrapper] and read colorPrimary/colorOnPrimary
 * back off it.
 */
class ThemePresetAdapter(
    private val host: SettingHost?,
    private val mode: Mode,
    private val presets: List<ThemeUtil.ThemePreset> = ThemeUtil.availableThemePresets,
    private val onChosen: ((ThemeUtil.ThemePreset) -> Unit)? = null
) : RecyclerView.Adapter<ThemePresetAdapter.ThemePresetViewHolder>() {

    enum class Mode { APPLY, CHOOSE }

    class ThemePresetViewHolder(
        val binding: ItemThemePresetBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemePresetViewHolder {
        val binding = ItemThemePresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ThemePresetViewHolder(binding)
    }

    override fun getItemCount() = presets.size

    override fun onBindViewHolder(holder: ThemePresetViewHolder, position: Int) {
        val preset = presets[position]
        val context = holder.binding.root.context
        val themedContext = ContextThemeWrapper(context, preset.styleResource)

        val primary = themedContext.colorFromAttr(MaterialR.attr.colorPrimary)
        val onPrimary = themedContext.colorFromAttr(MaterialR.attr.colorOnPrimary)

        holder.binding.apply {
            presetName.text = context.getString(preset.nameResource)
            presetCard.setCardBackgroundColor(primary)
            presetCard.strokeColor = onPrimary
            presetName.setTextColor(onPrimary)
            presetCard.rippleColor = ColorStateList.valueOf(onPrimary).withAlpha(40)

            root.setOnClickListener {
                when (mode) {
                    Mode.APPLY -> {
                        val activity = host?.getHostContext() ?: return@setOnClickListener
                        ThemeUtil.setConcreteThemePreset(activity, preset)
                        ThemeUtil.updateThemes()
                        host.refreshUI()
                    }
                    Mode.CHOOSE -> onChosen?.invoke(preset)
                }
            }
        }
    }
}

private fun Context.colorFromAttr(attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}
