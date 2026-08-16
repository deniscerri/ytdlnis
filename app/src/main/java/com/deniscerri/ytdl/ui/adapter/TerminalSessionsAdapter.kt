package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.button.MaterialButton

class TerminalSessionsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<String, TerminalSessionsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
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

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.terminal_session_card, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.itemView
        card.popup()
        card.tag = "${item!!}##card"

        val sessionName = card.findViewById<TextView>(R.id.session_name)
        sessionName.text = item

        // STOP BUTTON ----------------------------------
        val stopButton = card.findViewById<MaterialButton>(R.id.deleteSession)
        stopButton.setOnClickListener {onItemClickListener.onDeleteClick(item)}

        sessionName.setOnClickListener {
            onItemClickListener.onCardClick(item)
        }
    }
    interface OnItemClickListener {
        fun onDeleteClick(sessionId: String)
        fun onCardClick(sessionId: String)
    }
    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<String> = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }
}