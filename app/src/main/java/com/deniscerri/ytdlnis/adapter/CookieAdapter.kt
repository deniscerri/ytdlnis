package com.deniscerri.ytdlnis.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CookieItem

class CookieAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<CookieItem?, CookieAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: ConstraintLayout

        init {
            item = itemView.findViewById(R.id.command_template_item_constraint)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.command_template_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.item

        val title = card.findViewById<TextView>(R.id.title)
        title.text = item?.url

        val content = card.findViewById<TextView>(R.id.content)
        content.text = item?.content

        card.setOnClickListener {
            onItemClickListener.onItemClick(item!!)
        }

    }

    interface OnItemClickListener {
        fun onItemClick(commandTemplate: CookieItem)
        fun onSelected(commandTemplate: CookieItem)
        fun onDelete(commandTemplate: CookieItem)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<CookieItem> = object : DiffUtil.ItemCallback<CookieItem>() {
            override fun areItemsTheSame(oldItem: CookieItem, newItem: CookieItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CookieItem, newItem: CookieItem): Boolean {
                return oldItem.id == newItem.id
            }
        }
    }
}