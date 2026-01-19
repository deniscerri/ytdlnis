package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AlreadyExistsItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView

class AlreadyExistsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<AlreadyExistsItem, AlreadyExistsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
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
            cardView = itemView.findViewById(R.id.download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.already_exists_item, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alreadyExistsItem = getItem(position) ?: return
        val card = holder.cardView
        card.tag = alreadyExistsItem.downloadItem.id.toString()
        val item = alreadyExistsItem.downloadItem

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

        //DOWNLOAD TYPE -----------------------------
        val type = card.findViewById<TextView>(R.id.download_type)
        when(item.type){
            DownloadType.audio -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_music_formatcard, 0,0,0
            )
            DownloadType.video -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_video_formatcard, 0,0,0
            )
            else -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_terminal_formatcard, 0,0,0
            )
        }

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

        val menu = card.findViewById<View>(R.id.options)
        menu.isVisible = true
        menu.setOnClickListener {
            val popup = PopupMenu(activity, it)
            popup.menuInflater.inflate(R.menu.already_exists_menu, popup.menu)
            if (Build.VERSION.SDK_INT > 27) popup.menu.setGroupDividerEnabled(true)

            popup.setOnMenuItemClickListener { m ->
                when(m.itemId){
                    R.id.edit -> {
                        onItemClickListener.onEditItem(alreadyExistsItem, position)
                        popup.dismiss()
                    }
                    R.id.delete -> {
                        onItemClickListener.onDeleteItem(alreadyExistsItem, position)
                        popup.dismiss()
                    }
                    R.id.copy_url -> {
                        UiUtil.copyLinkToClipBoard(activity, item.url)
                        popup.dismiss()
                    }
                }
                true
            }

            popup.show()

        }

        card.setOnLongClickListener {
            onItemClickListener.onDeleteItem(alreadyExistsItem, position)
            true
        }

        if (alreadyExistsItem.historyID != null){
            card.setOnClickListener {
                onItemClickListener.onShowHistoryItem(alreadyExistsItem.historyID!!)
            }
        }else{
            card.setOnClickListener(null)
        }

    }

    interface OnItemClickListener {
        fun onEditItem(alreadyExistsItem: AlreadyExistsItem, position: Int)
        fun onDeleteItem(alreadyExistsItem: AlreadyExistsItem, position: Int)
        fun onShowHistoryItem(historyItemID: Long)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<AlreadyExistsItem> = object : DiffUtil.ItemCallback<AlreadyExistsItem>() {
            override fun areItemsTheSame(oldItem: AlreadyExistsItem, newItem: AlreadyExistsItem): Boolean {
                return oldItem.downloadItem.id == newItem.downloadItem.id
            }

            override fun areContentsTheSame(oldItem: AlreadyExistsItem, newItem: AlreadyExistsItem): Boolean {
                return oldItem.downloadItem.id == newItem.downloadItem.id && oldItem.downloadItem.title == newItem.downloadItem.title && oldItem.downloadItem.author == newItem.downloadItem.author && oldItem.downloadItem.thumb == newItem.downloadItem.thumb
            }
        }
    }
}