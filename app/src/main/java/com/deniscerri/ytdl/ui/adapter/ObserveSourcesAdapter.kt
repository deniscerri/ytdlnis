package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.util.Extensions
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import com.deniscerri.ytdl.util.Extensions.displayStatus
import com.deniscerri.ytdl.util.Extensions.dp
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class ObserveSourcesAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ObserveSourcesItem?, ObserveSourcesAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    private var runningIds: Set<Long> = emptySet()

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.observe_sources_card)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.observe_sources_item, parent, false)
        return ViewHolder(cardView)
    }

    private fun dp(v: View, value: Int) = (v.resources.displayMetrics.density * value).toInt()

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()

        if (item == null) return
        card.tag = item.url
        holder.itemView.tag = item.url


        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.name
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        //URL
        val url = card.findViewById<TextView>(R.id.url)
        url.text = item.url

        val info = card.findViewById<Chip>(R.id.info)
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.download_progress)
        progressBar.isIndeterminate = true
        progressBar.isVisible = false

        info.isVisible = true

        val isRunning = runningIds.contains(item.id)
        progressBar.isVisible = isRunning

        fun attr(a: Int) = MaterialColors.getColor(card, a)

        when (item.displayStatus()) {
            Extensions.ObserveSourceDisplayStatus.ACTIVE -> {
                val c = Calendar.getInstance().apply { timeInMillis = item.calculateNextTimeForObserving() }
                val weekdays = DateFormatSymbols(Locale.getDefault()).shortWeekdays
                val next = "${weekdays[c.get(Calendar.DAY_OF_WEEK)]}, " +
                        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault())
                            .format(c.timeInMillis)
                info.text = activity.getString(R.string.next_run, next)
                info.setChipIconResource(R.drawable.baseline_loop_24)

                // ACTIVE styling: accent chip + outline, full opacity
                val onAccent = attr(com.google.android.material.R.attr.colorOnTertiaryContainer)
                info.chipBackgroundColor = ColorStateList.valueOf(attr(com.google.android.material.R.attr.colorTertiaryContainer))
                info.setTextColor(onAccent)
                info.chipIconTint = ColorStateList.valueOf(onAccent)
                itemTitle.alpha = 1f
                url.alpha = 0.7f
            }

            Extensions.ObserveSourceDisplayStatus.PAUSED -> {
                info.text = activity.getString(R.string.paused)
                info.setChipIconResource(R.drawable.exomedia_ic_pause_white)
                styleInactive(card, info, itemTitle, url, ::attr)
            }

            Extensions.ObserveSourceDisplayStatus.FINISHED -> {
                info.text = activity.resources.getQuantityString(
                    R.plurals.finished_runs, item.runCount, item.runCount)
                info.setChipIconResource(R.drawable.ic_check)
                styleInactive(card, info, itemTitle, url, ::attr)
            }
        }


        card.setOnClickListener {
            onItemClickListener.onItemClick(item, position)
        }

        card.setOnLongClickListener {
            onItemClickListener.onDelete(item); true
        }
    }

    fun setRunningIds(ids: Set<Long>) {
        if (ids == runningIds) return
        runningIds = ids
        notifyDataSetChanged()   // list is small; fine
    }

    private fun styleInactive(
        card: MaterialCardView, info: Chip, title: TextView, url: TextView,
        attr: (Int) -> Int
    ) {
        val onNeutral = attr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        info.chipBackgroundColor = ColorStateList.valueOf(attr(com.google.android.material.R.attr.colorSurfaceVariant))
        info.setTextColor(onNeutral)
        info.chipIconTint = ColorStateList.valueOf(onNeutral)
        card.strokeWidth = 0
        title.alpha = 0.5f
        url.alpha = 0.4f
    }

    interface OnItemClickListener {

        fun onItemStart(item: ObserveSourcesItem, position: Int)
        fun onItemClick(item: ObserveSourcesItem, position: Int)
        fun onDelete(item: ObserveSourcesItem)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ObserveSourcesItem> = object : DiffUtil.ItemCallback<ObserveSourcesItem>() {
            override fun areItemsTheSame(oldItem: ObserveSourcesItem, newItem: ObserveSourcesItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ObserveSourcesItem, newItem: ObserveSourcesItem): Boolean {
                return oldItem.id == newItem.id
                        && oldItem.name == newItem.name
                        && oldItem.downloadItemTemplate == newItem.downloadItemTemplate
                        && oldItem.status == newItem.status
                        && oldItem.retryMissingDownloads == newItem.retryMissingDownloads
                        && oldItem.runCount == newItem.runCount
                        && oldItem.calculateNextTimeForObserving() == newItem.calculateNextTimeForObserving()
                        && oldItem.displayStatus() == newItem.displayStatus()
            }
        }
    }
}