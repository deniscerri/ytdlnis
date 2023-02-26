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
import java.io.File

class DownloadLogsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<File?, DownloadLogsAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
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

        card.findViewById<TextView>(R.id.title).text = item?.name

        card.findViewById<Button>(R.id.delete).setOnClickListener {
            onItemClickListener.onDeleteClick(item!!)
        }

        card.setOnClickListener {
            onItemClickListener.onItemClick(item!!)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(file: File)
        fun onDeleteClick(file: File)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<File> = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem.absolutePath == newItem.absolutePath
            }
        }
    }
}