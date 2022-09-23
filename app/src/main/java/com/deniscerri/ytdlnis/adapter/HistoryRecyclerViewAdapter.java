package com.deniscerri.ytdlnis.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.database.Video;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class HistoryRecyclerViewAdapter extends RecyclerView.Adapter<HistoryRecyclerViewAdapter.ViewHolder> {
    private ArrayList<Video> videoList;
    private final OnItemClickListener onItemClickListener;
    private Context context;

    public HistoryRecyclerViewAdapter(ArrayList<Video> videos, OnItemClickListener onItemClickListener, Context context){
        this.videoList = videos;
        this.onItemClickListener = onItemClickListener;
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private CardView cardView;

        public ViewHolder(@NonNull View itemView, OnItemClickListener onItemClickListener) {
            super(itemView);
            cardView = itemView.findViewById(R.id.history_card_view);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View cardView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.history_card, parent, false);

        return new HistoryRecyclerViewAdapter.ViewHolder(cardView, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videoList.get(position);
        CardView card = holder.cardView;
        // THUMBNAIL ----------------------------------
        ImageView thumbnail = card.findViewById(R.id.history_image_view);
        String imageURL= video.getThumb();

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> Picasso.get().load(imageURL).into(thumbnail));
        thumbnail.setColorFilter(Color.argb(70, 0, 0, 0));

        // TITLE  ----------------------------------
        TextView videoTitle = card.findViewById(R.id.history_title);
        String title = video.getTitle();

        if(title.length() > 100){
            title = title.substring(0, 40) + "...";
        }
        videoTitle.setText(title);

        // Bottom Info ----------------------------------
        TextView bottomInfo = card.findViewById(R.id.history_info_bottom);
        String info = video.getAuthor() + " • " + video.getDuration();
        bottomInfo.setText(info);

        // TIME DOWNLOADED  ----------------------------------
        TextView datetime = card.findViewById(R.id.history_info_time);
        String downloadedTime = video.getDownloadedTime();
        datetime.setText(downloadedTime);

        // BUTTON ----------------------------------
        LinearLayout buttonLayout = card.findViewById(R.id.history_download_button_layout);
        Button btn = buttonLayout.findViewById(R.id.history_download_button_type);
        if(video.getDownloadedType().equals("mp3")){
            btn.setBackground(ContextCompat.getDrawable(context, R.drawable.ic_music));
        }else{
            btn.setBackground(ContextCompat.getDrawable(context, R.drawable.ic_video));
        }

        card.setOnClickListener(view -> onItemClickListener.onCardClick(position));
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public interface OnItemClickListener {
        void onCardClick(int position);
    }

    public void setVideoList(ArrayList<Video> videoList){
        this.videoList = videoList;
        notifyDataSetChanged();
    }

    public void clear(){
        int size = videoList.size();
        videoList.clear();
        notifyItemRangeRemoved(0, size);
    }

}
