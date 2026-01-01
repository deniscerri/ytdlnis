package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.AppIconItemBinding
import com.deniscerri.ytdl.util.ThemeUtil

class IconsSheetAdapter(val activity: Activity) : RecyclerView.Adapter<IconsSheetAdapter.IconsSheetViewHolder>() {

    class IconsSheetViewHolder(
        val binding: AppIconItemBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconsSheetViewHolder {
        val binding = AppIconItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IconsSheetViewHolder(binding)
    }

    override fun getItemCount() = availableIcons.size

    override fun onBindViewHolder(holder: IconsSheetViewHolder, position: Int) {
        val appIcon = availableIcons[position]
        holder.binding.apply {
            iconIV.setImageResource(appIcon.iconResource)
            iconName.text = root.context.getString(appIcon.nameResource)
            root.setOnClickListener {
                val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                preferences.edit().putString("ytdlnis_icon", appIcon.activityAlias).apply()
                val theme = preferences.getString("ytdlnis_theme", "System")!!
                ThemeUtil.updateAppIcon(activity, theme,appIcon.activityAlias)
                activity.recreate()
            }
        }
    }

    companion object {
        sealed class AppIcon(
            @StringRes val nameResource: Int,
            @DrawableRes val iconResource: Int,
            val activityAlias: String
        ) {
            object Default : AppIcon(R.string.auto, R.mipmap.ic_launcher, "default")
            object DefaultLight : AppIcon(R.string.light, R.mipmap.ic_launcher_light, "Light")
            object DefaultDark : AppIcon(R.string.dark, R.mipmap.ic_launcher_dark, "Dark")
        }

        val availableIcons = listOf(
            AppIcon.Default,
            AppIcon.DefaultLight,
            AppIcon.DefaultDark
        )
    }
}