package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
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
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.database.models.PackageItem

class PackagesAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<PackageItem?, PackagesAdapter.ViewHolder>(AsyncDifferConfig.Builder(
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
            .inflate(R.layout.plugin_item, parent, false)
        return ViewHolder(cardView)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val it = getItem(position) ?: return
        val card = holder.itemView

        card.findViewById<TextView>(R.id.title).text = it.title

        val instance = it.getInstance()
        val location = instance.location

        val isDownloaded = location.isDownloaded
        val isBundled = location.isBundled

        var currentVersion : String? = activity.getString(R.string.not_installed)
        if (location.isAvailable) {
            currentVersion = if (isDownloaded) instance.downloadedVersion else instance.bundledVersion
        }

        card.findViewById<TextView>(R.id.version).text = currentVersion

        card.findViewById<TextView>(R.id.downloadedChip).isVisible = isDownloaded
        card.findViewById<TextView>(R.id.bundledChip).isVisible = isBundled

        card.setOnClickListener { cl ->
            onItemClickListener.onCardClick(it, location)
        }

        card.setOnLongClickListener { c ->
            val canUninstall = location.isAvailable && isDownloaded

            if (canUninstall) {
                onItemClickListener.onDeleteDownloadedVersion(it, currentVersion)
            }
            true
        }
    }
    interface OnItemClickListener {
        fun onCardClick(item: PackageItem, location: PackageBase.PackageLocation)
        fun onDeleteDownloadedVersion(item: PackageItem, currentVersion: String?)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<PackageItem> = object : DiffUtil.ItemCallback<PackageItem>() {
            override fun areItemsTheSame(oldItem: PackageItem, newItem: PackageItem): Boolean {
                return oldItem.title == newItem.title
            }

            override fun areContentsTheSame(oldItem: PackageItem, newItem: PackageItem): Boolean {
                return oldItem.title == newItem.title
            }
        }
    }
}