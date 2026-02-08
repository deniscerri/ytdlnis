package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.plugins.PluginBase.PluginRelease
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

        val version = "v${item.version}"
        card.findViewById<TextView>(R.id.title).text = version

        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(item.published_at)

        val parser2 = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault())
        card.findViewById<TextView>(R.id.createdAt).text = parser2.format(date.time)

        val actionBtn = card.findViewById<MaterialButton>(R.id.actionBtn)
        actionBtn.isVisible = !item.isBundled && item.downloadProgress < 100

        val progress = card.findViewById<CircularProgressIndicator>(R.id.progress)
        progress.isVisible = item.isDownloading

        if (item.isDownloading) {
            actionBtn.setIconResource(R.drawable.ic_cancel)
        } else if (item.isInstalled) {
            actionBtn.setIconResource(R.drawable.ic_baseline_delete_outline_24)
        } else {
            actionBtn.setIconResource(R.drawable.ic_down)
        }

        actionBtn.setOnClickListener {
            if (!item.isBundled) {
                if (item.isDownloading && item.downloadProgress < 100) {
                    onItemClickListener.onCancelDownloadReleaseClick(item)
                } else if (item.isInstalled) {
                    onItemClickListener.onDeleteReleaseClick(item)
                } else {
                    onItemClickListener.onDownloadReleaseClick(item)
                }
            }
        }

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val card = holder.itemView
            val progress = card.findViewById<CircularProgressIndicator>(R.id.progress)

            val progressValue = payloads.last().toString().toInt()
            progress.progress = progressValue
            progress.isIndeterminate = progressValue == 0 || progressValue == 100
        }
    }

    interface OnItemClickListener {
        fun onCancelDownloadReleaseClick(item: PluginRelease)
        fun onDownloadReleaseClick(item: PluginRelease)
        fun onDeleteReleaseClick(item: PluginRelease)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<PluginRelease> = object : DiffUtil.ItemCallback<PluginRelease>() {
            override fun areItemsTheSame(oldItem: PluginRelease, newItem: PluginRelease): Boolean {
                return oldItem.version == newItem.version
            }

            override fun areContentsTheSame(oldItem: PluginRelease, newItem: PluginRelease): Boolean {
                return oldItem.isInstalled == newItem.isInstalled &&
                        oldItem.isDownloading == newItem.isDownloading &&
                        oldItem.downloadProgress == newItem.downloadProgress
            }
        }
    }
}