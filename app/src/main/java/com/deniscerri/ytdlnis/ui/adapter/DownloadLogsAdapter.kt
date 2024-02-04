package com.deniscerri.ytdlnis.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class DownloadLogsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<LogItem?, DownloadLogsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val checkedItems: ArrayList<Long>

    init {
        checkedItems = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView

        init {
            item = itemView.findViewById(R.id.log_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.log_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.item
        card.popup()

        val title = card.findViewById<TextView>(R.id.title)
        title.text = item?.title

        val downloadedTime = card.findViewById<TextView>(R.id.downloadedTime)
        downloadedTime.text = SimpleDateFormat(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "ddMMMyyyy - HH:mm"), Locale.getDefault()).format(item!!.downloadTime)

        val downloadTypeIcon = card.findViewById<MaterialButton>(R.id.download_type)
        when(item.downloadType){
            DownloadViewModel.Type.audio -> downloadTypeIcon.setIconResource(R.drawable.ic_music)
            DownloadViewModel.Type.video -> downloadTypeIcon.setIconResource(R.drawable.ic_video)
            DownloadViewModel.Type.command -> downloadTypeIcon.setIconResource(R.drawable.ic_terminal)
            else -> {}
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
                onItemClickListener.onItemClick(item.id)
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
    fun checkAll(items: List<LogItem?>?){
        checkedItems.clear()
        checkedItems.addAll(items!!.map { it!!.id })
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelected(items: List<LogItem?>?){
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
        fun onItemClick(itemID: Long)
        fun onDeleteClick(item: LogItem)
        fun onCardSelect(itemID: Long, isChecked: Boolean)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<LogItem> = object : DiffUtil.ItemCallback<LogItem>() {
            override fun areItemsTheSame(oldItem: LogItem, newItem: LogItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: LogItem, newItem: LogItem): Boolean {
                return oldItem.content == newItem.content
            }
        }
    }
}