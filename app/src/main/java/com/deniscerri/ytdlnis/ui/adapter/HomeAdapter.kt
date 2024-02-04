package com.deniscerri.ytdlnis.ui.adapter

import android.animation.ValueAnimator
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.Extensions
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.squareup.picasso.Picasso


class HomeAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ResultItem?, HomeAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val checkedVideos: ArrayList<String>
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        checkedVideos = ArrayList()
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.result_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        val card = holder.cardView
        card.popup()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.result_image_view)

        // THUMBNAIL ----------------------------------
        if (!sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home")){
            val imageURL = video!!.thumb
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
        val videoTitle = card.findViewById<TextView>(R.id.result_title)
        var title = video!!.title.ifBlank { video.url }
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        videoTitle.text = title

        // Bottom Info ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        author.text = video.author
        val duration = card.findViewById<TextView>(R.id.duration)
        if (video.duration.isNotEmpty()) {
            duration.text = video.duration
        }

        // BUTTONS ----------------------------------
        val videoURL = video.url
        val musicBtn = card.findViewById<MaterialButton>(R.id.download_music)
        musicBtn.tag = "$videoURL##audio"
        musicBtn.setTag(R.id.cancelDownload, "false")
        musicBtn.setOnClickListener { onItemClickListener.onButtonClick(videoURL, DownloadViewModel.Type.audio) }
        musicBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(videoURL, DownloadViewModel.Type.audio); true}
        val videoBtn = card.findViewById<MaterialButton>(R.id.download_video)
        videoBtn.tag = "$videoURL##video"
        videoBtn.setTag(R.id.cancelDownload, "false")
        videoBtn.setOnClickListener { onItemClickListener.onButtonClick(videoURL, DownloadViewModel.Type.video) }
        videoBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(videoURL, DownloadViewModel.Type.video); true}


        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.download_progress)
        progressBar.tag = "$videoURL##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        progressBar.visibility = View.GONE

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
        if (checkedVideos.contains(videoURL)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        card.tag = "$videoURL##card"
        card.setOnLongClickListener {
            checkCard(card, videoURL)
            true
        }
        card.setOnClickListener {
            if (checkedVideos.size > 0) {
                checkCard(card, videoURL)
            }else{
                onItemClickListener.onCardDetailsClick(videoURL)
            }
        }
    }

    private fun checkCard(card: MaterialCardView, videoURL: String) {
        if (card.isChecked) {
            card.strokeWidth = 0
            checkedVideos.remove(videoURL)
        } else {
            card.strokeWidth = 5
            checkedVideos.add(videoURL)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardClick(videoURL, card.isChecked)
    }

    interface OnItemClickListener {
        fun onButtonClick(videoURL: String, type: DownloadViewModel.Type?)
        fun onLongButtonClick(videoURL: String, type: DownloadViewModel.Type?)
        fun onCardClick(videoURL: String, add: Boolean)
        fun onCardDetailsClick(videoURL: String)
    }

    fun checkAll(items: List<ResultItem?>?){
        checkedVideos.clear()
        checkedVideos.addAll(items!!.map { it!!.url })
        notifyDataSetChanged()
    }

    fun invertSelected(items: List<ResultItem?>?){
        val invertedList = mutableListOf<String>()
        items?.forEach {
            if (!checkedVideos.contains(it!!.url)) invertedList.add(it.url)
        }
        checkedVideos.clear()
        checkedVideos.addAll(invertedList)
        notifyDataSetChanged()
    }

    fun clearCheckedItems(){
        checkedVideos.clear()
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ResultItem> = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.id === newItem.id
            }

            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.url == newItem.url && oldItem.title == newItem.title && oldItem.author == newItem.author
            }
        }
    }
}