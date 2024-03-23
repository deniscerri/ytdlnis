package com.deniscerri.ytdlnis.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.util.Extensions.loadThumbnail
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.card.MaterialCardView

class AlreadyExistsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<Pair<DownloadItem, Long?>, AlreadyExistsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView as MaterialCardView
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.already_exists_item, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alreadyExistsItem = getItem(position) ?: return
        val item = alreadyExistsItem.first
        val historyID = alreadyExistsItem.second

        val card = holder.cardView
        card.tag = item.id.toString()
        card.popup()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, item.thumb) }

        val duration = card.findViewById<TextView>(R.id.duration)
        duration.text = item.duration

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title.ifEmpty { item.url }

        val formatNote = card.findViewById<TextView>(R.id.format_note)
        if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
            View.GONE
        else formatNote!!.text = item.format.format_note.uppercase()

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
        val fileSizeReadable = FileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize.visibility = View.GONE
        else fileSize.text = fileSizeReadable

        val editBtn = card.findViewById<Button>(R.id.already_exists_edit)
        val deleteBtn = card.findViewById<Button>(R.id.already_exists_delete)

        card.setOnLongClickListener {
            onItemClickListener.onDeleteItem(item, position, historyID)
            true
        }

        editBtn.setOnClickListener {
            onItemClickListener.onEditItem(item, position)
        }

        deleteBtn.setOnClickListener {
            onItemClickListener.onDeleteItem(item, position, historyID)
        }

        card.setOnClickListener(null)
        if (historyID != null){
            card.setOnClickListener {
                onItemClickListener.onShowHistoryItem(historyID)
            }
        }

    }

    interface OnItemClickListener {
        fun onEditItem(downloadItem: DownloadItem, position: Int)
        fun onDeleteItem(downloadItem: DownloadItem, position: Int, historyID: Long?)
        fun onShowHistoryItem(id: Long)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<Pair<DownloadItem, Long?>> = object : DiffUtil.ItemCallback<Pair<DownloadItem, Long?>>() {
            override fun areItemsTheSame(oldItem: Pair<DownloadItem, Long?>, newItem: Pair<DownloadItem, Long?>): Boolean {
                return oldItem.first.id == newItem.first.id
            }

            override fun areContentsTheSame(oldItem: Pair<DownloadItem, Long?>, newItem: Pair<DownloadItem, Long?>): Boolean {
                return oldItem.first.id == newItem.first.id && oldItem.first.title == newItem.first.title && oldItem.first.author == newItem.first.author && oldItem.first.thumb == newItem.first.thumb
            }
        }
    }
}