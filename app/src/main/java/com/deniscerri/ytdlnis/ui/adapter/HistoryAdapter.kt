package com.deniscerri.ytdlnis.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class HistoryAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<HistoryItem?, HistoryAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val checkedItems: ArrayList<Long>
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
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.downloads_card_view)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryAdapter.ViewHolder {
        return if (viewType == 0){
            val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.history_card, parent, false)
            ViewHolder(cardView, onItemClickListener)
        }else{
            val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.history_card_multiple, parent, false)
            ViewHolder(cardView, onItemClickListener)
        }
    }

    override fun getItemViewType(position: Int): Int {
        getItem(position)?.apply {
            return if(this.downloadPath.size == 1){
                0
            }else{
                1
            }
        }
        return 0
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()


        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        if (!sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("downloads")){
            val imageURL = item!!.thumb
            if (imageURL.isNotEmpty()) {
                uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
            } else {
                uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
            }
            thumbnail.setColorFilter(Color.argb(20, 0, 0, 0))
        }else{
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.downloads_title)
        var title = item!!.title.ifEmpty { item.url }
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        // Bottom Info ----------------------------------
        val author = card.findViewById<TextView>(R.id.downloads_info_bottom)
        author.text = item.author

        val length = card.findViewById<TextView>(R.id.length)
        length.text = if(item.downloadPath.size == 1) item.duration else "[${item.downloadPath.size}]"


        // TIME DOWNLOADED  ----------------------------------
        val datetime = card.findViewById<TextView>(R.id.downloads_info_time)
        datetime.text = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(item.time * 1000L)

        // BUTTON ----------------------------------
        val btn = card.findViewById<FloatingActionButton>(R.id.downloads_download_button_type)
        var filesPresent = true

        //IS IN THE FILE SYSTEM?
        if (item.downloadPath.all { !File(it).exists() && it.isNotBlank()}) {
            filesPresent = false
            thumbnail.colorFilter = ColorMatrixColorFilter(object : ColorMatrix() {
                init {
                    setSaturation(0f)
                }
            })
            thumbnail.alpha = 0.7f
            btn.backgroundTintList = MaterialColors.getColorStateList(activity, R.attr.colorSurface, ContextCompat.getColorStateList(activity, android.R.color.transparent)!!)
        }else{
            thumbnail.alpha = 1f
            btn.backgroundTintList = MaterialColors.getColorStateList(activity, R.attr.colorPrimaryContainer, ContextCompat.getColorStateList(activity, android.R.color.transparent)!!)
        }

        if (item.type == DownloadViewModel.Type.audio) {
            if (filesPresent) btn.setImageResource(R.drawable.ic_music_downloaded) else {
                btn.setImageResource(R.drawable.ic_music)
            }
        } else if (item.type == DownloadViewModel.Type.video) {
            if (filesPresent) btn.setImageResource(R.drawable.ic_video_downloaded) else btn.setImageResource(R.drawable.ic_video)
        }else{
            if (filesPresent) btn.setImageResource(R.drawable.ic_terminal) else btn.setImageResource(R.drawable.baseline_code_off_24)
        }
        if (btn.hasOnClickListeners()) btn.setOnClickListener(null)
        btn.isClickable = filesPresent

        if (checkedItems.contains(item.id)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        val finalFilePresent = filesPresent
        card.setOnLongClickListener {
            checkCard(card, item.id)
            true
        }
        card.setOnClickListener {
            if (checkedItems.size > 0) {
                checkCard(card, item.id)
            } else {
                onItemClickListener.onCardClick(item.id, finalFilePresent)
            }
        }

        btn.setOnClickListener {
            onItemClickListener.onButtonClick(item.id, finalFilePresent)
        }
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
        fun onCardClick(itemID: Long, isPresent: Boolean)
        fun onButtonClick(itemID: Long, isPresent: Boolean)
        fun onCardSelect(itemID: Long, isChecked: Boolean)
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

    fun checkAll(items: List<HistoryItem?>?){
        checkedItems.clear()
        checkedItems.addAll(items!!.map { it!!.id })
        notifyDataSetChanged()
    }

    fun invertSelected(items: List<HistoryItem?>?){
        val invertedList = mutableListOf<Long>()
        items?.forEach {
            if (!checkedItems.contains(it!!.id)) invertedList.add(it.id)
        }
        checkedItems.clear()
        checkedItems.addAll(invertedList)
        notifyDataSetChanged()
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<HistoryItem> = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.time == newItem.time
            }
        }
    }
}