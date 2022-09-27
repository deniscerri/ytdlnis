package com.deniscerri.ytdlnis.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.database.Video;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class HomeRecyclerViewAdapter extends RecyclerView.Adapter<HomeRecyclerViewAdapter.ViewHolder> {
    private ArrayList<Video> videoList;
    private ArrayList<Integer> checkedVideos;
    private final OnItemClickListener onItemClickListener;
    private Activity activity;

    public HomeRecyclerViewAdapter(ArrayList<Video> videos, OnItemClickListener onItemClickListener, Activity activity){
        this.videoList = videos;
        this.checkedVideos = new ArrayList<>();
        this.onItemClickListener = onItemClickListener;
        this.activity = activity;
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardView;

        public ViewHolder(@NonNull View itemView, OnItemClickListener onItemClickListener) {
            super(itemView);
            cardView = itemView.findViewById(R.id.result_card_view);
        }
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View cardView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.result_card, parent, false);
        return new HomeRecyclerViewAdapter.ViewHolder(cardView, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videoList.get(position);

        MaterialCardView card = holder.cardView;
        // THUMBNAIL ----------------------------------
        ImageView thumbnail = card.findViewById(R.id.result_image_view);
        String imageURL= video.getThumb();

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> Picasso.get().load(imageURL).into(thumbnail));
        thumbnail.setColorFilter(Color.argb(70, 0, 0, 0));

        // TITLE  ----------------------------------
        TextView videoTitle = card.findViewById(R.id.result_title);
        String title = video.getTitle();

        if(title.length() > 100){
            title = title.substring(0, 40) + "...";
        }
        videoTitle.setText(title);

        // Bottom Info ----------------------------------

        TextView bottomInfo = card.findViewById(R.id.result_info_bottom);
        String info = video.getAuthor() + " • " + video.getDuration();
        bottomInfo.setText(info);

        // BUTTONS ----------------------------------
        String videoID = video.getVideoId();

        LinearLayout buttonLayout = card.findViewById(R.id.download_button_layout);

        MaterialButton musicBtn = buttonLayout.findViewById(R.id.download_music);
        musicBtn.setTag(videoID + "##audio");
        musicBtn.setOnClickListener(view -> onItemClickListener.onButtonClick(position, "audio"));

        MaterialButton videoBtn = buttonLayout.findViewById(R.id.download_video);
        videoBtn.setTag(videoID + "##video");
        videoBtn.setOnClickListener(view -> onItemClickListener.onButtonClick(position, "video"));


        // PROGRESS BAR ----------------------------------------------------

        ProgressBar progressBar = card.findViewById(R.id.download_progress);
        progressBar.setVisibility(View.GONE);
        progressBar.setTag(videoID + "##progress");

        if(video.isAudioDownloaded() == 1){
            musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
        }else{
            musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music));
        }
        if(video.isVideoDownloaded() == 1){
            videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded));
        }else{
            videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video));
        }

        if(checkedVideos.contains(position)){
            card.setChecked(true);
            card.setStrokeWidth(5);
        }else{
            card.setChecked(false);
            card.setStrokeWidth(0);
        }

        card.setTag(videoID + "##card");
        card.setOnLongClickListener(view -> {
            checkCard(card, position);
            return true;
        });
        card.setOnClickListener(view -> {
            if(checkedVideos.size() > 0){
                checkCard(card, position);
            }
        });
    }

    private void checkCard(MaterialCardView card, int position){
        if(card.isChecked()){
            card.setStrokeWidth(0);
            checkedVideos.remove(Integer.valueOf(position));
        }else{
            card.setStrokeWidth(5);
            checkedVideos.add(position);
        }
        card.setChecked(!card.isChecked());
        onItemClickListener.onCardClick(position, card.isChecked());
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public interface OnItemClickListener {
        void onButtonClick(int position, String type);
        void onCardClick(int position, boolean add);
    }

    public void setVideoList(ArrayList<Video> list, boolean reset){
        int size = videoList.size();
        if(reset || size == 0) clear();
        videoList.addAll(list);
        notifyItemRangeInserted(size, videoList.size());
    }

    public void clearCheckedVideos(){
        int size = checkedVideos.size();
        for (int i = 0; i < size; i++){
            int position = checkedVideos.get(i);
            notifyItemChanged(position);
        }
        checkedVideos.clear();
    }

    public void clear(){
        int size = videoList.size();
        videoList = new ArrayList<>();
        notifyItemRangeRemoved(0, size);
    }

}
