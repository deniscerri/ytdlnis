package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.card.MaterialCardView
import org.junit.internal.Checks


class PlaylistAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : PagingDataAdapter<ResultItem, PlaylistAdapter.ViewHolder>(DIFF_CALLBACK) {
    val checkedItems: ArrayList<Long>
    var inverted: Boolean
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        checkedItems = ArrayList()
        this.inverted = false
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.playlist_card)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.playlist_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return

        val card = holder.cardView
        card.popup()
        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, item!!.thumb) }

        card.findViewById<TextView>(R.id.title).text = item!!.title
        card.findViewById<TextView>(R.id.author).text = item.author
        card.findViewById<TextView>(R.id.duration).text = item.duration
        card.findViewById<TextView>(R.id.index).text = ((item.playlistIndex ?: (position + 1))).toString()

        // CHECKBOX ----------------------------------
        val check = card.findViewById<CheckBox>(R.id.checkBox)

        if ((checkedItems.contains(item.id) && !inverted) || (!checkedItems.contains(item.id) && inverted)) {
            check.isChecked = true
        } else {
            check.isChecked = false
        }
        check.setOnClickListener {
            checkCard(check, item.id)
        }

        card.setOnClickListener {
            check.performClick()
        }
    }

    private fun checkCard(check: CheckBox, id: Long) {
        if (!check.isChecked) {
            if (inverted) checkedItems.add(id)
            else checkedItems.remove(id)
        } else {
            if (inverted) checkedItems.remove(id)
            else checkedItems.add(id)
        }
        onItemClickListener.onCardSelect(id, check.isChecked)
    }

    interface OnItemClickListener {
        fun onCardSelect(itemID: Long, isChecked: Boolean)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckeditems() {
        inverted = false
        checkedItems.clear()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkMultipleItems(list: List<Long>){
        checkedItems.clear()
        inverted = false
        checkedItems.addAll(list)
        notifyDataSetChanged()
    }

    fun checkAll(){
        checkedItems.clear()
        inverted = true
        notifyDataSetChanged()
    }

    fun invertSelected(){
        inverted = !inverted
        notifyDataSetChanged()
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ResultItem> = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.id == newItem.id
            }
        }
    }
}