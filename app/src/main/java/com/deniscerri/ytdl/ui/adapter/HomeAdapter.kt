package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator


class HomeAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : PagingDataAdapter<ResultItem, HomeAdapter.ViewHolder>(
    DIFF_CALLBACK
) {
    val checkedItems: MutableSet<Long>
    var inverted: Boolean
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    // Maps a result's url to a local downloaded file path. When set, the card shows a play
    // button for that item. Empty by default, so screens that don't populate it (e.g. Home)
    // never show the play button.
    val downloadedPaths = mutableMapOf<String, String>()
    var onPlayFile: ((ResultItem, String) -> Unit)? = null

    // Urls with an in-progress download. When set, the card shows an indeterminate progress bar.
    // Empty by default, so Home is unaffected.
    val downloadingUrls = mutableSetOf<String>()

    // Urls already played. When set, the card shows a "Played" badge. Empty by default.
    val playedUrls = mutableSetOf<String>()

    // When true, items render as compact list rows instead of large cards. Card layout (false)
    // is the default, so Home is unaffected.
    var useListLayout = false

    init {
        checkedItems = mutableSetOf()
        this.inverted = false
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.result_card_view)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (useListLayout) VIEW_TYPE_LIST else VIEW_TYPE_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == VIEW_TYPE_LIST) R.layout.result_card_list else R.layout.result_card
        val cardView = LayoutInflater.from(parent.context)
                .inflate(layout, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position) ?: return
        val card = holder.cardView
        card.popup()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.result_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, video.thumb) }

        // TITLE  ----------------------------------
        val videoTitle = card.findViewById<TextView>(R.id.result_title)
        var title = video.title.ifBlank { video.url }
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        videoTitle.text = title

        // Bottom Info ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        author.text = video.author
        val duration = card.findViewById<TextView>(R.id.duration)
        if (video.duration.isNotEmpty() && video.duration != "-1") {
            duration.text = video.duration
        }

        // BUTTONS ----------------------------------
        val videoURL = video.url
        val musicBtn = card.findViewById<MaterialButton>(R.id.download_music)
        musicBtn.tag = "$videoURL##audio"
        musicBtn.setTag(R.id.cancelDownload, "false")
        musicBtn.setOnClickListener { onItemClickListener.onButtonClick(video, DownloadType.audio) }
        musicBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(video, DownloadType.audio); true}
        val videoBtn = card.findViewById<MaterialButton>(R.id.download_video)
        videoBtn.tag = "$videoURL##video"
        videoBtn.setTag(R.id.cancelDownload, "false")
        videoBtn.setOnClickListener { onItemClickListener.onButtonClick(video, DownloadType.video) }
        videoBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(video, DownloadType.video); true}

        // PLAY BUTTON (only for items with a local downloaded file) ---------
        val playBtn = card.findViewById<MaterialButton>(R.id.play_file)
        val downloadedPath = downloadedPaths[videoURL]
        if (downloadedPath != null) {
            playBtn.visibility = View.VISIBLE
            playBtn.setOnClickListener { onPlayFile?.invoke(video, downloadedPath) }
        } else {
            playBtn.visibility = View.GONE
            playBtn.setOnClickListener(null)
        }


        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.download_progress)
        progressBar.tag = "$videoURL##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        progressBar.visibility = if (downloadingUrls.contains(videoURL)) View.VISIBLE else View.GONE

        // PLAYED BADGE ----------------------------------------------------
        val playedBadge = card.findViewById<TextView>(R.id.played_badge)
        playedBadge.visibility = if (playedUrls.contains(videoURL)) View.VISIBLE else View.GONE

//        if (video.isDownloading()){
//            progressBar.setVisibility(View.VISIBLE);
//        }else {
//            progressBar.setProgress(0);
//            progressBar.setIndeterminate(true);
//            progressBar.setVisibility(View.GONE);
//        }
//
//        if (video.isDownloadingAudio()) {
//            musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_cancel));
//            musicBtn.setTag(R.id.cancelDownload, "true");
//        }else{
//            if(video.isAudioDownloaded() == 1){
//                musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
//            }else{
//                musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music));
//            }
//        }
//
//        if (video.isDownloadingVideo()){
//            videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_cancel));
//            videoBtn.setTag(R.id.cancelDownload, "true");
//        }else{
//            if(video.isVideoDownloaded() == 1){
//                videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded));
//            }else{
//                videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video));
//            }
//        }
        if ((checkedItems.contains(video.id) && !inverted) || (!checkedItems.contains(video.id) && inverted)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }

        card.tag = "$videoURL##card"
        card.setOnLongClickListener {
            checkCard(card, video.id, video)
            true
        }
        card.setOnClickListener {
            if (card.isChecked || checkedItems.isNotEmpty()) {
                checkCard(card, video.id, video)
            }else{
                onItemClickListener.onCardDetailsClick(video)
            }
        }
    }

    private fun checkCard(card: MaterialCardView, itemID: Long, item: ResultItem) {
        if (card.isChecked) {
            card.strokeWidth = 0
            if (inverted) checkedItems.add(itemID)
            else checkedItems.remove(itemID)
        } else {
            card.strokeWidth = 5
            if (inverted) checkedItems.remove(itemID)
            else checkedItems.add(itemID)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardClick(item, card.isChecked)
    }

    interface OnItemClickListener {
        fun onButtonClick(item: ResultItem, type: DownloadType?)
        fun onLongButtonClick(item: ResultItem, type: DownloadType?)
        fun onCardClick(item: ResultItem, isChecked: Boolean)
        fun onCardDetailsClick(item: ResultItem)
    }

    fun checkAll(){
        checkedItems.clear()
        inverted = true
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkMultipleItems(list: List<Long>){
        checkedItems.clear()
        inverted = false
        checkedItems.addAll(list)
        notifyDataSetChanged()
    }

    fun invertSelected(){
        inverted = !inverted
        notifyDataSetChanged()
    }

    fun getSelectedObjectsCount(totalSize: Int) : Int{
        return if (inverted){
            totalSize - checkedItems.size
        }else{
            checkedItems.size
        }
    }

    fun clearCheckedItems(){
        inverted = false
        checkedItems.clear()
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_CARD = 0
        private const val VIEW_TYPE_LIST = 1

        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ResultItem> = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.url == newItem.url && oldItem.title == newItem.title && oldItem.author == newItem.author
            }
        }
    }
}