package com.deniscerri.ytdlnis.ui.adapter

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
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.downloadcard.ConfigureDownloadBottomSheetDialog
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlreadyExistsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<Pair<DownloadItem, Long?>, AlreadyExistsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: ConstraintLayout

        init {
            cardView = itemView as ConstraintLayout
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.already_exists_item, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val alreadyExistsItem = holder.cardView
        alreadyExistsItem.tag = item.first.id.toString()
        alreadyExistsItem.popup()

        val editBtn = alreadyExistsItem.findViewById<Button>(R.id.already_exists_edit)
        val deleteBtn = alreadyExistsItem.findViewById<Button>(R.id.already_exists_delete)

        val ttle = alreadyExistsItem.findViewById<Button>(R.id.already_exists_title)
        ttle.text = item.first.title

        CoroutineScope(Dispatchers.IO).launch {

        }

        ttle.setOnLongClickListener {
            onItemClickListener.onDeleteItem(item.first, position, item.second)
            true
        }

        editBtn.setOnClickListener {
            onItemClickListener.onEditItem(item.first, position)
        }

        deleteBtn.setOnClickListener {
            onItemClickListener.onDeleteItem(item.first, position, item.second)
        }

        ttle.setOnClickListener(null)
        if (item.second != null){
            ttle.setOnClickListener {
                onItemClickListener.onShowHistoryItem(item.second!!)
            }
        }

    }

    interface OnItemClickListener {
        fun onEditItem(downloadItem: DownloadItem, position: Int)
        fun onDeleteItem(downloadItem: DownloadItem, position: Int, historyID: Long?)
        fun onShowHistoryItem(id: Long)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<Pair<DownloadItem, Long?>> = object : DiffUtil.ItemCallback<Pair<DownloadItem, Long?>>() {
            override fun areItemsTheSame(oldItem: Pair<DownloadItem, Long?>, newItem: Pair<DownloadItem, Long?>): Boolean {
                return oldItem.first.id == newItem.first.id
            }

            override fun areContentsTheSame(oldItem: Pair<DownloadItem, Long?>, newItem: Pair<DownloadItem, Long?>): Boolean {
                return oldItem.first.id == newItem.first.id && oldItem.first.title == newItem.first.title && oldItem.first.author == newItem.first.author && oldItem.first.thumb == newItem.first.thumb
            }
        }
    }
}