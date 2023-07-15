package com.deniscerri.ytdlnis.adapter

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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.squareup.picasso.Picasso
import java.lang.StringBuilder

class ActiveDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, ActiveDownloadAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
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
        }

        val formatDetailsChip = card.findViewById<Chip>(R.id.format_note)
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

        //OUTPUT
        val output = card.findViewById<TextView>(R.id.output)
        output.tag = "${item.id}##output"

        output.setOnClickListener {
            onItemClickListener.onOutputClick(item)
        }


        // CANCEL BUTTON ----------------------------------
        val cancelButton = card.findViewById<MaterialButton>(R.id.active_download_cancel)
        if (cancelButton.hasOnClickListeners()) cancelButton.setOnClickListener(null)

        cancelButton.setOnClickListener {
            onItemClickListener.onCancelClick(item.id)
        }
    }
    interface OnItemClickListener {
        fun onCancelClick(itemID: Long)
        fun onOutputClick(item: DownloadItem)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItem> = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.id == newItem.id && oldItem.title == newItem.title && oldItem.author == newItem.author && oldItem.thumb == newItem.thumb
            }
        }
    }
}