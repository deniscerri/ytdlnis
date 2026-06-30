package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ChannelItem
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.card.MaterialCardView

class ChannelsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ChannelItem?, ChannelsAdapter.ViewHolder>(
    AsyncDifferConfig.Builder(DIFF_CALLBACK).build()
) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.channel_card)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.channel_item, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()

        if (item == null) return
        card.tag = item.url
        holder.itemView.tag = item.url

        // THUMBNAIL
        val thumbnail = card.findViewById<ImageView>(R.id.channel_thumb)
        thumbnail.loadThumbnail(false, item.thumb)

        // NAME
        val name = card.findViewById<TextView>(R.id.channel_name)
        name.text = item.name

        // URL
        val url = card.findViewById<TextView>(R.id.channel_url)
        url.text = item.url

        // CLICK LISTENER
        card.setOnClickListener {
            onItemClickListener.onItemClick(item)
        }

        // LONG CLICK FOR DELETE
        card.setOnLongClickListener {
            onItemClickListener.onDelete(item)
            true
        }
    }

    interface OnItemClickListener {
        fun onItemClick(item: ChannelItem)
        fun onDelete(item: ChannelItem)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ChannelItem> = object : DiffUtil.ItemCallback<ChannelItem>() {
            override fun areItemsTheSame(oldItem: ChannelItem, newItem: ChannelItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChannelItem, newItem: ChannelItem): Boolean {
                return oldItem.id == newItem.id && oldItem.name == newItem.name &&
                        oldItem.url == newItem.url && oldItem.thumb == newItem.thumb
            }
        }
    }
}
