package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
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
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
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

        //INFO
        val info = card.findViewById<Chip>(R.id.info)
        val nextTime = item.calculateNextTimeForObserving()
        val c = Calendar.getInstance()
        c.timeInMillis = nextTime

        val weekdays = DateFormatSymbols(Locale.getDefault()).shortWeekdays
        val text = "${weekdays[c.get(Calendar.DAY_OF_WEEK)]}, ${SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(c.timeInMillis)}"
        info.text = text

        //CHECK MISSING
        val checkMissing = card.findViewById<Button>(R.id.check_missing)
        checkMissing.isVisible = item.retryMissingDownloads

        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.download_progress)
        progressBar.isIndeterminate = true
        progressBar.isVisible = false

        // BUTTON ----------------------------------
        val searchBtn = card.findViewById<MaterialButton>(R.id.search)
        val pauseBtn = card.findViewById<MaterialButton>(R.id.pause_resume)
        searchBtn.isEnabled = true
        pauseBtn.isEnabled = true
        if (item.status == ObserveSourcesRepository.SourceStatus.STOPPED){
            info.isVisible = false
            searchBtn.isVisible = false

            pauseBtn.setIconResource(R.drawable.exomedia_ic_play_arrow_white)
            pauseBtn.contentDescription = activity.getString(R.string.resume)
            pauseBtn.setOnClickListener {
                pauseBtn.isEnabled = false
                onItemClickListener.onItemStart(item, position)
            }
        }else{
            info.isVisible = true
            searchBtn.isVisible = true

            searchBtn.setOnClickListener {
                searchBtn.isEnabled = false
                progressBar.isVisible = true
                progressBar.animate()
                onItemClickListener.onItemSearch(item)
            }

            pauseBtn.setIconResource(R.drawable.exomedia_ic_pause_white)
            pauseBtn.contentDescription = activity.getString(R.string.pause)
            pauseBtn.setOnClickListener {
                pauseBtn.isEnabled = false
                onItemClickListener.onItemPaused(item, position)
            }
        }


        card.setOnClickListener {
            onItemClickListener.onItemClick(item)
        }

        card.setOnLongClickListener {
            onItemClickListener.onDelete(item); true
        }
    }

    interface OnItemClickListener {

        fun onItemSearch(item: ObserveSourcesItem)
        fun onItemStart(item: ObserveSourcesItem, position: Int)
        fun onItemPaused(item: ObserveSourcesItem, position: Int)
        fun onItemClick(item: ObserveSourcesItem)
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
            }
        }
    }
}