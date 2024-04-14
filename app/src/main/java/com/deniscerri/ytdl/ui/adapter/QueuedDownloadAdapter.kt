package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView

class QueuedDownloadAdapter(onItemClickListener: OnItemClickListener, activity: Activity, private var itemTouchHelper: ItemTouchHelper) : PagingDataAdapter<DownloadItemSimple, QueuedDownloadAdapter.ViewHolder>(
    DIFF_CALLBACK
) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    val checkedItems: MutableSet<Long> = mutableSetOf()
    var inverted: Boolean
    var showDragHandle: Boolean
    private val sharedPreferences: SharedPreferences

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        this.inverted = false
        this.showDragHandle = false
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
            .inflate(R.layout.queued_download_card, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView

        if (item == null) return
        card.tag = item.id.toString()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        //DRAG HANDLE
        val dragView = card.findViewById<View>(R.id.drag_view)
        dragView.isVisible = showDragHandle
        card.findViewById<View>(R.id.drag_view).setOnTouchListener { view, motionEvent ->
            view.performClick()
            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN){
                itemTouchHelper.startDrag(holder)
            }
            true
        }

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
            DownloadViewModel.Type.audio -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_music_formatcard, 0,0,0
            )
            DownloadViewModel.Type.video -> type.setCompoundDrawablesRelativeWithIntrinsicBounds(
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
        menu.setOnClickListener {
            val popup = PopupMenu(activity, it)
            popup.menuInflater.inflate(R.menu.queued_download_menu, popup.menu)
            if (Build.VERSION.SDK_INT > 27) popup.menu.setGroupDividerEnabled(true)

            popup.setOnMenuItemClickListener { m ->
                when(m.itemId){
                    R.id.cancel -> {
                        onItemClickListener.onQueuedCancelClick(item.id)
                        popup.dismiss()
                    }
                    R.id.move_top -> {
                        onItemClickListener.onMoveQueuedItemToTop(item.id)
                        popup.dismiss()
                    }
                    R.id.move_bottom -> {
                        onItemClickListener.onMoveQueuedItemToBottom(item.id)
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

        if ((checkedItems.contains(item.id) && !inverted) || (!checkedItems.contains(item.id) && inverted)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        card.findViewById<View>(R.id.card_content).setOnClickListener {
            if (checkedItems.size > 0 || inverted) {
                checkCard(card, item.id, position)
            } else {
                onItemClickListener.onQueuedCardClick(item.id)
            }
        }

        card.findViewById<View>(R.id.card_content).setOnLongClickListener {
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

    @SuppressLint("NotifyDataSetChanged")
    fun toggleShowDragHandle(){
        showDragHandle = !showDragHandle
        notifyDataSetChanged()
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
        onItemClickListener.onQueuedCardSelect(card.isChecked, position)
    }

    interface OnItemClickListener {
        fun onQueuedCancelClick(itemID: Long)
        fun onMoveQueuedItemToTop(itemID: Long)
        fun onMoveQueuedItemToBottom(itemID: Long)
        fun onQueuedCardClick(itemID: Long)
        fun onQueuedCardSelect(isChecked: Boolean, position: Int)
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