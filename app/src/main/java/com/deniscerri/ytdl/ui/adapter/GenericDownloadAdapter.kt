package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class GenericDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : PagingDataAdapter<DownloadItemSimple, GenericDownloadAdapter.ViewHolder>(
    DIFF_CALLBACK
) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    val checkedItems: MutableSet<Long>
    var inverted: Boolean
    private val sharedPreferences: SharedPreferences

    init {
        checkedItems = mutableSetOf()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        this.inverted = false
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
            .inflate(R.layout.download_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()

        if (item == null) return
        card.tag = item.id.toString()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("queue")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, item.thumb) }

        val duration = card.findViewById<TextView>(R.id.duration)
        duration.text = item.duration
        duration.isVisible = item.duration != "-1"

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

        val incognitoLabel = card.findViewById<MaterialButton>(R.id.incognitoLabel)
        incognitoLabel.isVisible = item.incognito

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

        // ACTION BUTTON ----------------------------------
        val actionButton = card.findViewById<MaterialButton>(R.id.action_button)
        if (actionButton.hasOnClickListeners()) actionButton.setOnClickListener(null)

        when(item.status){
            DownloadRepository.Status.Cancelled.toString() -> {
                actionButton.setIconResource(R.drawable.ic_refresh)
                actionButton.contentDescription = activity.getString(R.string.download)
            }
            DownloadRepository.Status.Saved.toString() -> {
                actionButton.setIconResource(R.drawable.ic_downloads)
                actionButton.contentDescription = activity.getString(R.string.download)
            }
            DownloadRepository.Status.Queued.toString() -> {
                actionButton.setIconResource(R.drawable.ic_baseline_delete_outline_24)
                actionButton.contentDescription = activity.getString(R.string.Remove)
            }
            else -> {
                actionButton.setIconResource(R.drawable.ic_baseline_file_open_24)
                actionButton.contentDescription = activity.getString(R.string.logs)
                if (item.logID == null){
                    actionButton.visibility = View.GONE
                }
            }
        }



        actionButton.setOnClickListener {
            onItemClickListener.onActionButtonClick(item.id)
        }
        if ((checkedItems.contains(item.id) && !inverted) || (!checkedItems.contains(item.id) && inverted)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        card.setOnClickListener {
            if (checkedItems.size > 0 || inverted) {
                checkCard(card, item.id, position)
            } else {
                onItemClickListener.onCardClick(item.id)
            }
        }

        card.setOnLongClickListener {
            checkCard(card, item.id, position)
            true
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckedItems() {
        inverted = false
        checkedItems.clear()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkAll() {
        checkedItems.clear()
        inverted = true
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkMultipleItems(list: List<Long>){
        checkedItems.clear()
        inverted = false
        checkedItems.addAll(list)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelected() {
        inverted = !inverted
        notifyDataSetChanged()
    }

    fun getSelectedObjectsCount(totalSize: Int) : Int{
        return if (inverted){
            totalSize - checkedItems.size
        }else{
            checkedItems.size
        }
    }



    private fun checkCard(card: MaterialCardView, itemID: Long, position: Int) {
        if (card.isChecked) {
            card.strokeWidth = 0
            if (inverted) checkedItems.add(itemID)
            else checkedItems.remove(itemID)
        } else {
            card.strokeWidth = 5
            if (inverted) checkedItems.remove(itemID)
            else checkedItems.add(itemID)
        }

        card.isChecked = !card.isChecked
        onItemClickListener.onCardSelect(card.isChecked, position)
    }

    interface OnItemClickListener {
        fun onActionButtonClick(itemID: Long)
        fun onCardClick(itemID: Long)
        fun onCardSelect(isChecked: Boolean, position: Int)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItemSimple> = object : DiffUtil.ItemCallback<DownloadItemSimple>() {
            override fun areItemsTheSame(oldItem: DownloadItemSimple, newItem: DownloadItemSimple): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: DownloadItemSimple, newItem: DownloadItemSimple): Boolean {
                return oldItem.id == newItem.id && oldItem.title == newItem.title && oldItem.author == newItem.author && oldItem.thumb == newItem.thumb
            }
        }
    }
}