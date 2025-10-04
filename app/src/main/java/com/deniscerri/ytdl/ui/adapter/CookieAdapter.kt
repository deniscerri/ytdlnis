package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class CookieAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<CookieItem?, CookieAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView

        init {
            item = itemView.findViewById(R.id.cookie_card)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.cookie_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (item == null) return
        val card = holder.item
        card.popup()

        val title = card.findViewById<TextView>(R.id.title)
        title.text = item.description.ifBlank { item.url }

        val content = card.findViewById<TextView>(R.id.content)
        content.text = item.content

        val switch = card.findViewById<MaterialSwitch>(R.id.cookieEnabled)
        switch.isChecked = item.enabled

        card.setOnClickListener {
            onItemClickListener.onItemClick(item, position)
        }

        card.setOnLongClickListener {
            onItemClickListener.onDelete(item); true
        }

        switch.setOnCheckedChangeListener { _, isEnabled ->
            onItemClickListener.onItemEnabledChanged(item, isEnabled)
            true
        }
    }

    interface OnItemClickListener {
        fun onItemClick(cookieItem: CookieItem, position: Int)
        fun onSelected(cookieItem: CookieItem)
        fun onDelete(cookieItem: CookieItem)
        fun onItemEnabledChanged(cookieItem: CookieItem, isEnabled: Boolean)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<CookieItem> = object : DiffUtil.ItemCallback<CookieItem>() {
            override fun areItemsTheSame(oldItem: CookieItem, newItem: CookieItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CookieItem, newItem: CookieItem): Boolean {
                return oldItem.id == newItem.id && oldItem.description == newItem.description
            }
        }
    }
}