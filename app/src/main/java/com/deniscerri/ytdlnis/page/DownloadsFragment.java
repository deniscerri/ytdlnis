package com.deniscerri.ytdlnis.page;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
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
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.adapter.DownloadsRecyclerViewAdapter;
import com.deniscerri.ytdlnis.database.DBManager;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.service.DownloadInfo;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 */
public class DownloadsFragment extends Fragment implements DownloadsRecyclerViewAdapter.OnItemClickListener, View.OnClickListener{

    private View fragmentView;
    private DBManager dbManager;
    Context context;
    Activity activity;
    Context fragmentContext;
    private LayoutInflater layoutinflater;
    private ShimmerFrameLayout shimmerCards;
    private MaterialToolbar topAppBar;
    private RecyclerView recyclerView;
    private DownloadsRecyclerViewAdapter downloadsRecyclerViewAdapter;
    private BottomSheetDialog bottomSheet;
    private BottomSheetDialog sortSheet;
    private Handler uiHandler;
    private RelativeLayout no_results;
    private LinearLayout selectionChips;
    private ChipGroup websiteGroup;
    private ArrayList<Video> downloadsObjects;
    private String format = "";
    private String website = "";
    private String sort = "DESC";
    private String searchQuery = "";

    private static final String TAG = "downloadsFragment";


    public IDownloaderListener listener = new IDownloaderListener() {

        public void onDownloadStart(DownloadInfo downloadInfo) {
            try{

            }catch(Exception ignored){}
        }

        public void onDownloadProgress(DownloadInfo info) {
            activity.runOnUiThread(() -> {
                try{

                }catch(Exception ignored){}
            });
        }

        public void onDownloadError(DownloadInfo info){
            try{

            }catch(Exception ignored){}
        }

        public void onDownloadEnd(DownloadInfo downloadInfo) {
            try{

            }catch(Exception ignored){}
        }

        @Override
        public void onDownloadCancel(DownloadInfo downloadInfo) {

        }

        public void onDownloadServiceEnd() {}
    };


    public DownloadsFragment() {
    }

    @SuppressWarnings("unused")
    public static DownloadsFragment newInstance() {
        DownloadsFragment fragment = new DownloadsFragment();
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

        fragmentView = inflater.inflate(R.layout.fragment_downloads, container, false);
        context = fragmentView.getContext().getApplicationContext();
        activity = getActivity();
        fragmentContext = fragmentView.getContext();
        layoutinflater = LayoutInflater.from(context);
        shimmerCards = fragmentView.findViewById(R.id.shimmer_downloads_framelayout);
        topAppBar = fragmentView.findViewById(R.id.downloads_toolbar);
        no_results = fragmentView.findViewById(R.id.downloads_no_results);
        selectionChips = fragmentView.findViewById(R.id.downloads_selection_chips);
        websiteGroup = fragmentView.findViewById(R.id.website_chip_group);
        uiHandler = new Handler(Looper.getMainLooper());

        dbManager = new DBManager(context);
        downloadsObjects = new ArrayList<>();

        recyclerView = fragmentView.findViewById(R.id.recycler_view_downloads);

        downloadsRecyclerViewAdapter = new DownloadsRecyclerViewAdapter(downloadsObjects, this, context);
        recyclerView.setAdapter(downloadsRecyclerViewAdapter);
        recyclerView.setNestedScrollingEnabled(false);

        initMenu();
        initChips();
        initCards();
        return fragmentView;
    }


    public void initCards(){
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        downloadsRecyclerViewAdapter.clear();
        no_results.setVisibility(View.GONE);
        selectionChips.setVisibility(View.VISIBLE);
        try{
            Thread thread = new Thread(() -> {
                downloadsObjects = dbManager.getHistory("", format,website,sort);
                uiHandler.post(() -> {
                    downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                    updateWebsiteChips();
                });

                if(downloadsObjects.size() == 0){
                    uiHandler.post(() -> {
                        no_results.setVisibility(View.VISIBLE);
                        selectionChips.setVisibility(View.GONE);
                        websiteGroup.removeAllViews();
                    });
                }
            });
            thread.start();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }

    }

    public void scrollToTop(){
        recyclerView.scrollToPosition(0);
        new Handler(Looper.getMainLooper()).post(() -> ((AppBarLayout) topAppBar.getParent()).setExpanded(true, true));
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

        topAppBar.getMenu().findItem(R.id.search_downloads).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) topAppBar.getMenu().findItem(R.id.search_downloads).getActionView();
        searchView.setInputType(InputType.TYPE_CLASS_TEXT);
        searchView.setQueryHint(getString(R.string.search_history_hint));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                topAppBar.getMenu().findItem(R.id.search_downloads).collapseActionView();
                downloadsObjects = dbManager.getHistory(query, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);

                if(downloadsObjects.size() == 0) {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                downloadsObjects = dbManager.getHistory(newText, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);

                if (downloadsObjects.size() == 0) {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                }else{
                    no_results.setVisibility(View.GONE);
                    selectionChips.setVisibility(View.VISIBLE);
                }

                return true;
            }
        });

        topAppBar.setOnClickListener(view -> scrollToTop());

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            int itemID = m.getItemId();
            if(itemID == R.id.remove_downloads) {
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearHistory();
                    downloadsRecyclerViewAdapter.clear();
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                });
                delete_dialog.show();
            }else if(itemID == R.id.remove_deleted_downloads){
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_deleted_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearDeletedHistory();
                    initCards();
                });
                delete_dialog.show();
            }else if (itemID == R.id.remove_duplicates){
                if (downloadsObjects.size() == 0) {
                    Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show();
                    return true;
                }

                MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
                delete_dialog.setTitle(getString(R.string.confirm_delete_history));
                delete_dialog.setMessage(getString(R.string.confirm_delete_history_duplicates_desc));
                delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    dialogInterface.cancel();
                });
                delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dbManager.clearDuplicateHistory();
                    initCards();
                });
                delete_dialog.show();
            }
            return true;
        });

    }

    private void initChips(){
        //sort
        Chip sortChip = fragmentView.findViewById(R.id.sort_chip);
        sortChip.setOnClickListener(view -> {
            sortSheet = new BottomSheetDialog(fragmentContext);
            sortSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
            sortSheet.setContentView(R.layout.downloads_sort_sheet);

            TextView newest = sortSheet.findViewById(R.id.newest);
            TextView oldest = sortSheet.findViewById(R.id.oldest);

            newest.setOnClickListener(view1 -> {
                sort = "DESC";
                downloadsObjects = dbManager.getHistory(searchQuery, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                sortSheet.cancel();
            });

            oldest.setOnClickListener(view1 -> {
                sort = "ASC";
                downloadsObjects = dbManager.getHistory(searchQuery, format,website,sort);
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
                sortSheet.cancel();
            });

            TextView cancel = sortSheet.findViewById(R.id.cancel);
            cancel.setOnClickListener(view1 -> {
                sortSheet.cancel();
            });

            sortSheet.show();
            sortSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        });

        //format
        Chip audio = fragmentView.findViewById(R.id.audio_chip);
        audio.setOnClickListener(view -> {
            if (audio.isChecked()) {
                format = "audio";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                audio.setChecked(true);
            }else {
                format = "";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                audio.setChecked(false);
            }

            downloadsRecyclerViewAdapter.clear();
            downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
        });

        Chip video = fragmentView.findViewById(R.id.video_chip);
        video.setOnClickListener(view -> {
            if (video.isChecked()) {
                format = "video";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                video.setChecked(true);
            }else {
                format = "";
                downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                video.setChecked(false);
            }

            downloadsRecyclerViewAdapter.clear();
            downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
        });

    }

    private void updateWebsiteChips(){
        websiteGroup.removeAllViews();

        ArrayList<String> websites = downloadsRecyclerViewAdapter.getWebsites();
        for (int i = 0; i < websites.size(); i++){
            String w = websites.get(i);
            Chip tmp = (Chip) layoutinflater.inflate(R.layout.filter_chip, websiteGroup, false);
            tmp.setText(w);
            tmp.setId(i);
            tmp.setOnClickListener(view -> {
                if (tmp.isChecked()){
                    website = (String) tmp.getText();
                    downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                    websiteGroup.check(view.getId());
                }else{
                    website = "";
                    downloadsObjects = dbManager.getHistory(searchQuery,format,website,sort);
                    websiteGroup.clearCheck();
                }
                downloadsRecyclerViewAdapter.clear();
                downloadsRecyclerViewAdapter.setVideoList(downloadsObjects);
            });
            websiteGroup.addView(tmp);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.bottomsheet_remove_button){
            removedownloadsItem((Integer) v.getTag());
        }else if(id == R.id.bottom_sheet_link){
            copyLinkToClipBoard((Integer) v.getTag());
        }else if(id == R.id.bottomsheet_open_link_button){
            openLinkIntent((Integer) v.getTag());
        }
    }

    private void removedownloadsItem(int position){
        if(bottomSheet != null) bottomSheet.hide();

        Video v = downloadsObjects.get(position);
        MaterialAlertDialogBuilder delete_dialog = new MaterialAlertDialogBuilder(fragmentContext);
        delete_dialog.setTitle(getString(R.string.confirm_delete_history));
        delete_dialog.setMessage(getString(R.string.you_are_going_to_delete) + " \""+v.getTitle()+"\"!");
        delete_dialog.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
            dialogInterface.cancel();
        });
        delete_dialog.setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
            downloadsObjects.remove(position);
            downloadsRecyclerViewAdapter.notifyItemRemoved(position);
            downloadsRecyclerViewAdapter.setWebsiteList();
            updateWebsiteChips();
            dbManager.clearHistoryItem(v);

            if(downloadsObjects.size() == 0){
                uiHandler.post(() -> {
                    no_results.setVisibility(View.VISIBLE);
                    selectionChips.setVisibility(View.GONE);
                    websiteGroup.removeAllViews();
                });
            }
        });
        delete_dialog.show();
    }

    private void copyLinkToClipBoard(int position){
        String url = downloadsObjects.get(position).getURL();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.url), url);
        clipboard.setPrimaryClip(clip);
        if(bottomSheet != null) bottomSheet.hide();
        Toast.makeText(context, getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    private void openLinkIntent(int position){
        String url =downloadsObjects.get(position).getURL();
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        if(bottomSheet != null) bottomSheet.hide();
        startActivity(i);
    }

    @Override
    public void onCardClick(int position) {
        bottomSheet = new BottomSheetDialog(fragmentContext);
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        bottomSheet.setContentView(R.layout.downloads_bottom_sheet);
        Video video = downloadsObjects.get(position);

        TextView title = bottomSheet.findViewById(R.id.bottom_sheet_title);
        title.setText(video.getTitle());

        TextView author = bottomSheet.findViewById(R.id.bottom_sheet_author);
        author.setText(video.getAuthor());

        Button link = bottomSheet.findViewById(R.id.bottom_sheet_link);
        String url = video.getURL();
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