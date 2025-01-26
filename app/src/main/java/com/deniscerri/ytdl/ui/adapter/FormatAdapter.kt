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
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.databinding.FormatItemBinding
import com.deniscerri.ytdl.ui.adapter.HistoryPaginatedAdapter.ViewHolder
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView

class FormatAdapter(onItemClickListener: OnItemClickListener, activity: Activity, private val downloadType: DownloadViewModel.Type) : ListAdapter<Format?, FormatAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private var selectedVideoFormat: Format?
    private val selectedAudioFormats: MutableList<Format>
    private val usingGrid: Boolean


    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        this.selectedVideoFormat = null
        this.usingGrid = false
        this.selectedAudioFormats = mutableListOf()
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView

        init {
            item = itemView.findViewById(R.id.format_card_constraintLayout)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (usingGrid){
            val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.format_item_grid, parent, false)

            ViewHolder(
                cardView,
                onItemClickListener
            )
        }else{
            val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.format_item, parent, false)

            ViewHolder(
                cardView,
                onItemClickListener
            )
        }
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val card = holder.item
        card.popup()
        UiUtil.populateFormatCard(activity, card, item)
        card.setOnClickListener {
            when(downloadType) {
                DownloadViewModel.Type.audio -> {
                    onItemClickListener.onItemClick(item)
                }
                DownloadViewModel.Type.video -> {
                    if(item.isAudio()) {
                        if (card.isChecked) {
                            selectedAudioFormats.remove(item)
                        }else {
                            selectedAudioFormats.add(item)
                        }
                    }else {
                        if (card.isChecked) {
                            onItemClickListener.onItemClick(item)
                        }else {
                            selectedVideoFormat = item
                            card.isChecked = true
                        }
                    }
                }
                else -> {}
            }
        }


    }

    private fun Format.isAudio() : Boolean {
        return this.vcodec.isNotBlank() && this.vcodec != "none"
    }

    interface OnItemClickListener {
        fun onItemClick(item: Format)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<Format> = object : DiffUtil.ItemCallback<Format>() {
            override fun areItemsTheSame(oldItem: Format, newItem: Format): Boolean {
                return oldItem.format_id == newItem.format_id
            }

            override fun areContentsTheSame(oldItem: Format, newItem: Format): Boolean {
                return oldItem.format_id == newItem.format_id
            }
        }
    }
}