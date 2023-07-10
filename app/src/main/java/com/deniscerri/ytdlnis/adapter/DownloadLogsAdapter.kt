package com.deniscerri.ytdlnis.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.LogItem
import java.io.File

class DownloadLogsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<LogItem?, DownloadLogsAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: ConstraintLayout

        init {
            item = itemView.findViewById(R.id.download_log_item_constraint)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.download_log_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.item

        val title = card.findViewById<TextView>(R.id.title)
        title.text = item?.title

        val delete = card.findViewById<Button>(R.id.delete)

        delete.setOnClickListener {
            onItemClickListener.onDeleteClick(item!!)
        }

        title.setOnClickListener {
            onItemClickListener.onItemClick(item!!)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(item: LogItem)
        fun onDeleteClick(item: LogItem)
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