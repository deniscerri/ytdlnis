package com.deniscerri.ytdl.ui.more.settings.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.ui.more.settings.DefaultPreferenceActions
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.settings.SettingsRegistry
import com.google.android.material.button.MaterialButton

class SettingsSearchAdapter(
    private var items: List<SearchSettingsItem>,
    private val activity: SettingsActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<SearchSettingsItem>) {
        items = newList
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_DEFAULT = 0
        private const val TYPE_SWITCH = 1
        private const val TYPE_SEEKBAR = 2
        private const val TYPE_HEADER = 3
    }

    override fun getItemViewType(position: Int): Int {
        if (items[position].isHeader) return TYPE_HEADER

        return when (items[position].preference) {
            is SwitchPreferenceCompat -> TYPE_SWITCH
            is SeekBarPreference -> TYPE_SEEKBAR
            else -> TYPE_DEFAULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.preference_search_result_title, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_SWITCH -> {
                val view = inflater.inflate(R.layout.preference_search_result_switch, parent, false)
                SwitchViewHolder(view)
            }
            TYPE_SEEKBAR -> {
                val view = inflater.inflate(R.layout.preference_search_result_seekbar, parent, false)
                SeekbarViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.preference_search_result_regular, parent, false)
                DefaultViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any?>
    ) {
        if (payloads.contains("SKIP_BIND_LOGIC")) {
            bindVisualsOnly(holder, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindVisualsOnly(holder, position)
    }

    private fun bindVisualsOnly(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val pref = item.preference
        if (pref.title.isNullOrBlank())

        if (!item.isHeader && item.canRebind) {
            item.module?.bindLogic(pref, activity)
        }

        // Handle Visuals (Dependencies)
        holder.itemView.isEnabled = pref.isEnabled
        holder.itemView.alpha = if (pref.isEnabled) 1.0f else 0.5f

        holder.itemView.setOnLongClickListener {
            val bundle = Bundle().apply {
                putString("highlight_key", pref.key)
            }

            val destinationId = activity.getDestinationIdForXml(item.xmlId)
            val navHostFragment = activity.supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
            val navController = navHostFragment.findNavController()
            activity.closeSearchView()
            navController.navigate(destinationId, bundle)
            true
        }

        // Bind the UI
        when (holder) {
            is HeaderViewHolder -> {
                holder.title.text = item.groupTitle
            }
            is SwitchViewHolder -> {
                holder.switchWidget.setOnCheckedChangeListener(null)

                holder.title.text = pref.title
                holder.summary.text = pref.summary
                holder.summary.isVisible = !pref.summary.isNullOrBlank()
                holder.switchWidget.isChecked = (pref as SwitchPreferenceCompat).isChecked
                holder.switchWidget.isFocusable = pref.isEnabled
                holder.switchWidget.isClickable = pref.isEnabled
                holder.icon.isVisible = pref.icon != null
                pref.icon?.apply { holder.icon.icon = this }


                holder.itemView.setOnClickListener {
                    holder.switchWidget.performClick()
                }
                holder.switchWidget.setOnCheckedChangeListener { _, isChecked ->
                    pref.callChangeListener(isChecked)
                    pref.isChecked = isChecked
                    activity.refreshUI()
                }
            }
            is SeekbarViewHolder -> {
                holder.title.text = pref.title
                holder.summary.text = pref.summary
                holder.summary.isVisible = !pref.summary.isNullOrBlank()
                holder.icon.isVisible = pref.icon != null
                pref.icon?.apply { holder.icon.icon = this }

                holder.seekbar.max = (pref as SeekBarPreference).max
                holder.seekbar.progress = pref.value
                holder.seekbarValue.text = pref.value.toString()
                holder.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        holder.seekbarValue.text = progress.toString()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        seekBar?.apply {
                            pref.value = progress
                            pref.callChangeListener(progress)
                            holder.seekbarValue.text = progress.toString()
                            activity.refreshUI()
                        }

                    }
                })
            }
            is DefaultViewHolder -> {
                holder.title.text = pref.title
                holder.summary.text = pref.summary
                holder.summary.isVisible = !pref.summary.isNullOrBlank()
                holder.icon.isVisible = pref.icon != null
                pref.icon?.apply { holder.icon.icon = this }


                holder.itemView.setOnClickListener {
                    if (pref.onPreferenceClickListener == null || item.module == null) {
                        val didLaunchDialog = DefaultPreferenceActions.onPreferenceDisplayDialog( activity, pref) {
                            activity.refreshUI()
                        }
                        if (!didLaunchDialog) {
                            holder.itemView.performLongClick()
                        }
                    } else {
                        pref.performClick()
                    }
                }
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.preference_title)
    }

    class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.preference_title)
        val summary: TextView = view.findViewById(R.id.preference_summary)
        val switchWidget: SwitchCompat = view.findViewById(R.id.preference_switch)
        var icon: MaterialButton = view.findViewById(R.id.preference_icon)
    }

    class DefaultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.preference_title)
        val summary: TextView = view.findViewById(R.id.preference_summary)
        var icon: MaterialButton = view.findViewById(R.id.preference_icon)
    }

    class SeekbarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.preference_title)
        val summary: TextView = view.findViewById(R.id.preference_summary)
        var icon: MaterialButton = view.findViewById(R.id.preference_icon)
        var seekbar: SeekBar = view.findViewById(R.id.seekBar)
        var seekbarValue: TextView = view.findViewById(R.id.seekbarValue)
    }

    override fun getItemCount() = items.size
}