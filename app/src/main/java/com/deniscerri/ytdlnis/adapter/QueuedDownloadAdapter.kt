package com.deniscerri.ytdlnis.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.squareup.picasso.Picasso
import org.w3c.dom.Text
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class QueuedDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, QueuedDownloadAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private var fileUtil: FileUtil

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        fileUtil = FileUtil()
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView
        init {
            cardView = itemView.findViewById(R.id.queued_download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.queued_download_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        // THUMBNAIL ----------------------------------
        val thumbnail = card.findViewById<ImageView>(R.id.image_view)
        val imageURL = item!!.thumb
        val uiHandler = Handler(Looper.getMainLooper())
        if (imageURL.isNotEmpty()) {
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
        } else {
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }
        thumbnail.setColorFilter(Color.argb(95, 0, 0, 0))

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        // Author ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        var info = item.author
        if (item.duration.isNotEmpty()) {
            if (item.author.isNotEmpty()) info += " • "
            info += item.duration
        }
        author.text = info

        val formatNote = card.findViewById<TextView>(R.id.format_note)
        formatNote.text = item.format.format_note
        val codec = card.findViewById<TextView>(R.id.codec)
        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none"){
            codec.visibility = View.GONE
        }else{
            codec.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSize = card.findViewById<TextView>(R.id.file_size)
        fileSize.text = fileUtil.convertFileSize(item.format.filesize)
        val downloadStartTime = card.findViewById<TextView>(R.id.time)
        val time = item.downloadStartTime
        if (time == 0L) {
            downloadStartTime.visibility = View.GONE
        } else {
            downloadStartTime.visibility = View.VISIBLE
            val cal = Calendar.getInstance()
            val date = Date(time * 1000L)
            cal.time = date
            val day = cal[Calendar.DAY_OF_MONTH]
            val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            val year = cal[Calendar.YEAR]
            val formatter: DateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = formatter.format(date)
            downloadStartTime.text = "$day $month $year - $timeString"
        }

        // CANCEL BUTTON ----------------------------------
        val cancelButton = card.findViewById<MaterialButton>(R.id.queued_download_cancel)
        if (cancelButton.hasOnClickListeners()) cancelButton.setOnClickListener(null)

        cancelButton.setOnClickListener {
            onItemClickListener.onQueuedCancelClick(item.id)
        }

    }
    interface OnItemClickListener {
        fun onQueuedCancelClick(itemID: Long)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItem> = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.url == newItem.url && oldItem.format.format_id == newItem.format.format_id
            }
        }
    }
}