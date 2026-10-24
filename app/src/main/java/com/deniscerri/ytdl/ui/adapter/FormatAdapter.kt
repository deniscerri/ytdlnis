package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView

class FormatAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<Format, FormatAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    var selectedVideoFormat: Format? = null
    val selectedAudioFormats: MutableList<Format> = mutableListOf()
    private var canMultiSelectAudio: Boolean = false


    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView = itemView.findViewById(R.id.format_card_constraintLayout)
    }

    fun setCanMultiSelectAudio(it: Boolean) {
        canMultiSelectAudio = it
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.format_item, parent, false)
        return ViewHolder(cardView)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val card = holder.item
        //card.popup()
        UiUtil.populateFormatCard(activity, card, item)
        card.isChecked = selectedVideoFormat == item || selectedAudioFormats.any { it == item }

        card.setOnClickListener {
            if (!canMultiSelectAudio) {
                onItemClickListener.onItemSelect(item, null)
            }else {
                if (item.isVideo()) {
                    if (card.isChecked) {
                        onItemClickListener.onItemSelect(item, selectedAudioFormats)
                    }else {
                        selectedVideoFormat = item
                        notifyDataSetChanged()
                        onItemClickListener.onSelectionChanged(item)
                    }
                }else {
                    if (card.isChecked) {
                        selectedAudioFormats.remove(item)
                    }else {
                        selectedAudioFormats.add(item)
                    }
                    notifyDataSetChanged()
                    onItemClickListener.onSelectionChanged(item)
                }
            }
        }

        card.setOnLongClickListener {
            UiUtil.showFormatDetails(item, activity)
            true
        }

    }

    private fun Format.isVideo() : Boolean {
        return this.vcodec.isNotBlank() && this.vcodec != "none"
    }

    interface OnItemClickListener {
        fun onItemSelect(item: Format, audioFormats: List<Format>?)
        fun onSelectionChanged(item: Format) {}
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<Format> = object : DiffUtil.ItemCallback<Format>() {
            override fun areItemsTheSame(oldItem: Format, newItem: Format): Boolean {
                return oldItem.format_id == newItem.format_id && oldItem.format_note == newItem.format_note
            }

            override fun areContentsTheSame(oldItem: Format, newItem: Format): Boolean {
                return oldItem.format_id == newItem.format_id && oldItem.format_note == newItem.format_note
            }
        }
    }
}
