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
import com.deniscerri.ytdlnis.database.models.CommandTemplate

class TemplatesAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<CommandTemplate?, TemplatesAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
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
        title.text = item?.title

        val content = card.findViewById<TextView>(R.id.content)
        content.text = item?.content

//        val check = card.findViewById<Button>(R.id.check)
//
//        check.setOnClickListener {
//            onItemClickListener.onSelected(item!!)
//        }

        title.setOnClickListener {
            onItemClickListener.onItemClick(item!!)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(commandTemplate: CommandTemplate)
        fun onSelected(commandTemplate: CommandTemplate)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<CommandTemplate> = object : DiffUtil.ItemCallback<CommandTemplate>() {
            override fun areItemsTheSame(oldItem: CommandTemplate, newItem: CommandTemplate): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CommandTemplate, newItem: CommandTemplate): Boolean {
                return oldItem.content == newItem.content
            }
        }
    }
}