package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.plugins.PluginBase
import com.deniscerri.ytdl.core.plugins.PluginBase.PluginRelease
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.PluginItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import java.sql.Date
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class PluginReleaseAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<PluginRelease?, PluginReleaseAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val activity: Activity
    private val onItemClickListener: OnItemClickListener

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var layoutParams: LinearLayout.LayoutParams
        init {
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(10, 10, 10, 0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.plugin_release_item, parent, false)
        return ViewHolder(cardView)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val card = holder.itemView

        card.findViewById<TextView>(R.id.title).text = item.version
        card.findViewById<TextView>(R.id.createdAt).text = item.createdAt

        val actionBtn = card.findViewById<MaterialButton>(R.id.actionBtn)
        if (item.isInstalled) {
            actionBtn.setIconResource(R.drawable.ic_baseline_delete_outline_24)
        } else {
            actionBtn.setIconResource(R.drawable.ic_down)
        }

        card.setOnClickListener {
            if (item.isInstalled) {
                onItemClickListener.onDeleteReleaseClick(item)
            } else {
                onItemClickListener.onDownloadReleaseClick(item)
            }
        }

    }
    interface OnItemClickListener {
        fun onDownloadReleaseClick(item: PluginRelease)
        fun onDeleteReleaseClick(item: PluginRelease)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<PluginRelease> = object : DiffUtil.ItemCallback<PluginRelease>() {
            override fun areItemsTheSame(oldItem: PluginRelease, newItem: PluginRelease): Boolean {
                return oldItem.version == newItem.version
            }

            override fun areContentsTheSame(oldItem: PluginRelease, newItem: PluginRelease): Boolean {
                return oldItem.isInstalled == newItem.isInstalled
            }
        }
    }
}