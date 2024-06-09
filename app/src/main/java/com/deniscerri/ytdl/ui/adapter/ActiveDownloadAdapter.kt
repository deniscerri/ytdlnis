package com.deniscerri.ytdl.ui.adapter

import android.animation.ValueAnimator
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.dp
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator


class ActiveDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, ActiveDownloadAdapter.ViewHolder>(AsyncDifferConfig.Builder(
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
                .inflate(R.layout.active_download_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.tag = "${item!!.id}##card"
        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, item.thumb) }

        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.progress)
        progressBar.tag = "${item.id}##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title.ifEmpty { item.playlistTitle.ifEmpty { item.url } }
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        // Author ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        var info = item.author
        if (item.duration.isNotEmpty() && item.duration != "-1") {
            if (item.author.isNotEmpty()) info += " • "
            info += item.duration
        }
        author.text = info

        val type = card.findViewById<MaterialButton>(R.id.download_type)
        when(item.type){
            DownloadViewModel.Type.audio -> type.setIconResource(R.drawable.ic_music)
            DownloadViewModel.Type.video -> type.setIconResource(R.drawable.ic_video)
            DownloadViewModel.Type.command -> type.setIconResource(R.drawable.ic_terminal)
            else -> {}
        }

        val formatDetailsChip = card.findViewById<Chip>(R.id.format_note)

        val sideDetails = mutableListOf<String>()
        sideDetails.add(item.format.format_note.uppercase().replace("\n", " "))
        sideDetails.add(item.container.uppercase().ifEmpty { item.format.container.uppercase() })

        val fileSize = FileUtil.convertFileSize(item.format.filesize)
        if (fileSize != "?") sideDetails.add(fileSize)
        formatDetailsChip.text = sideDetails.filter { it.isNotBlank() }.joinToString("  ·  ")

        //OUTPUT
        val output = card.findViewById<TextView>(R.id.output)
        output.tag = "${item.id}##output"

        output.setOnClickListener {
            onItemClickListener.onOutputClick(item)
        }

        // CANCEL BUTTON ----------------------------------
        val cancelButton = card.findViewById<MaterialButton>(R.id.active_download_delete)
        if (cancelButton.hasOnClickListeners()) cancelButton.setOnClickListener(null)
        cancelButton.setOnClickListener {onItemClickListener.onCancelClick(item.id)}

        val activePaused = item.status == DownloadRepository.Status.ActivePaused.toString()
        val resumeButton = card.findViewById<MaterialButton>(R.id.active_download_resume)
        resumeButton.isVisible = activePaused
        if (resumeButton.hasOnClickListeners()) resumeButton.setOnClickListener(null)
        resumeButton.setOnClickListener {
            resumeButton.isVisible = false
            onItemClickListener.onResumeClick(item.id)
        }

        if (sharedPreferences.getBoolean("paused_downloads", false) || activePaused) {
            progressBar.isIndeterminate = false
            cancelButton.isEnabled = true
            output.text = activity.getString(R.string.exo_download_paused)
        }else{
            progressBar.isIndeterminate = true
            cancelButton.isEnabled = true
        }
    }
    interface OnItemClickListener {
        fun onCancelClick(itemID: Long)
        fun onResumeClick(itemID: Long)
        fun onOutputClick(item: DownloadItem)
    }

    enum class ActiveDownloadAction {
        Resume, Pause
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