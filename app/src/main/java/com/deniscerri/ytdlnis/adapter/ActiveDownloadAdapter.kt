package com.deniscerri.ytdlnis.adapter

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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

class ActiveDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, ActiveDownloadAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val fileUtil: FileUtil

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        fileUtil = FileUtil()
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
        // THUMBNAIL ----------------------------------
        val thumbnail = card.findViewById<ImageView>(R.id.image_view)
        val imageURL = item!!.thumb
        val uiHandler = Handler(Looper.getMainLooper())
        if (imageURL.isNotEmpty()) {
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
        } else {
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }
        thumbnail.setColorFilter(Color.argb(20, 0, 0, 0))

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

        val type = card.findViewById<MaterialButton>(R.id.download_type)
        when(item.type){
            DownloadViewModel.Type.audio -> type.setIconResource(R.drawable.ic_music)
            DownloadViewModel.Type.video -> type.setIconResource(R.drawable.ic_video)
            DownloadViewModel.Type.command -> type.setIconResource(R.drawable.ic_terminal)
        }

        val formatNote = card.findViewById<Chip>(R.id.format_note)
        formatNote.text = item.format.format_note.uppercase()

        val codec = card.findViewById<Chip>(R.id.codec)
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

        val fileSize = card.findViewById<Chip>(R.id.file_size)
        fileSize.text = fileUtil.convertFileSize(item.format.filesize)
        if (fileSize.text == "?") fileSize.visibility = View.GONE

        if (fileSize.visibility == View.VISIBLE && codec.visibility == View.GONE){
            fileSize.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, 0F)
                .setBottomLeftCorner(CornerFamily.ROUNDED, 0F)
                .setTopRightCorner(CornerFamily.ROUNDED, 20F)
                .setBottomRightCorner(CornerFamily.ROUNDED, 20F)
                .build()
        }

        if (fileSize.visibility == View.GONE && codec.visibility == View.GONE){
            formatNote.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, 40F)
                .setBottomLeftCorner(CornerFamily.ROUNDED, 40F)
                .setTopRightCorner(CornerFamily.ROUNDED, 40F)
                .setBottomRightCorner(CornerFamily.ROUNDED, 40F)
                .build()
        }

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