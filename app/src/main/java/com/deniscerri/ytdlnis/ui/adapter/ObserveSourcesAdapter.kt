package com.deniscerri.ytdlnis.ui.adapter

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.squareup.picasso.Picasso
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
        val uiHandler = Handler(Looper.getMainLooper())
        card.popup()

        if (item == null) return
        card.tag = item.url
        holder.itemView.tag = item.url

        val thumbnail = card.findViewById<ImageView>(R.id.result_image_view)

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

        val c = Calendar.getInstance()
        val hourMin = Calendar.getInstance()
        hourMin.timeInMillis = item.everyTime
        c.set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
        c.set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))

        when(item.everyCategory){
            ObserveSourcesRepository.EveryCategory.DAY -> {
                c.add(Calendar.DAY_OF_MONTH, item.everyNr)
            }
            ObserveSourcesRepository.EveryCategory.WEEK -> {
                if(item.everyWeekDay.isEmpty()){
                    c.add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                }else{
                    val weekDayID = c.get(Calendar.DAY_OF_WEEK).toString()
                    val followingWeekDay = (item.everyWeekDay.firstOrNull { it.toInt() > weekDayID.toInt() } ?: item.everyWeekDay.minBy { it.toInt() }).toInt()
                    c.set(Calendar.DAY_OF_WEEK, followingWeekDay)
                    if (item.everyNr > 1){
                        c.add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                    }
                }
            }
            ObserveSourcesRepository.EveryCategory.MONTH -> {
                c.add(Calendar.MONTH, item.everyNr)
                c.set(Calendar.DAY_OF_MONTH, item.everyMonthDay)
            }
        }
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
        val btn = card.findViewById<FloatingActionButton>(R.id.search)
        btn.isEnabled = true
        if (item.status == ObserveSourcesRepository.SourceStatus.STOPPED){
            info.isVisible = false
            checkMissing.isVisible = false

            btn.setImageResource(R.drawable.exomedia_ic_play_arrow_white)
            btn.setOnClickListener {
                btn.isEnabled = false
                progressBar.isVisible = true
                progressBar.animate()
                onItemClickListener.onItemStart(item, position)
            }
        }else{
            info.isVisible = true
            checkMissing.isVisible = true

            btn.setImageResource(R.drawable.ic_search)
            btn.setOnClickListener {
                btn.isEnabled = false
                progressBar.isVisible = true
                progressBar.animate()
                onItemClickListener.onItemSearch(item)
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
        fun onItemClick(item: ObserveSourcesItem)
        fun onDelete(item: ObserveSourcesItem)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ObserveSourcesItem> = object : DiffUtil.ItemCallback<ObserveSourcesItem>() {
            override fun areItemsTheSame(oldItem: ObserveSourcesItem, newItem: ObserveSourcesItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ObserveSourcesItem, newItem: ObserveSourcesItem): Boolean {
                return oldItem.id == newItem.id &&
                        oldItem.name == newItem.name &&
                        oldItem.downloadItemTemplate == newItem.downloadItemTemplate &&
                        oldItem.status == newItem.status &&
                        oldItem.retryMissingDownloads == newItem.retryMissingDownloads &&
                        oldItem.runCount == newItem.runCount
            }
        }
    }
}