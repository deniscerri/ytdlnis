package com.deniscerri.ytdlnis.adapter

import android.app.Activity
import android.graphics.Color
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
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.squareup.picasso.Picasso
import java.util.*

class ConfigureMultipleDownloadsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, ConfigureMultipleDownloadsAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val fileUtil: FileUtil

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        this.fileUtil = FileUtil()
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.download_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        // THUMBNAIL ----------------------------------
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)
        val imageURL = item!!.thumb
        if (imageURL.isNotEmpty()) {
            val uiHandler = Handler(Looper.getMainLooper())
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
            thumbnail.setColorFilter(Color.argb(70, 0, 0, 0))
        } else {
            val uiHandler = Handler(Looper.getMainLooper())
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
            thumbnail.setColorFilter(Color.argb(70, 0, 0, 0))
        }

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        // Author ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        author.text = item.author

        // Container ----------------------------------
        val container = card.findViewById<TextView>(R.id.container)
        container.text = item.format.container.uppercase(Locale.getDefault())

        // File Size
        val fileSize = card.findViewById<TextView>(R.id.filesize)
        val formattedSize = fileUtil.convertFileSize(item.format.filesize)
        fileSize.text = if (formattedSize == "?") "" else formattedSize.uppercase(Locale.getDefault())

        // Quality
        val quality = card.findViewById<TextView>(R.id.quality)
        quality.text = item.format.format_note

        // Type Icon Button
        val btn = card.findViewById<MaterialButton>(R.id.downloads_download_button_type)

        when(item.type) {
            DownloadViewModel.Type.audio -> {
                btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_music)
            }
            DownloadViewModel.Type.video -> {
                btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_video)
            }
            DownloadViewModel.Type.command -> {
                btn.icon = ContextCompat.getDrawable(activity, R.drawable.ic_baseline_keyboard_arrow_right_24)
            }
        }

        card.setOnClickListener {
            onItemClickListener.onCardClick(item.id)
        }
    }

    interface OnItemClickListener {
        fun onButtonClick(videoURL: String, type: String?)
        fun onCardClick(itemID: Long)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItem> = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.id === newItem.id
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.url == newItem.url && oldItem.title == newItem.title && oldItem.author == newItem.author && oldItem.type == newItem.type && oldItem.format == newItem.format
            }
        }
    }
}