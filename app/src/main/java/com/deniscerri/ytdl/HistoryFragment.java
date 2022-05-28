package com.deniscerri.ytdl;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputType;
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
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.deniscerri.ytdl.database.DBManager;
import com.deniscerri.ytdl.database.Video;
import com.deniscerri.ytdl.placeholder.PlaceholderContent;
import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 */
public class HistoryFragment extends Fragment {

    private LinearLayout linearLayout;
    private ScrollView scrollView;
    private View fragmentView;
    private DBManager dbManager;
    private ArrayList<Video> historyObjects;


    public HistoryFragment() {
    }

    // TODO: Customize parameter initialization
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
        initViews();

        dbManager = new DBManager(requireContext());
        historyObjects = dbManager.merrHistorine();

        System.out.println(historyObjects);

        for(int i = historyObjects.size()-1; i >= 0; i--){
            createCard(historyObjects.get(i));
        }

        return fragmentView;
    }

    private int getDp(int value){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void createCard(Video video) {

        RelativeLayout r = new RelativeLayout(getContext());
        r.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
        r.getLayoutParams().height = getDp(130);

        CardView card = new CardView(requireContext());
        card.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(50);
        card.setCardElevation(10);
        card.setMaxCardElevation(12);
        card.setPreventCornerOverlap(true);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));

        // THUMBNAIL ----------------------------------

        ImageView thumbnail = new ImageView(getContext());
        thumbnail.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        String imageURL= video.getThumb();


        Picasso.get().load(imageURL).into(thumbnail);
        thumbnail.setAdjustViewBounds(false);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // TITLE  ----------------------------------
        TextView videoTitle = new TextView(getContext());
        videoTitle.setLayoutParams(new RelativeLayout.LayoutParams(getDp(300), getDp(100)));
        int padding = getDp(20);
        videoTitle.setPadding(padding, padding, padding, padding);

        videoTitle.setText(video.getTitle());
        videoTitle.setTextSize(getDp(5));
        videoTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoTitle.setShadowLayer(1, 1, 1, ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));
        videoTitle.setTypeface(null, Typeface.BOLD);
        videoTitle.setShadowLayer(1.5f, 4f, 4f, R.color.black);

        // AUTHOR ----------------------------------

        TextView videoAuthor = new TextView(getContext());
        videoAuthor.setGravity(Gravity.BOTTOM);
        videoAuthor.setLayoutParams(new RelativeLayout.LayoutParams(getDp(100), getDp(100)));
        videoAuthor.setPadding(getDp(20), 0, 0, getDp(10));
        videoAuthor.setShadowLayer(1.5f, 4f, 4f, R.color.black);

        videoAuthor.setText(video.getAuthor());
        videoAuthor.setTextSize(getDp(3));
        videoAuthor.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoAuthor.setShadowLayer(1, 1, 1, ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));
        videoAuthor.setTypeface(null, Typeface.BOLD);

        // DATE ---------------------------------------------

        TextView date = new TextView(getContext());
        date.setGravity(Gravity.BOTTOM);
        date.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, getDp(100)));
        date.setPadding(getDp(70), 0, getDp(100), getDp(10));
        date.setShadowLayer(1.5f, 4f, 4f, R.color.black);

        date.setText(video.getDownloadedTime());
        date.setTextSize(getDp(3));
        date.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        date.setShadowLayer(1, 1, 1, ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark));
        date.setTypeface(null, Typeface.BOLD);

        // BUTTONS -------------------------------------------


        LinearLayout buttonLayout = new LinearLayout(requireContext());
        buttonLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        buttonLayout.setGravity(Gravity.BOTTOM);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(getDp(300), 0, getDp(20), getDp(30));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getDp(48), getDp(48)
        );

        if(video.getDownloadedType().equals("mp3")){
            Button musicBtn = new Button(getContext());

            params.setMargins(0,0, getDp(10), 0);
            musicBtn.setLayoutParams(params);
            //musicBtn.setIconSize(getDp(24));
            musicBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_music));

            buttonLayout.addView(musicBtn);
        }else{
            Button videoBtn = new Button(requireContext());
            videoBtn.setLayoutParams(params);
            //videoBtn.setIconSize(getDp(24));
            videoBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_video));

            buttonLayout.addView(videoBtn);
        }

        card.addView(thumbnail);
        card.addView(videoTitle);
        card.addView(videoAuthor);
        card.addView(date);
        card.addView(buttonLayout);

        r.addView(card);

        linearLayout.addView(r);
    }


    private void initViews() {
        linearLayout = fragmentView.findViewById(R.id.linearLayout1);
        scrollView = fragmentView.findViewById(R.id.scrollView1);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        inflater.inflate(R.menu.history_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_history:
                dbManager.clearHistory();
                linearLayout.removeAllViews();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }
}