package com.deniscerri.ytdlnis.adapter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.database.models.HistoryItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class HistoryRecyclerViewAdapter extends ListAdapter<HistoryItem, HistoryRecyclerViewAdapter.ViewHolder> {
    private ArrayList<Integer> checkedItems;
    private final OnItemClickListener onItemClickListener;
    private Activity activity;

    public HistoryRecyclerViewAdapter(OnItemClickListener onItemClickListener, Activity activity) {
        super(DIFF_CALLBACK);
        this.checkedItems = new ArrayList<>();
        this.onItemClickListener = onItemClickListener;
        this.activity = activity;
    }

    private static final DiffUtil.ItemCallback<HistoryItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<HistoryItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull HistoryItem oldItem, @NonNull HistoryItem newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull HistoryItem oldItem, @NonNull HistoryItem newItem) {
            return oldItem.getTime() == newItem.getTime();
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
            cardView = itemView.findViewById(R.id.downloads_card_view);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View cardView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.downloads_card, parent, false);

        return new HistoryRecyclerViewAdapter.ViewHolder(cardView, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem video = getItem(position);
        Log.e("AAA", String.valueOf(position));
        MaterialCardView card = holder.cardView;
        // THUMBNAIL ----------------------------------
        ImageView thumbnail = card.findViewById(R.id.downloads_image_view);
        String imageURL= video.getThumb();

        Handler uiHandler = new Handler(Looper.getMainLooper());
        if (!imageURL.isEmpty()){
            uiHandler.post(() -> Picasso.get().load(imageURL).into(thumbnail));
        }else {
            uiHandler.post(() -> Picasso.get().load(R.color.black).into(thumbnail));
        }
        thumbnail.setColorFilter(Color.argb(95, 0, 0, 0));

        // TITLE  ----------------------------------
        TextView videoTitle = card.findViewById(R.id.downloads_title);
        String title = video.getTitle();

        if(title.length() > 100){
            title = title.substring(0, 40) + "...";
        }
        videoTitle.setText(title);

        // Bottom Info ----------------------------------
        TextView bottomInfo = card.findViewById(R.id.downloads_info_bottom);
        String info = video.getAuthor();
        if (!video.getDuration().isEmpty()){
            if (!video.getAuthor().isEmpty()) info += " • ";
            info += video.getDuration();
        }
        bottomInfo.setText(info);

        // TIME DOWNLOADED  ----------------------------------
        TextView datetime = card.findViewById(R.id.downloads_info_time);
        long time = video.getTime();
        String downloadedTime;
        if (time == 0){
            downloadedTime = activity.getString(R.string.currently_downloading) + " " + video.getType();
        }else{
            Calendar cal = Calendar.getInstance();
            Date date = new Date(time*1000L);
            cal.setTime(date);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
            int year = cal.get(Calendar.YEAR);

            DateFormat formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String timeString = formatter.format(date);
            downloadedTime = day + " " + month + " " + year + " - " + timeString;
        }
        datetime.setText(downloadedTime);

        // BUTTON ----------------------------------
        LinearLayout buttonLayout = card.findViewById(R.id.downloads_download_button_layout);
        MaterialButton btn = buttonLayout.findViewById(R.id.downloads_download_button_type);

        boolean filePresent = true;

        //IS IN THE FILE SYSTEM?
        String path = video.getDownloadPath();
        File file = new File(path);
        if(!file.exists() && !path.isEmpty()){
            filePresent = false;
            thumbnail.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(){{setSaturation(0f);}}));
            thumbnail.setAlpha(0.7f);
        }

        if(!video.getType().isEmpty()){
            if(video.getType().equals("audio")){
                if (filePresent) btn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
                else btn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music));
            }else{
                if (filePresent) btn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded));
                else btn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video));
            }
        }

        if (btn.hasOnClickListeners()) btn.setOnClickListener(null);

        if(checkedItems.contains(position)){
            card.setChecked(true);
            card.setStrokeWidth(5);
        }else{
            card.setChecked(false);
            card.setStrokeWidth(0);
        }

        boolean finalFilePresent = filePresent;
        card.setOnLongClickListener(view -> {
            checkCard(card, position);
            return true;
        });
        card.setOnClickListener(view -> {
            if(checkedItems.size() > 0){
                checkCard(card, video.getId());
            }else{
                onItemClickListener.onCardClick(video.getId(), finalFilePresent);
            }
        });
    }


    private void checkCard(MaterialCardView card, int videoID){
        if(card.isChecked()){
            card.setStrokeWidth(0);
            checkedItems.remove(videoID);
        }else{
            card.setStrokeWidth(5);
            checkedItems.add(videoID);
        }
        card.setChecked(!card.isChecked());
        onItemClickListener.onCardSelect(videoID, card.isChecked());
    }

    public interface OnItemClickListener {
        void onCardClick(int position, boolean isPresent);
        void onCardSelect(int position, boolean isChecked);
        void onButtonClick(int position);
    }

    public void clearCheckedVideos(){
        int size = checkedItems.size();
        for (int i = 0; i < size; i++){
            int position = checkedItems.get(i);
            notifyItemChanged(position);
        }
        checkedItems.clear();
    }
}
