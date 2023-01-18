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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.database.models.ResultItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class HomeAdapter extends ListAdapter<ResultItem, HomeAdapter.ViewHolder> {
    private final ArrayList<Integer> checkedVideos;
    private final OnItemClickListener onItemClickListener;
    private Activity activity;

    public HomeAdapter(HomeAdapter.OnItemClickListener onItemClickListener, Activity activity){
        super(DIFF_CALLBACK);
        this.checkedVideos = new ArrayList<>();
        this.onItemClickListener = onItemClickListener;
        this.activity = activity;
        Log.e("TAG", "adapter");
    }

    private static final DiffUtil.ItemCallback<ResultItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ResultItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ResultItem oldItem, @NonNull ResultItem newItem) {
            Log.e("TAG", "adapter2");
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ResultItem oldItem, @NonNull ResultItem newItem) {
            Log.e("TAG", "adapter3");
            return oldItem.getUrl().equals(newItem.getUrl());
        }
    };

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
        return new HomeAdapter.ViewHolder(cardView, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ResultItem video = getItem(position);
        Log.e("TAG", "adapter4");
        MaterialCardView card = holder.cardView;
        // THUMBNAIL ----------------------------------
        ImageView thumbnail = card.findViewById(R.id.result_image_view);
        String imageURL= video.getThumb();
        if (!imageURL.isEmpty()){
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> Picasso.get().load(imageURL).into(thumbnail));
            thumbnail.setColorFilter(Color.argb(70, 0, 0, 0));
        }else {
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> Picasso.get().load(R.color.black).into(thumbnail));
            thumbnail.setColorFilter(Color.argb(70, 0, 0, 0));
        }

        // TITLE  ----------------------------------
        TextView videoTitle = card.findViewById(R.id.result_title);
        String title = video.getTitle();

        if(title.length() > 100){
            title = title.substring(0, 40) + "...";
        }
        videoTitle.setText(title);

        // Bottom Info ----------------------------------

        TextView author = card.findViewById(R.id.author);
        author.setText(video.getAuthor());

        TextView duration = card.findViewById(R.id.duration);
        if (!video.getDuration().isEmpty()){
            duration.setText(video.getDuration());
        }

        // BUTTONS ----------------------------------
        int videoID = video.getId();

        LinearLayout buttonLayout = card.findViewById(R.id.download_button_layout);

        MaterialButton musicBtn = buttonLayout.findViewById(R.id.download_music);
        musicBtn.setTag(videoID + "##audio");
        musicBtn.setTag(R.id.cancelDownload, "false");
        musicBtn.setOnClickListener(view -> onItemClickListener.onButtonClick(position, "audio"));

        MaterialButton videoBtn = buttonLayout.findViewById(R.id.download_video);
        videoBtn.setTag(videoID + "##video");
        videoBtn.setTag(R.id.cancelDownload, "false");
        videoBtn.setOnClickListener(view -> onItemClickListener.onButtonClick(position, "video"));


        // PROGRESS BAR ----------------------------------------------------

        LinearProgressIndicator progressBar = card.findViewById(R.id.download_progress);
        progressBar.setTag(videoID + "##progress");
        progressBar.setProgress(0);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);

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


        if(checkedVideos.contains(videoID)){
            card.setChecked(true);
            card.setStrokeWidth(5);
        }else{
            card.setChecked(false);
            card.setStrokeWidth(0);
        }

        card.setTag(videoID + "##card");
        card.setOnLongClickListener(view -> {
            checkCard(card, videoID);
            return true;
        });
        card.setOnClickListener(view -> {
            if(checkedVideos.size() > 0){
                checkCard(card, videoID);
            }
        });
    }

    private void checkCard(MaterialCardView card, int videoID){
        if(card.isChecked()){
            card.setStrokeWidth(0);
            checkedVideos.remove(Integer.valueOf(videoID));
        }else{
            card.setStrokeWidth(5);
            checkedVideos.add(videoID);
        }
        card.setChecked(!card.isChecked());
        onItemClickListener.onCardClick(videoID, card.isChecked());
    }


    public interface OnItemClickListener {
        void onButtonClick(int videoID, String type);
        void onCardClick(int videoID, boolean add);
    }

    public void clearCheckedVideos(){
        int size = checkedVideos.size();
        for (int i = 0; i < size; i++){
            int position = checkedVideos.get(i);
            notifyItemChanged(position);
        }
        checkedVideos.clear();
    }
}
