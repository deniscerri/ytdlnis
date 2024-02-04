package com.deniscerri.ytdlnis.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.squareup.picasso.Picasso
import java.util.*


class PlaylistAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ResultItem?, PlaylistAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val checkedItems: ArrayList<String>
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        checkedItems = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: ConstraintLayout

        init {
            cardView = itemView.findViewById(R.id.playlist_card_constraintLayout)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.playlist_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()
        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        if (!sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home")){
            val imageURL = item!!.thumb
            if (imageURL.isNotEmpty()) {
                uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
            } else {
                uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
            }
            thumbnail.setColorFilter(Color.argb(95, 0, 0, 0))
        }else{
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }

        card.findViewById<TextView>(R.id.title).text = item!!.title
        card.findViewById<TextView>(R.id.author).text = item.author
        card.findViewById<TextView>(R.id.duration).text = item.duration

        // CHECKBOX ----------------------------------
        val check = card.findViewById<CheckBox>(R.id.checkBox)
        check.isChecked = checkedItems.contains(item.url)
        check.setOnClickListener {
            checkCard(check.isChecked, item.url)
        }

        card.setOnClickListener {
            check.performClick()
        }
    }

    private fun checkCard(isChecked: Boolean, itemURL: String) {
        if (isChecked) {
            checkedItems.add(itemURL)
        } else {
            checkedItems.remove(itemURL)
        }
        onItemClickListener.onCardSelect(itemURL, isChecked, checkedItems)
    }

    interface OnItemClickListener {
        fun onCardSelect(itemURL: String, isChecked: Boolean, checkedItems: List<String>)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckeditems() {
        for (i in 0 until itemCount){
            val item = getItem(i)
            if (checkedItems.find { it == item?.url } != null){
                checkedItems.remove(item?.url)
                notifyItemChanged(i)
            }
        }
        checkedItems.clear()
    }

    fun checkAll(){
        checkedItems.clear()
        for (i in 0 until itemCount){
            val item = getItem(i)
            checkedItems.add(item!!.url)
            notifyItemChanged(i)
        }
    }

    fun invertSelected(items: List<ResultItem?>?){
        val invertedList = mutableListOf<String>()
        items?.forEach {
            if (!checkedItems.contains(it!!.url)) invertedList.add(it.url)
        }
        checkedItems.clear()
        checkedItems.addAll(invertedList)
        notifyDataSetChanged()
    }

    fun checkRange(start: Int, end: Int){
        checkedItems.clear()
        if (start == end ){
            val item = getItem(start)
            checkedItems.add(item!!.url)
            notifyItemChanged(start)
        }else{
            for (i in start..end){
                val item = getItem(i)
                checkedItems.add(item!!.url)
                notifyItemChanged(i)
            }
        }

    }

    fun getCheckedItems() : List<String>{
        return checkedItems
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