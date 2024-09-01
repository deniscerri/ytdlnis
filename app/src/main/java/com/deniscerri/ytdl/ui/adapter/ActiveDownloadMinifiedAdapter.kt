package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.media3.exoplayer.offline.Download
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class ActiveDownloadMinifiedAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, ActiveDownloadMinifiedAdapter.ViewHolder>(AsyncDifferConfig.Builder(
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

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.active_download_card_minified, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, item!!.thumb) }

        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.progress)
        progressBar.tag = "${item!!.id}##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true

        //OUTPUT ------------------------------------------------------------
        val output = card.findViewById<TextView>(R.id.output)
        output.tag = "${item.id}##output"

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title.ifEmpty { item.playlistTitle.ifEmpty { item.url } }
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        //DOWNLOAD TYPE -----------------------------
        val type = card.findViewById<TextView>(R.id.download_type)
        when(item.type){
            DownloadViewModel.Type.audio -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_music_formatcard, 0, 0, 0
            )
            DownloadViewModel.Type.video -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_video_formatcard, 0, 0, 0
            )
            else -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_terminal_formatcard, 0, 0, 0
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
        if (fileSizeReadable == "?" && item.downloadSections.isNotBlank()) fileSize.visibility = View.GONE
        else fileSize.text = fileSizeReadable

        val menu = card.findViewById<View>(R.id.options)
        //val paused = sharedPreferences.getBoolean("paused_downloads", false) || item.status == DownloadRepository.Status.ActivePaused.toString()
        menu.setOnClickListener {
            val popup = PopupMenu(activity, it)
            popup.menuInflater.inflate(R.menu.active_downloads_minified, popup.menu)
            if (Build.VERSION.SDK_INT > 27) popup.menu.setGroupDividerEnabled(true)

//            val pause = popup.menu[0]
//            val resume = popup.menu[1]
//
//            if (paused){
//                pause.isVisible = false
//                resume.isVisible = true
//            }else{
//                pause.isVisible = true
//                resume.isVisible = false
//            }

            popup.setOnMenuItemClickListener { m ->
                when(m.itemId){
//                    R.id.pause -> {
//                        onItemClickListener.onPauseClick(item.id, ActiveDownloadAdapter.ActiveDownloadAction.Pause, position)
//                        if (progressBar.progress == 0) progressBar.isIndeterminate = false
//                        popup.dismiss()
//                    }
//                    R.id.resume -> {
//                        onItemClickListener.onPauseClick(item.id, ActiveDownloadAdapter.ActiveDownloadAction.Resume, position)
//                        progressBar.isIndeterminate = true
//                        popup.dismiss()
//                    }
                    R.id.cancel -> {
                        onItemClickListener.onCancelClick(item.id)
                        popup.dismiss()
                    }
                }
                true
            }

            popup.show()

        }

        progressBar.isIndeterminate = !sharedPreferences.getBoolean("paused_downloads", false)

        card.setOnClickListener {
            onItemClickListener.onCardClick()
        }
    }
    interface OnItemClickListener {
        fun onCancelClick(itemID: Long)
        fun onCardClick()
    }


    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItem> = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.id == newItem.id && oldItem.title == newItem.title && oldItem.author == newItem.author && oldItem.thumb == newItem.thumb && oldItem.status == newItem.status
            }
        }
    }
}