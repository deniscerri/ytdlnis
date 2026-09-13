package com.deniscerri.ytdl.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.ItemAccentBinding
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.util.ThemeUtil
import com.google.android.material.color.DynamicColors
import com.google.android.material.R as MaterialR

class AccentAdapter(val host: SettingHost) : RecyclerView.Adapter<AccentAdapter.AccentViewHolder>() {

    class AccentViewHolder(val binding: ItemAccentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccentViewHolder {
        val binding = ItemAccentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AccentViewHolder(binding)
    }

    override fun getItemCount() = ThemeUtil.availableAccents.size

    override fun onBindViewHolder(holder: AccentViewHolder, position: Int) {
        val accent = ThemeUtil.availableAccents[position]
        val context = holder.binding.root.context

        // "Default" is Material You. Only wrap it with real dynamic color when
        // the device actually supports it — wrapContextIfAvailable() returns the
        // context unchanged (not BaseTheme) when it doesn't, which would preview
        // whatever accent happens to currently be active instead of a neutral one.
        val themedContext = when {
            accent.value == "Default" && DynamicColors.isDynamicColorAvailable() ->
                DynamicColors.wrapContextIfAvailable(context)
            accent.value == "Default" ->
                ContextThemeWrapper(context, R.style.BaseTheme)
            else ->
                ContextThemeWrapper(context, accent.styleResource)
        }

        val primary = themedContext.colorFromAttr(MaterialR.attr.colorPrimary)
        val onPrimary = themedContext.colorFromAttr(MaterialR.attr.colorOnPrimary)

        holder.binding.apply {
            accentName.text = root.context.getString(accent.nameResource)
            accentCard.setCardBackgroundColor(primary)
            accentCard.strokeColor = onPrimary
            accentName.setTextColor(onPrimary)
            accentCard.rippleColor = ColorStateList.valueOf(onPrimary).withAlpha(40)

            root.setOnClickListener {
                val preferences = PreferenceManager.getDefaultSharedPreferences(host.getHostContext())
                preferences.edit { putString("theme_accent", accent.value) }
                ThemeUtil.updateThemes()
                host.refreshUI()
            }
        }
    }
}

private fun Context.colorFromAttr(attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}
