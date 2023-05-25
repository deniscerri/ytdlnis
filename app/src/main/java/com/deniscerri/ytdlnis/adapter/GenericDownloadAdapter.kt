package com.deniscerri.ytdlnis.adapter

import android.annotation.SuppressLint
import android.app.Activity
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
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.squareup.picasso.Picasso

class GenericDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, GenericDownloadAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private var fileUtil: FileUtil
    private val checkedItems: ArrayList<Long>

    init {
        checkedItems = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        fileUtil = FileUtil()
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
        val uiHandler = Handler(Looper.getMainLooper())
        if (imageURL.isNotEmpty()) {
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
        } else {
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }

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
        val fileSizeReadable = fileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize.visibility = View.GONE
        else fileSize.text = fileSizeReadable

        // ACTION BUTTON ----------------------------------
        val actionButton = card.findViewById<MaterialButton>(R.id.action_button)
        if (actionButton.hasOnClickListeners()) actionButton.setOnClickListener(null)

        when(item.status){
            DownloadRepository.Status.Cancelled.toString() -> actionButton.setIconResource(R.drawable.ic_refresh)
            DownloadRepository.Status.Queued.toString() -> actionButton.setIconResource(R.drawable.ic_baseline_delete_outline_24)
            else -> {
                actionButton.setIconResource(R.drawable.ic_baseline_file_open_24)
                val logFile = fileUtil.getLogFile(activity, item)
                if (!logFile.exists()){
                    actionButton.visibility = View.GONE
                }
            }
        }



        actionButton.setOnClickListener {
            onItemClickListener.onActionButtonClick(item.id)
        }
        if (checkedItems.contains(item.id)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        card.setOnClickListener {
            if (checkedItems.size > 0) {
                checkCard(card, item.id)
            } else {
                onItemClickListener.onCardClick(item.id)
            }
        }

        card.setOnLongClickListener {
            checkCard(card, item.id)
            true
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckeditems() {
        for (i in 0 until itemCount){
            val item = getItem(i)
            if (checkedItems.find { it == item?.id } != null){
                checkedItems.remove(item?.id)
                notifyItemChanged(i)
            }
        }

        checkedItems.clear()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkAll(items: List<DownloadItem?>?){
        checkedItems.clear()
        checkedItems.addAll(items!!.map { it!!.id })
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelected(items: List<DownloadItem?>?){
        val invertedList = mutableListOf<Long>()
        items?.forEach {
            if (!checkedItems.contains(it!!.id)) invertedList.add(it.id)
        }
        checkedItems.clear()
        checkedItems.addAll(invertedList)
        notifyDataSetChanged()
    }

    private fun checkCard(card: MaterialCardView, itemID: Long) {
        if (card.isChecked) {
            card.strokeWidth = 0
            checkedItems.remove(itemID)
        } else {
            card.strokeWidth = 5
            checkedItems.add(itemID)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardSelect(itemID, card.isChecked)
    }

    interface OnItemClickListener {
        fun onActionButtonClick(itemID: Long)
        fun onCardClick(itemID: Long)
        fun onCardSelect(itemID: Long, isChecked: Boolean)
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