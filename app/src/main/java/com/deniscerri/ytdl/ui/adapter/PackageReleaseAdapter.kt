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
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.core.packages.PackageBase.PackageRelease
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PackageReleaseAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<PackageRelease?, PackageReleaseAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val activity: Activity
    private lateinit var location: PackageBase.PackageLocation
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

    fun setPackageLocation(location: PackageBase.PackageLocation) {
        this.location = location
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
        actionBtn.isVisible = !item.isBundled

        if (item.isInstalled) {
            actionBtn.setIconResource(R.drawable.ic_baseline_delete_outline_24)
            actionBtn.setOnClickListener {
                onItemClickListener.onDeleteDownloadedPackageClick(item)
            }
        } else {
            actionBtn.setIconResource(R.drawable.ic_down)
            actionBtn.setOnClickListener {
                onItemClickListener.onDownloadReleaseClick(item)
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
        fun onDownloadReleaseClick(item: PackageRelease)
        fun onDeleteDownloadedPackageClick(item: PackageRelease)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<PackageRelease> = object : DiffUtil.ItemCallback<PackageRelease>() {
            override fun areItemsTheSame(oldItem: PackageRelease, newItem: PackageRelease): Boolean {
                return oldItem.version == newItem.version
            }

            override fun areContentsTheSame(oldItem: PackageRelease, newItem: PackageRelease): Boolean {
                return oldItem.isInstalled == newItem.isInstalled
            }
        }
    }
}