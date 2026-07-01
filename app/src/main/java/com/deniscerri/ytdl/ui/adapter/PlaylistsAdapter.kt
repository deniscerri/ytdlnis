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
import com.deniscerri.ytdl.database.models.PlaylistItem
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.card.MaterialCardView

class PlaylistsAdapter(
    private val onItemClickListener: OnItemClickListener,
    private val activity: Activity
) : ListAdapter<PlaylistItem, PlaylistsAdapter.ViewHolder>(
    AsyncDifferConfig.Builder(DIFF_CALLBACK).build()
) {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.channel_card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.channel_item, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val card = holder.cardView
        card.popup()

        card.findViewById<ImageView>(R.id.channel_thumb).loadThumbnail(false, item.thumb)
        card.findViewById<TextView>(R.id.channel_name).text = item.title
        card.findViewById<TextView>(R.id.channel_url).text =
            activity.getString(if (item.type.name == "audio") R.string.audio else R.string.video)

        card.setOnClickListener { onItemClickListener.onItemClick(item) }
        card.setOnLongClickListener {
            onItemClickListener.onLongClick(item)
            true
        }
    }

    interface OnItemClickListener {
        fun onItemClick(item: PlaylistItem)
        fun onLongClick(item: PlaylistItem)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PlaylistItem>() {
            override fun areItemsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem) = oldItem == newItem
        }
    }
}
