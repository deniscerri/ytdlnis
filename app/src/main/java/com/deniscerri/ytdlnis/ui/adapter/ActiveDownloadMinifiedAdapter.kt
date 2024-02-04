package com.deniscerri.ytdlnis.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.squareup.picasso.Picasso
import java.lang.StringBuilder

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
            cardView = itemView.findViewById(R.id.active_download_card_view)
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
        val thumbnail = card.findViewById<ImageView>(R.id.image_view)

        // THUMBNAIL ----------------------------------
        if (!sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")){
            val imageURL = item!!.thumb
            if (imageURL.isNotEmpty()) {
                uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
            } else {
                uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
            }
            thumbnail.setColorFilter(Color.argb(20, 0, 0, 0))
        }else{
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }

        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.progress)
        progressBar.tag = "${item!!.id}##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title.ifEmpty { item.url }


        val formatDetailsChip = card.findViewById<TextView>(R.id.format_note)
        val formatDetailsText = StringBuilder(item.format.format_note.uppercase())

        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText != "" && codecText != "none"){
            formatDetailsText.append(" \t •\t $codecText")
        }

        val fileSize = FileUtil.convertFileSize(item.format.filesize)
        if (fileSize != "?") formatDetailsText.append(" \t •\t $fileSize")

        formatDetailsChip.text = formatDetailsText

        // PAUSE BUTTON ----------------------------------
        val pauseButton = card.findViewById<MaterialButton>(R.id.active_download_pause)
        if (pauseButton.hasOnClickListeners()) pauseButton.setOnClickListener(null)

        // CANCEL BUTTON ----------------------------------
        val cancelButton = card.findViewById<MaterialButton>(R.id.active_download_delete)
        if (cancelButton.hasOnClickListeners()) cancelButton.setOnClickListener(null)
        cancelButton.setOnClickListener {onItemClickListener.onCancelClick(item.id)}

        if (item.status == DownloadRepository.Status.ActivePaused.toString()){
            progressBar.isIndeterminate = false
            pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.exomedia_ic_play_arrow_white)
            pauseButton.tag = ActiveDownloadAdapter.ActiveDownloadAction.Resume
            cancelButton.visibility = View.VISIBLE
        }else{
            progressBar.isIndeterminate = true
            pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.exomedia_ic_pause_white)
            cancelButton.visibility = View.GONE
            pauseButton.tag = ActiveDownloadAdapter.ActiveDownloadAction.Pause
        }

        pauseButton.setOnClickListener {
            if (pauseButton.tag == ActiveDownloadAdapter.ActiveDownloadAction.Pause){
                onItemClickListener.onPauseClick(item.id,
                    ActiveDownloadAdapter.ActiveDownloadAction.Pause, position)
                pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.exomedia_ic_play_arrow_white)
                if (progressBar.progress == 0) progressBar.isIndeterminate = false
                cancelButton.visibility = View.VISIBLE
                pauseButton.tag = ActiveDownloadAdapter.ActiveDownloadAction.Resume
            }else{
                onItemClickListener.onPauseClick(item.id,
                    ActiveDownloadAdapter.ActiveDownloadAction.Resume, position)
                pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.exomedia_ic_pause_white)
                progressBar.isIndeterminate = true
                cancelButton.visibility = View.GONE
                pauseButton.tag = ActiveDownloadAdapter.ActiveDownloadAction.Pause
            }
        }

        card.setOnClickListener {
            onItemClickListener.onCardClick()
        }
    }
    interface OnItemClickListener {
        fun onCancelClick(itemID: Long)
        fun onPauseClick(itemID: Long, action: ActiveDownloadAdapter.ActiveDownloadAction, position: Int)
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