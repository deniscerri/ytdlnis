package com.deniscerri.ytdlnis.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.TerminalItem
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class TerminalDownloadsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<TerminalItem?, TerminalDownloadsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.active_download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.active_terminal_card, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()

        card.tag = "${item!!.id}##card"

        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.progress)
        progressBar.tag = "${item.id}##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true

        // COMMAND  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        itemTitle.text = item.command.trim()

        //OUTPUT
        val output = card.findViewById<TextView>(R.id.output)
        output.tag = "${item.id}##output"

        // STOP BUTTON ----------------------------------
        val stopButton = card.findViewById<MaterialButton>(R.id.active_download_stop)
        if (stopButton.hasOnClickListeners()) stopButton.setOnClickListener(null)
        stopButton.setOnClickListener {onItemClickListener.onCancelClick(item.id)}

        card.setOnClickListener {
            onItemClickListener.onCardClick(item)
        }
    }
    interface OnItemClickListener {
        fun onCancelClick(itemID: Long)
        fun onCardClick(item: TerminalItem)
    }
    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<TerminalItem> = object : DiffUtil.ItemCallback<TerminalItem>() {
            override fun areItemsTheSame(oldItem: TerminalItem, newItem: TerminalItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: TerminalItem, newItem: TerminalItem): Boolean {
                return oldItem.command == newItem.command
            }
        }
    }
}