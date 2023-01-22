package com.deniscerri.ytdlnis.adapter

import android.app.Activity
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.squareup.picasso.Picasso
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<HistoryItem?, HistoryAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val checkedItems: ArrayList<Int>
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        checkedItems = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.downloads_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.downloads_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        val card = holder.cardView
        // THUMBNAIL ----------------------------------
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)
        val imageURL = video!!.thumb
        val uiHandler = Handler(Looper.getMainLooper())
        if (imageURL.isNotEmpty()) {
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
        } else {
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }
        thumbnail.setColorFilter(Color.argb(95, 0, 0, 0))

        // TITLE  ----------------------------------
        val videoTitle = card.findViewById<TextView>(R.id.downloads_title)
        var title = video.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        videoTitle.text = title

        // Bottom Info ----------------------------------
        val bottomInfo = card.findViewById<TextView>(R.id.downloads_info_bottom)
        var info = video.author
        if (video.duration.isNotEmpty()) {
            if (video.author.isNotEmpty()) info += " • "
            info += video.duration
        }
        bottomInfo.text = info

        // TIME DOWNLOADED  ----------------------------------
        val datetime = card.findViewById<TextView>(R.id.downloads_info_time)
        val time = video.time
        val downloadedTime: String
        if (time == 0L) {
            downloadedTime = activity.getString(R.string.currently_downloading) + " " + video.type
        } else {
            val cal = Calendar.getInstance()
            val date = Date(time * 1000L)
            cal.time = date
            val day = cal[Calendar.DAY_OF_MONTH]
            val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            val year = cal[Calendar.YEAR]
            val formatter: DateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = formatter.format(date)
            downloadedTime = "$day $month $year - $timeString"
        }
        datetime.text = downloadedTime

        // BUTTON ----------------------------------
        val buttonLayout = card.findViewById<LinearLayout>(R.id.downloads_download_button_layout)
        val btn = buttonLayout.findViewById<MaterialButton>(R.id.downloads_download_button_type)
        var filePresent = true

        //IS IN THE FILE SYSTEM?
        val path = video.downloadPath
        val file = File(path)
        if (!file.exists() && path.isNotEmpty()) {
            filePresent = false
            thumbnail.colorFilter = ColorMatrixColorFilter(object : ColorMatrix() {
                init {
                    setSaturation(0f)
                }
            })
            thumbnail.alpha = 0.7f
        }
        if (video.type.isNotEmpty()) {
            if (video.type == "audio") {
                if (filePresent) btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded) else btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_music)
            } else {
                if (filePresent) btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded) else btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_video)
            }
        }
        if (btn.hasOnClickListeners()) btn.setOnClickListener(null)
        if (checkedItems.contains(position)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        val finalFilePresent = filePresent
        card.setOnLongClickListener {
            checkCard(card, position)
            true
        }
        card.setOnClickListener {
            if (checkedItems.size > 0) {
                checkCard(card, video.id!!)
            } else {
                onItemClickListener.onCardClick(video.id!!, finalFilePresent)
            }
        }
    }

    private fun checkCard(card: MaterialCardView, videoID: Int) {
        if (card.isChecked) {
            card.strokeWidth = 0
            checkedItems.removeAt(videoID)
        } else {
            card.strokeWidth = 5
            checkedItems.add(videoID)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardSelect(videoID, card.isChecked)
    }

    interface OnItemClickListener {
        fun onCardClick(position: Int, isPresent: Boolean)
        fun onCardSelect(position: Int, isChecked: Boolean)
        fun onButtonClick(position: Int)
    }

    fun clearCheckedVideos() {
        val size = checkedItems.size
        for (i in 0 until size) {
            val position = checkedItems[i]
            notifyItemChanged(position)
        }
        checkedItems.clear()
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<HistoryItem> = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.id === newItem.id
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.time == newItem.time
            }
        }
    }
}