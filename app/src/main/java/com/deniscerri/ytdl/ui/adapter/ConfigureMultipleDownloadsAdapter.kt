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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItemConfigureMultiple
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.button.MaterialButton
import java.util.Locale

class ConfigureMultipleDownloadsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItemConfigureMultiple?, ConfigureMultipleDownloadsAdapter.ViewHolder>(
    AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences : SharedPreferences
    private var checkedItems: MutableSet<Long> = mutableSetOf()
    private var currentItems: Set<Long> = setOf()
    private var _isCheckingItems: Boolean = false
    private var inverted: Boolean = false

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: FrameLayout

        init {
            cardView = itemView.findViewById(R.id.download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.download_card, parent, false)
        return ViewHolder(cardView)
    }

    fun isCheckingItems() : Boolean {
        return _isCheckingItems
    }

    fun getCheckedItemsOrNull(): List<Long>? {
        if (!_isCheckingItems) return null

        val res = if (inverted) {
            currentItems.filter { !checkedItems.contains(it) }
        }else {
            checkedItems
        }

        return res.toList().ifEmpty { null }
    }

    fun getCheckedItemsSize() : Int {
        return if (inverted){
            currentItems.size - checkedItems.size
        }else{
            checkedItems.size
        }
    }

    fun removeItemsFromCheckList(ids: List<Long>) {
        checkedItems.removeAll(ids.toSet())
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckedItems() {
        _isCheckingItems = false
        inverted = false
        checkedItems = mutableSetOf()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectItems(ids: List<Long>) {
        checkedItems = mutableSetOf()
        inverted = false
        checkedItems.addAll(ids)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkAll() {
        checkedItems = mutableSetOf()
        inverted = true
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelected() {
        inverted = !inverted
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun initCheckingItems(itemIDs: List<Long>) {
        currentItems = itemIDs.toSet()
        _isCheckingItems = true
        checkedItems = mutableSetOf()
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        card.popup()
        if (item == null) return
        card.tag = item.id.toString()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, item.thumb) }

        val duration = card.findViewById<TextView>(R.id.duration)
        duration.text = item.duration

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        card.findViewById<TextView>(R.id.download_type).isVisible = false

        // Format Note ----------------------------------
        val formatNote = card.findViewById<TextView>(R.id.format_note)
        if (item.format.format_note.isNotEmpty()){
            formatNote.text = item.format.format_note.uppercase(Locale.getDefault())
            formatNote.visibility = View.VISIBLE
        }else{
            formatNote.visibility = View.GONE
        }


        val incognitoLabel = card.findViewById<MaterialButton>(R.id.incognitoLabel)
        incognitoLabel.isVisible = item.incognito

        val codec = card.findViewById<TextView>(R.id.codec)
        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none" || codecText == "DEFAULT"){
            codec.visibility = View.GONE
        }else{
            codec.visibility = View.VISIBLE
            codec.text = codecText
        }

        val container = card.findViewById<TextView>(R.id.container)
        container.isVisible = item.container.isNotBlank()
        container.text = item.container.uppercase()

        val fileSize = card.findViewById<TextView>(R.id.file_size)
        val fileSizeReadable = if(item.type != DownloadType.video){
            FileUtil.convertFileSize(item.format.filesize)
        }else{
            if (item.format.filesize < 10L) {
                FileUtil.convertFileSize(0)
            }else{
                val preferredAudioFormatIDs = item.videoPreferences.audioFormatIDs
                val audioFilesize = if (item.videoPreferences.removeAudio) {
                    0
                }else{
                    item.allFormats
                        .filter { preferredAudioFormatIDs.contains(it.format_id) }
                        .sumOf { it.filesize }
                }

                FileUtil.convertFileSize(item.format.filesize + audioFilesize)
            }

        }
        if (fileSizeReadable == "?") fileSize.visibility = View.GONE
        else {
            fileSize.text = fileSizeReadable
            fileSize.visibility = View.VISIBLE
        }

        // Type Icon Button
        val btn = card.findViewById<MaterialButton>(R.id.action_button)
        if (btn.hasOnClickListeners()) btn.setOnClickListener(null)

        btn.setOnClickListener {
            onItemClickListener.onButtonClick(item.id)
        }

        when(item.type) {
            DownloadType.audio -> {
                btn.setIconResource(R.drawable.ic_music)
                btn.contentDescription = activity.getString(R.string.audio)
            }
            DownloadType.video -> {
                btn.setIconResource(R.drawable.ic_video)
                btn.contentDescription = activity.getString(R.string.video)
            }
            else -> {
                btn.setIconResource(R.drawable.ic_terminal)
                btn.contentDescription = activity.getString(R.string.command)
            }
        }

        val checkbox = card.findViewById<CheckBox>(R.id.checkBox)
        checkbox.isVisible = _isCheckingItems
        checkbox.isChecked = (checkedItems.contains(item.id) && !inverted) || (inverted && !checkedItems.contains(item.id))
        checkbox.setOnClickListener {
            if (checkbox.isChecked) {
                if (inverted) checkedItems.remove(item.id)
                else checkedItems.add(item.id)
                onItemClickListener.onCardChecked(item.id)
            }else {
                if (inverted) checkedItems.add(item.id)
                else checkedItems.remove(item.id)
                onItemClickListener.onCardUnChecked(item.id)
            }
        }

        val index = card.findViewById<TextView>(R.id.index)
        index.isVisible = _isCheckingItems
        index.text = (position + 1).toString()

        card.setOnClickListener {
            if (_isCheckingItems) {
                checkbox.performClick()
            }else {
                onItemClickListener.onCardClick(item.id)
            }
        }

        card.setOnLongClickListener {
            if (_isCheckingItems) {
                checkbox.performClick()
            }else{
                onItemClickListener.onDelete(item.id)
            }
            true
        }
    }

    interface OnItemClickListener {
        fun onButtonClick(id: Long)
        fun onCardClick(id: Long)
        fun onCardChecked(id: Long)
        fun onCardUnChecked(id: Long)
        fun onDelete(id: Long)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItemConfigureMultiple> = object : DiffUtil.ItemCallback<DownloadItemConfigureMultiple>() {
            override fun areItemsTheSame(oldItem: DownloadItemConfigureMultiple, newItem: DownloadItemConfigureMultiple): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: DownloadItemConfigureMultiple, newItem: DownloadItemConfigureMultiple): Boolean {
                return oldItem.title == newItem.title &&
                    oldItem.type == newItem.type &&
                    oldItem.container == newItem.container &&
                    oldItem.videoPreferences == newItem.videoPreferences &&
                    oldItem.format == newItem.format &&
                    oldItem.incognito == newItem.incognito
            }
        }
    }
}