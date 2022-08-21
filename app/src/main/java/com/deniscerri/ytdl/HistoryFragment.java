package com.deniscerri.ytdl;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.deniscerri.ytdl.database.DBManager;
import com.deniscerri.ytdl.database.Video;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 */
public class HistoryFragment extends Fragment implements View.OnClickListener{

    private LinearLayout linearLayout;
    private ScrollView scrollView;
    private View fragmentView;
    private DBManager dbManager;
    Context context;
    private LayoutInflater layoutinflater;
    private ShimmerFrameLayout shimmerCards;
    private MaterialToolbar topAppBar;

    private static final String TAG = "HistoryFragment";


    public HistoryFragment() {
    }

    @SuppressWarnings("unused")
    public static HistoryFragment newInstance() {
        HistoryFragment fragment = new HistoryFragment();
        Bundle args = new Bundle();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        fragmentView = inflater.inflate(R.layout.fragment_history, container, false);
        context = fragmentView.getContext();
        layoutinflater = LayoutInflater.from(context);
        linearLayout = fragmentView.findViewById(R.id.linearLayout1);
        scrollView = fragmentView.findViewById(R.id.scrollView1);
        shimmerCards = fragmentView.findViewById(R.id.shimmer_history_framelayout);
        topAppBar = fragmentView.findViewById(R.id.history_toolbar);

        dbManager = new DBManager(context);

        SwipeRefreshLayout swipeRefreshLayout = fragmentView.findViewById(R.id.swiperefresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            initCards();
            swipeRefreshLayout.setRefreshing(false);
        });

        FloatingActionButton fab = fragmentView.findViewById(R.id.fab_history);
        fab.setOnClickListener(this);
        scrollView.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if( y > 500){
                fab.show();
            }else{
                fab.hide();
            }
        });
        initMenu();
        initCards();
        return fragmentView;
    }


    private void initCards(){
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        linearLayout.removeAllViews();
        try{
            Thread thread = new Thread(() -> {
                Handler uiHandler = new Handler(Looper.getMainLooper());
                ArrayList<Video> historyObjects = dbManager.getHistory();
                for(int i = historyObjects.size()-1; i >= 0; i--){
                    createCard(historyObjects.get(i));
                }
                TextView padding = new TextView(context);
                int dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, context.getResources().getDisplayMetrics());
                padding.setHeight(dp);
                padding.setGravity(Gravity.CENTER);

                uiHandler.post(() -> {
                    linearLayout.addView(padding);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                });
            });
            thread.start();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

    }

    public void scrollToTop(){
        scrollView.smoothScrollTo(0,0);
    }

    private void createCard(Video video){
        RelativeLayout r = new RelativeLayout(context);
        layoutinflater.inflate(R.layout.history_card, r);

        CardView card = r.findViewById(R.id.history_card_view);
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

        uiHandler.post(() -> linearLayout.addView(r));
    }

    private void initMenu(){
        topAppBar.setOnClickListener(view -> {
            scrollToTop();
        });

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            switch (m.getItemId()){
                case R.id.delete_history:
                    dbManager.clearHistory();
                    linearLayout.removeAllViews();
                    return true;
            }
            return true;
        });

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab_history) {
            scrollView.smoothScrollBy(0, 0);
            scrollView.smoothScrollTo(0, 0);
        }
    }
}