package com.deniscerri.ytdlnis.ui.adapter

import android.animation.ValueAnimator
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.Extensions
import com.deniscerri.ytdlnis.util.Extensions.dp
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.squareup.picasso.Picasso


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
        card.popup()
        card.tag = "${item!!.id}##card"
        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.image_view)

        // THUMBNAIL ----------------------------------
        if (!sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")){
            val imageURL = item.thumb
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
        progressBar.tag = "${item.id}##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title.ifEmpty { item.url }

        // Author ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        var info = item.author
        if (item.duration.isNotEmpty()) {
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
        formatDetailsChip.text = sideDetails.joinToString("  ·  ")

        //OUTPUT
        val output = card.findViewById<TextView>(R.id.output)
        output.tag = "${item.id}##output"

        output.setOnClickListener {
            onItemClickListener.onOutputClick(item)
        }


        // PAUSE BUTTON ----------------------------------
        val pauseButton = card.findViewById<MaterialButton>(R.id.active_download_pause)
        if (pauseButton.hasOnClickListeners()) pauseButton.setOnClickListener(null)

        // CANCEL BUTTON ----------------------------------
        val cancelButton = card.findViewById<MaterialButton>(R.id.active_download_delete)
        if (cancelButton.hasOnClickListeners()) cancelButton.setOnClickListener(null)
        cancelButton.setOnClickListener {onItemClickListener.onCancelClick(item.id)}


        when(DownloadRepository.Status.valueOf(item.status)){
            DownloadRepository.Status.Active -> {
                progressBar.isIndeterminate = true

                val fromRadius: Int = dp(activity.resources, 30f)
                val toRadius: Int = dp(activity.resources, 15f)
                val animator = ValueAnimator.ofInt(fromRadius, toRadius)
                animator.setDuration(500)
                    .addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        pauseButton.cornerRadius = value
                        pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.exomedia_ic_pause_white)
                        pauseButton.isEnabled = true
                        pauseButton.tag = ActiveDownloadAction.Pause
                    }
                animator.start()

                cancelButton.visibility = View.GONE
            }
            DownloadRepository.Status.ActivePaused -> {
                progressBar.isIndeterminate = false

                val fromRadius: Int = dp(activity.resources, 15f)
                val toRadius: Int = dp(activity.resources, 30f)
                val animator = ValueAnimator.ofInt(fromRadius, toRadius)
                animator.setDuration(500)
                    .addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        pauseButton.cornerRadius = value
                        pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.exomedia_ic_play_arrow_white)
                        pauseButton.tag = ActiveDownloadAction.Resume
                        pauseButton.isEnabled = true
                    }
                animator.start()
                cancelButton.visibility = View.VISIBLE
            }
            DownloadRepository.Status.PausedReQueued -> {
                progressBar.isIndeterminate = true
                pauseButton.icon = ContextCompat.getDrawable(activity, R.drawable.ic_refresh)
                pauseButton.tag = null
                pauseButton.isEnabled = false
                cancelButton.visibility = View.GONE
            }
            else -> {}
        }

        pauseButton.setOnClickListener {
            if (pauseButton.tag == ActiveDownloadAction.Pause){
                onItemClickListener.onPauseClick(item.id, ActiveDownloadAction.Pause, position)
            }else{
                onItemClickListener.onPauseClick(item.id, ActiveDownloadAction.Resume, position)
            }
        }

    }
    interface OnItemClickListener {
        fun onCancelClick(itemID: Long)
        fun onPauseClick(itemID: Long, action: ActiveDownloadAction, position: Int)
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