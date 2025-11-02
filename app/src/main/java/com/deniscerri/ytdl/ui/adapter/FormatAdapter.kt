package com.deniscerri.ytdl.ui.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.FormatRecyclerView
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.databinding.FormatItemBinding
import com.deniscerri.ytdl.ui.adapter.HistoryPaginatedAdapter.ViewHolder
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView

class FormatAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<FormatRecyclerView?, FormatAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    var selectedVideoFormat: Format? = null
    val selectedAudioFormats: MutableList<Format> = mutableListOf()
    private var canMultiSelectAudio: Boolean = false
    private var formats: MutableList<FormatRecyclerView?> = mutableListOf()


    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val item: MaterialCardView? = itemView.findViewById(R.id.format_card_constraintLayout)
        val label: Button? = itemView.findViewById(R.id.title)
    }

    override fun submitList(list: MutableList<FormatRecyclerView?>?) {
        if (list != null) {
            formats = list
        }
        super.submitList(list ?: listOf<FormatRecyclerView>())
    }

    fun setCanMultiSelectAudio(it: Boolean) {
        canMultiSelectAudio = it
    }

    override fun getItemViewType(position: Int): Int {
        val item = formats.getOrNull(position)
        return if (item?.label != null) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == 0){
            val button = LayoutInflater.from(parent.context)
                .inflate(R.layout.format_type_label, parent, false)

            ViewHolder(
                button,
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
        val itm = getItem(position) ?: return
        val viewType = getItemViewType(position)
        if (viewType == 0) {
            val button = holder.label
            button?.text = itm.label
            return
        }

        val item = itm.format ?: return
        val card = holder.item ?: return
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
                    }
                }else {
                    if (card.isChecked) {
                        selectedAudioFormats.remove(item)
                    }else {
                        selectedAudioFormats.add(item)
                    }
                    notifyDataSetChanged()
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
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<FormatRecyclerView> = object : DiffUtil.ItemCallback<FormatRecyclerView>() {
            override fun areItemsTheSame(oldItem: FormatRecyclerView, newItem: FormatRecyclerView): Boolean {
                return oldItem.label == newItem.label && oldItem.format?.format_id == newItem.format?.format_id && oldItem.format?.format_note == newItem.format?.format_note
            }

            override fun areContentsTheSame(oldItem: FormatRecyclerView, newItem: FormatRecyclerView): Boolean {
                return oldItem.label == newItem.label && oldItem.format?.format_id == newItem.format?.format_id && oldItem.format?.format_note == newItem.format?.format_note
            }
        }
    }
}