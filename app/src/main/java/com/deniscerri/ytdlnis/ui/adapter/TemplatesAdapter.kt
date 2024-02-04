package com.deniscerri.ytdlnis.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.util.fastJoinToString
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.google.android.material.card.MaterialCardView

class TemplatesAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<CommandTemplate?, TemplatesAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val checkedItems: ArrayList<Long> = ArrayList()

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView

        init {
            item = itemView.findViewById(R.id.command_card)
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
        card.popup()

        val title = card.findViewById<TextView>(R.id.title)
        title.text = item?.title

        val content = card.findViewById<TextView>(R.id.content)
        content.text = item?.content

        card.findViewById<TextView>(R.id.useInExtraCommands).apply {
            isVisible = item!!.useAsExtraCommand
            val extraAudio = if (item.useAsExtraCommandAudio) context.getString(R.string.audio) else null
            val extraVideo = if (item.useAsExtraCommandVideo) context.getString(R.string.video) else null
            val finalText = context.getString(R.string.extra_command) + " " + listOfNotNull(
                extraAudio,
                extraVideo
            ).joinToString(" ", "[", "]", -1)
            text = finalText
        }

        if (checkedItems.contains(item!!.id)) {
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
                onItemClickListener.onItemClick(item, position)
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
    fun checkAll(items: List<CommandTemplate?>?){
        checkedItems.clear()
        checkedItems.addAll(items!!.map { it!!.id })
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelected(items: List<CommandTemplate?>?){
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
        fun onItemClick(commandTemplate: CommandTemplate, index: Int)
        fun onSelected(commandTemplate: CommandTemplate)
        fun onCardSelect(itemID: Long, isChecked: Boolean)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<CommandTemplate> = object : DiffUtil.ItemCallback<CommandTemplate>() {
            override fun areItemsTheSame(oldItem: CommandTemplate, newItem: CommandTemplate): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CommandTemplate, newItem: CommandTemplate): Boolean {
                return oldItem.title == newItem.title &&
                        oldItem.content == newItem.content &&
                        oldItem.useAsExtraCommand == newItem.useAsExtraCommand &&
                        oldItem.useAsExtraCommandAudio == newItem.useAsExtraCommandAudio &&
                        oldItem.useAsExtraCommandVideo == newItem.useAsExtraCommandVideo
            }
        }
    }
}