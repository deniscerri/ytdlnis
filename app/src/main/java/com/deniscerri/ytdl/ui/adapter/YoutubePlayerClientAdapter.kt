package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class YoutubePlayerClientAdapter(onItemClickListener: OnItemClickListener, activity: Activity, private var itemTouchHelper: ItemTouchHelper) : ListAdapter<YoutubePlayerClientItem?, YoutubePlayerClientAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    var showDragHandle: Boolean
    private val sharedPreferences: SharedPreferences

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        this.showDragHandle = false
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView

        init {
            item = itemView.findViewById(R.id.card)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.player_client_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)!!
        holder.itemView.tag = item.playerClient
        val card = holder.item
        val title = card.findViewById<TextView>(R.id.title)
        title.text = item.playerClient

        val content = card.findViewById<TextView>(R.id.content)
        if (item.poTokens.isNotEmpty()) {
            val text = item.poTokens.joinToString("\n") { "PO Token (${it.context}): ${it.token}" }
            content.text = text
        }else {
            content.isVisible = false
        }

        val switch = card.findViewById<MaterialSwitch>(R.id.materialSwitch)
        switch.isChecked = item.enabled
        switch.setOnClickListener {
            onItemClickListener.onStatusToggle(item, switch.isChecked, position)
        }

        //DRAG HANDLE
        val dragView = card.findViewById<View>(R.id.drag_view)
        dragView.isVisible = showDragHandle
        dragView.setOnTouchListener { view, motionEvent ->
            view.performClick()
            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN){
                itemTouchHelper.startDrag(holder)
            }
            true
        }

        card.setOnClickListener {
            onItemClickListener.onItemClick(item, position)
        }

        card.setOnLongClickListener {
            onItemClickListener.onDeleteClick(item, position)
            true
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleShowDragHandle(){
        showDragHandle = !showDragHandle
        notifyDataSetChanged()
    }

    interface OnItemClickListener {
        fun onItemClick(item: YoutubePlayerClientItem, index: Int)
        fun onStatusToggle(item: YoutubePlayerClientItem, enabled: Boolean, index: Int)
        fun onDeleteClick(item: YoutubePlayerClientItem, index: Int)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<YoutubePlayerClientItem> = object : DiffUtil.ItemCallback<YoutubePlayerClientItem>() {
            override fun areItemsTheSame(oldItem: YoutubePlayerClientItem, newItem: YoutubePlayerClientItem): Boolean {
                return oldItem.playerClient == newItem.playerClient
            }

            override fun areContentsTheSame(oldItem: YoutubePlayerClientItem, newItem: YoutubePlayerClientItem): Boolean {
                return oldItem.playerClient == newItem.playerClient &&
                        oldItem.enabled == newItem.enabled &&
                        oldItem.poTokens.joinToString("\n") { it.token } == newItem.poTokens.joinToString("\n") { it.token } &&
                        oldItem.urlRegex.joinToString("\n") == newItem.urlRegex.joinToString("\n")
            }
        }
    }
}