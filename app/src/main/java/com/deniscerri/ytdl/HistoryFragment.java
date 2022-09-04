package com.deniscerri.ytdl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.deniscerri.ytdl.adapter.HistoryRecyclerViewAdapter;
import com.deniscerri.ytdl.database.DBManager;
import com.deniscerri.ytdl.database.Video;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 */
public class HistoryFragment extends Fragment implements HistoryRecyclerViewAdapter.OnItemClickListener, View.OnClickListener{

    private LinearLayout linearLayout;
    private View fragmentView;
    private DBManager dbManager;
    Context context;
    private LayoutInflater layoutinflater;
    private ShimmerFrameLayout shimmerCards;
    private MaterialToolbar topAppBar;
    private RecyclerView recyclerView;
    private HistoryRecyclerViewAdapter historyRecyclerViewAdapter;
    private BottomSheetDialog bottomSheet;

    private ArrayList<Video> historyObjects;
    private static final String TAG = "HistoryFragment";
    private static final String youtubeVideoURL = "https://www.youtube.com/watch?v=";

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
        linearLayout = fragmentView.findViewById(R.id.historylinearLayout1);
        shimmerCards = fragmentView.findViewById(R.id.shimmer_history_framelayout);
        topAppBar = fragmentView.findViewById(R.id.history_toolbar);

        dbManager = new DBManager(context);
        historyObjects = new ArrayList<>();

        recyclerView = fragmentView.findViewById(R.id.recycler_view_history);

        historyRecyclerViewAdapter = new HistoryRecyclerViewAdapter(historyObjects, this, context);
        recyclerView.setAdapter(historyRecyclerViewAdapter);
        recyclerView.setNestedScrollingEnabled(false);

        initMenu();
        initCards();
        return fragmentView;
    }


    private void initCards(){
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        historyRecyclerViewAdapter.clear();
        linearLayout.removeAllViews();
        try{
            Thread thread = new Thread(() -> {
                Handler uiHandler = new Handler(Looper.getMainLooper());
                historyObjects = dbManager.getHistory("");

                uiHandler.post(() -> {
                    historyRecyclerViewAdapter.setVideoList(historyObjects);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                });

                if(historyObjects.size() == 0){
                    uiHandler.post(() -> {
                        RelativeLayout no_results = new RelativeLayout(context);
                        layoutinflater.inflate(R.layout.history_no_results, no_results);
                        linearLayout.addView(no_results);
                    });
                }
            });
            thread.start();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

    }

    public void scrollToTop(){
        recyclerView.smoothScrollToPosition(0);
    }

    private void addNoResultsView(){
        RelativeLayout no_results = new RelativeLayout(context);
        layoutinflater.inflate(R.layout.history_no_results, no_results);
        linearLayout.addView(no_results);
    }

    private void initMenu(){
        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                return true;
            }
        };

        topAppBar.getMenu().findItem(R.id.search_history).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) topAppBar.getMenu().findItem(R.id.search_history).getActionView();
        searchView.setInputType(InputType.TYPE_CLASS_TEXT);
        searchView.setQueryHint(getString(R.string.search_history_hint));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                topAppBar.getMenu().findItem(R.id.search_history).collapseActionView();
                historyObjects = dbManager.getHistory(query);
                historyRecyclerViewAdapter.clear();
                historyRecyclerViewAdapter.setVideoList(historyObjects);

                if(historyObjects.size() == 0) addNoResultsView();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {

                historyObjects = dbManager.getHistory(newText);
                historyRecyclerViewAdapter.clear();
                historyRecyclerViewAdapter.setVideoList(historyObjects);

                if (historyObjects.size() == 0) {
                    linearLayout.removeAllViews();
                    addNoResultsView();
                }else linearLayout.removeAllViews();

                return true;
            }
        });

        topAppBar.setOnClickListener(view -> scrollToTop());

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            switch (m.getItemId()){
                case R.id.delete_history:
                    if(historyObjects.size() == 0){
                        Toast.makeText(context, "History is Empty!", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(context);
                    delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                    delete_dialog.setMessage(getString(R.string.confirm_delete_history_desc));
                    delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                        dialogInterface.cancel();
                    });
                    delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                        dbManager.clearHistory();
                        historyRecyclerViewAdapter.clear();
                        addNoResultsView();
                    });
                    delete_dialog.show();
                    return true;
                case R.id.refresh_history:
                    historyRecyclerViewAdapter.clear();
                    initCards();
                    return true;
            }
            return true;
        });

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id){
            case R.id.bottomsheet_remove_button:
                removeHistoryItem((Integer) v.getTag());
                break;
            case R.id.bottom_sheet_link:
                copyLinkToClipBoard((Integer) v.getTag());
                break;
            case R.id.bottomsheet_open_link_button:
                openLinkIntent((Integer) v.getTag());
                break;
        }
    }

    private void removeHistoryItem(int position){
        if(bottomSheet != null) bottomSheet.hide();

        Video v = historyObjects.get(position);
        MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(context);
        delete_dialog.setTitle(getString(R.string.confirm_delete_history));
        delete_dialog.setMessage("You are going to delete \""+v.getTitle()+"\"!");
        delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
            dialogInterface.cancel();
        });
        delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
            historyObjects.remove(position);
            historyRecyclerViewAdapter.notifyItemRemoved(position);
            historyRecyclerViewAdapter.notifyItemRangeChanged(position, historyObjects.size());
            dbManager.clearHistoryItem(v);
        });
        delete_dialog.show();
    }

    private void copyLinkToClipBoard(int position){
        String url = youtubeVideoURL + historyObjects.get(position).getVideoId();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Youtube URL", url);
        clipboard.setPrimaryClip(clip);
        if(bottomSheet != null) bottomSheet.hide();
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void openLinkIntent(int position){
        String url = youtubeVideoURL + historyObjects.get(position).getVideoId();
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        if(bottomSheet != null) bottomSheet.hide();
        startActivity(i);
    }

    @Override
    public void onCardClick(int position) {
        bottomSheet = new BottomSheetDialog(context);
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        bottomSheet.setContentView(R.layout.history_bottom_sheet);
        Video video = historyObjects.get(position);

        TextView title = bottomSheet.findViewById(R.id.bottom_sheet_title);
        title.setText(video.getTitle());

        TextView author = bottomSheet.findViewById(R.id.bottom_sheet_author);
        author.setText(video.getAuthor());

        Button link = bottomSheet.findViewById(R.id.bottom_sheet_link);
        String url = youtubeVideoURL+video.getVideoId();
        link.setText(url);
        link.setTag(position);
        link.setOnClickListener(this);

        Button remove = bottomSheet.findViewById(R.id.bottomsheet_remove_button);
        remove.setTag(position);
        remove.setOnClickListener(this);

        Button openLink = bottomSheet.findViewById(R.id.bottomsheet_open_link_button);
        openLink.setTag(position);
        openLink.setOnClickListener(this);

        bottomSheet.show();
        bottomSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
    }


}