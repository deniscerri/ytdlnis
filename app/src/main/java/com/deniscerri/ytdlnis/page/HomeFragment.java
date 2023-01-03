package com.deniscerri.ytdlnis.page;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.deniscerri.ytdlnis.MainActivity;
import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.adapter.HomeRecyclerViewAdapter;
import com.deniscerri.ytdlnis.util.InfoUtil;
import com.deniscerri.ytdlnis.database.DatabaseManager;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.service.DownloadInfo;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HomeFragment extends Fragment implements HomeRecyclerViewAdapter.OnItemClickListener, View.OnClickListener {
    private boolean downloading = false;
    private View fragmentView;
    private LinearProgressIndicator progressBar;
    private String inputQuery;
    private LinkedList<String> inputQueries;
    private int inputQueriesLength = 0;
    private RecyclerView recyclerView;
    private HomeRecyclerViewAdapter homeRecyclerViewAdapter;
    private ScrollView searchSuggestions;
    private LinearLayout searchSuggestionsLinearLayout;
    private ShimmerFrameLayout shimmerCards;
    private CoordinatorLayout downloadFabs;
    private CoordinatorLayout downloadAllFab;
    private CoordinatorLayout homeFabs;
    private BottomSheetDialog bottomSheet;
    Context context;
    Context fragmentContext;
    Activity activity;
    private MaterialToolbar topAppBar;

    private static final String TAG = "HomeFragment";

    private ArrayList<Video> resultObjects;
    public ArrayList<Video> selectedObjects;
    private DatabaseManager databaseManager;
    private InfoUtil infoUtil;
    private ArrayList<Video> downloadQueue;
    MainActivity mainActivity;
    private DownloadInfo downloadInfo;

    public IDownloaderListener listener = new IDownloaderListener() {

        public void onDownloadStart(DownloadInfo info) {
            downloading = true;
            downloadInfo = info;
            try{
                if(downloadInfo != null){
                    Video video = downloadInfo.getVideo();
                    String url = video.getURL();
                    String type = video.getDownloadedType();
                    video = findVideo(url);
                    recyclerView.smoothScrollToPosition(resultObjects.indexOf(video));
                    updateDownloadingStatusOnResult(video, type, true);
                    progressBar = fragmentView.findViewWithTag(downloadInfo.getVideo().getVideoId()+"##progress");
                    topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(true);
                }
            }catch(Exception ignored){}
        }

        public void onDownloadProgress(DownloadInfo info) {
            downloadInfo = info;
            downloading = true;
            activity.runOnUiThread(() -> {
                try{
                    int progress = downloadInfo.getProgress();
                    if (progress > 0) {
                        progressBar = fragmentView.findViewWithTag(downloadInfo.getVideo().getVideoId()+"##progress");
                        progressBar.setProgressCompat(progress, true);
                    }
                }catch(Exception ignored){}
            });
        }

        public void onDownloadError(DownloadInfo info){
            downloadInfo = info;
            try{
                Video item = downloadInfo.getVideo();
                String url = item.getURL();
                String type = item.getDownloadedType();
                updateDownloadStatusOnResult(item, type, false);

                item = findVideo(url);
                if (type.equals("audio")) item.setDownloadingAudio(false);
                else if (type.equals("video")) item.setDownloadingVideo(false);
                homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(item));

                downloading = false;
                topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(false);
            }catch(Exception ignored){}
        }

        public void onDownloadEnd(DownloadInfo info) {
            downloadInfo = info;
            Video item = downloadInfo.getVideo();
            String url = item.getURL();
            String type = downloadInfo.getDownloadType();
            item = findVideo(url);
            try{
                updateDownloadingStatusOnResult(item, type, false);
                if (type.equals("audio")) item.setDownloadedAudio(1);
                else item.setDownloadedVideo(1);
                homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(findVideo(url)));
                updateDownloadStatusOnResult(item, type, true);
                downloading = false;
                topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(false);
            }catch(Exception ignored){}
        }

        @Override
        public void onDownloadCancel(DownloadInfo info){
            try {
                Video video = findVideo(info.getVideo().getURL());
                String type = info.getDownloadType();
                updateDownloadingStatusOnResult(video, type, false);

                if (info.getDownloadQueue().size() == 1){
                    downloading = false;
                    topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(false);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void onDownloadCancelAll(DownloadInfo info) {
            downloadInfo = info;
            try{
                while (!info.getDownloadQueue().isEmpty()){
                    Video item = downloadInfo.getDownloadQueue().pop();
                    String url = item.getURL();
                    String type = item.getDownloadedType();
                    item = findVideo(url);
                    updateDownloadingStatusOnResult(item, type, false);
                }

                downloading = false;
                topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(false);

            }catch(Exception e){
                e.printStackTrace();
            }
        }


        public void onDownloadServiceEnd() {
            mainActivity.stopDownloadService();
        }
    };

    public HomeFragment() {
        // Required empty public constructor
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

        downloadQueue = new ArrayList<>();
        resultObjects = new ArrayList<>();
        selectedObjects = new ArrayList<>();

        // Inflate the layout for this fragment
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false);
        context = fragmentView.getContext().getApplicationContext();
        fragmentContext = fragmentView.getContext();
        activity = getActivity();
        mainActivity = (MainActivity) activity;
        downloading = mainActivity.isDownloadServiceRunning();

        //initViews
        shimmerCards = fragmentView.findViewById(R.id.shimmer_results_framelayout);
        topAppBar = fragmentView.findViewById(R.id.home_toolbar);
        recyclerView = fragmentView.findViewById(R.id.recycler_view_home);
        searchSuggestions = fragmentView.findViewById(R.id.search_suggestions_scroll_view);
        searchSuggestionsLinearLayout = fragmentView.findViewById(R.id.search_suggestions_linear_layout);

        homeRecyclerViewAdapter = new HomeRecyclerViewAdapter(resultObjects, this, activity);
        recyclerView.setAdapter(homeRecyclerViewAdapter);

        initMenu();

        homeFabs = fragmentView.findViewById(R.id.home_fabs);
        downloadFabs = homeFabs.findViewById(R.id.download_selected_coordinator);
        downloadAllFab = homeFabs.findViewById(R.id.download_all_coordinator);

        FloatingActionButton music_fab = downloadFabs.findViewById(R.id.audio_fab);
        FloatingActionButton video_fab = downloadFabs.findViewById(R.id.video_fab);
        music_fab.setTag("SELECT##audio");
        video_fab.setTag("SELECT##video");

        music_fab.setOnClickListener(this);
        video_fab.setOnClickListener(this);


        ExtendedFloatingActionButton download_all_fab = downloadAllFab.findViewById(R.id.download_all_fab);
        download_all_fab.setTag("downloadAll");
        download_all_fab.setOnClickListener(this);

        if (inputQueries != null) {
            databaseManager = new DatabaseManager(context);
            databaseManager.clearResults();
            databaseManager.close();
            inputQueriesLength = inputQueries.size();
            homeRecyclerViewAdapter.clear();

            shimmerCards.startShimmer();
            shimmerCards.setVisibility(View.VISIBLE);
            Thread thread = new Thread(() -> {
                while(!inputQueries.isEmpty()){
                    inputQuery = inputQueries.pop();
                    parseQuery(false);
                }

                try{
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // DOWNLOAD ALL BUTTON
                        if ((resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1) || inputQueriesLength > 1) {
                            downloadAllFab.setVisibility(View.VISIBLE);
                        }
                        databaseManager = new DatabaseManager(context);
                        databaseManager.clearResults();
                        for (Video v : resultObjects) v.setIsPlaylistItem(1);
                        databaseManager.addToResults(resultObjects);
                    });
                }catch (Exception ignored){}
            });
            thread.start();
        } else {
            initCards();
        }

        return fragmentView;
    }


    private void initCards() {
        homeRecyclerViewAdapter.clear();
        Handler uiHandler = new Handler(Looper.getMainLooper());
        try {
            Thread thread = new Thread(() -> {
                databaseManager = new DatabaseManager(context);
                resultObjects = databaseManager.getResults();
                Log.e(TAG, resultObjects.toString());
                String playlistTitle = "";
                try {
                    playlistTitle = resultObjects.get(0).getPlaylistTitle();
                }catch(Exception ignored){}
                if (resultObjects.size() == 0 || (playlistTitle.equals(getString(R.string.trendingPlaylist)) && !downloading)) {
                    try {
                        databaseManager.clearResults();
                        uiHandler.post(() -> {
                            shimmerCards.startShimmer();
                            shimmerCards.setVisibility(View.VISIBLE);
                        });
                        infoUtil = new InfoUtil(context);
                        resultObjects = infoUtil.getTrending(context);
                        databaseManager.addToResults(resultObjects);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }else {
                    if (!downloading){
                        homeRecyclerViewAdapter.add(resultObjects);
                        for (int i = 0; i < resultObjects.size(); i++){
                            Video tmp = resultObjects.get(i);
                            if(tmp.isDownloading()){
                                updateDownloadingStatusOnResult(tmp, "audio", false);
                                updateDownloadingStatusOnResult(tmp, "video", false);
                            }
                        }
                    }
                }

                uiHandler.post(() -> {
                    homeRecyclerViewAdapter.add(resultObjects);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                });

                databaseManager.close();
                if (resultObjects != null) {
                    uiHandler.post(this::scrollToTop);
                    if (resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1) {
                        uiHandler.post(() -> downloadAllFab.setVisibility(View.VISIBLE));
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            uiHandler.post(() -> {
                shimmerCards.stopShimmer();
                shimmerCards.setVisibility(View.GONE);
            });
        }
    }

    private void initMenu() {
        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                homeFabs.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                searchSuggestions.setVisibility(View.VISIBLE);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                homeFabs.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
                searchSuggestions.setVisibility(View.GONE);
                return true;
            }
        };

        if(downloading){
            topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(true);
        }

        topAppBar.getMenu().findItem(R.id.search).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) topAppBar.getMenu().findItem(R.id.search).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        searchView.setQueryHint(getString(R.string.search_hint));

        databaseManager = new DatabaseManager(context);
        infoUtil = new InfoUtil(context);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                topAppBar.getMenu().findItem(R.id.search).collapseActionView();
                downloadAllFab.setVisibility(View.GONE);
                downloadFabs.setVisibility(View.GONE);
                selectedObjects = new ArrayList<>();
                inputQuery = query.trim();


                shimmerCards.startShimmer();
                shimmerCards.setVisibility(View.VISIBLE);
                homeRecyclerViewAdapter.clear();
                Thread thread = new Thread(() -> {
                    parseQuery(true);
                    // DOWNLOAD ALL BUTTON
                    if (resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1) {
                        new Handler(Looper.getMainLooper()).post(() -> downloadAllFab.setVisibility(View.VISIBLE));
                    }
                });
                thread.start();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchSuggestionsLinearLayout.removeAllViews();
                if (newText.isEmpty()) return false;
                Thread thread = new Thread(() -> {
                    ArrayList<String> suggestions = infoUtil.getSearchSuggestions(newText);
                    for (int i = 0; i < suggestions.size();i++){
                        View v = LayoutInflater.from(fragmentContext).inflate(R.layout.search_suggestion_item, null);
                        TextView textView = v.findViewById(R.id.suggestion_text);
                        textView.setText(suggestions.get(i));
                        new Handler(Looper.getMainLooper()).post(() ->  searchSuggestionsLinearLayout.addView(v));
                        textView.setOnClickListener(view -> onQueryTextSubmit(textView.getText().toString()));

                        MaterialButton mb = v.findViewById(R.id.set_search_query_button);
                        mb.setOnClickListener(view -> searchView.setQuery(textView.getText(), false));
                    }
                });
                thread.start();
                return false;
            }
        });

        topAppBar.setOnClickListener(view -> scrollToTop());

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            int itemId = m.getItemId();
            if(itemId == R.id.delete_results){
                databaseManager.clearResults();
                databaseManager.close();
                selectedObjects = new ArrayList<>();
                downloadAllFab.setVisibility(View.GONE);
                downloadFabs.setVisibility(View.GONE);
                initCards();
            }else if(itemId == R.id.cancel_download){
                try{
                    mainActivity.cancelDownloadService();
                    topAppBar.getMenu().findItem(itemId).setVisible(false);
                    for (int i = 0; i < downloadInfo.getDownloadQueue().size(); i++){
                        Video vid = downloadInfo.getDownloadQueue().get(i);
                        String type = vid.getDownloadedType();
                        updateDownloadingStatusOnResult(vid, type, false);
                    }
                    downloadQueue = new ArrayList<>();
                    downloading = false;
                }catch(Exception ignored){}
            }
            return true;
        });

    }


    public void handleIntent(Intent intent) {
        inputQueries = new LinkedList<>();
        inputQueries.add(intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    public void handleFileIntent(LinkedList<String> lines) {
        inputQueries = lines;
    }

    public void scrollToTop() {
        recyclerView.scrollToPosition(0);
        ((AppBarLayout) topAppBar.getParent()).setExpanded(true, true);
    }

    private void parseQuery(boolean resetResults) {
        databaseManager = new DatabaseManager(context);
        infoUtil = new InfoUtil(context);
        new Handler(Looper.getMainLooper()).post(this::scrollToTop);

        String type = "Search";
        Pattern p = Pattern.compile("^(https?)://(www.)?youtu(.be)?");
        Matcher m = p.matcher(inputQuery);

        if (m.find()) {
            type = "Video";
            if (inputQuery.contains("playlist?list=")) {
                type = "Playlist";
            }
        }else if(inputQuery.contains("http")){
            type = "Default";
        }

        Log.e(TAG, inputQuery + " " + type);

        try {
            switch (type) {
                case "Search": {
                    try {
                        if (resetResults) resultObjects.clear();
                        ArrayList<Video> res = infoUtil.search(inputQuery);
                        resultObjects.addAll(res);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (resetResults) databaseManager.clearResults();
                            databaseManager.addToResults(resultObjects);
                            homeRecyclerViewAdapter.add(res);
                            shimmerCards.stopShimmer();
                            shimmerCards.setVisibility(View.GONE);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            shimmerCards.stopShimmer();
                            shimmerCards.setVisibility(View.GONE);
                        });
                    }
                    break;
                }
                case "Video": {
                    String[] el = inputQuery.split("/");
                    inputQuery = el[el.length - 1];

                    if (inputQuery.contains("watch?v=")) {
                        inputQuery = inputQuery.substring(8);
                    }

                    if (inputQuery.contains("&list=")) {
                        el = inputQuery.split("&list=");
                        inputQuery = el[0];
                    }

                    try {
                        if (resetResults) resultObjects.clear();
                        Video v = infoUtil.getVideo(inputQuery);
                        ArrayList<Video> res = new ArrayList<>();
                        res.add(v);
                        resultObjects.add(v);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (resetResults) databaseManager.clearResults();
                            databaseManager.addToResults(resultObjects);
                            homeRecyclerViewAdapter.add(res);
                            shimmerCards.stopShimmer();
                            shimmerCards.setVisibility(View.GONE);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            shimmerCards.stopShimmer();
                            shimmerCards.setVisibility(View.GONE);
                        });
                    }
                    break;
                }
                case "Playlist": {
                    inputQuery = inputQuery.split("list=")[1];
                    String nextPageToken = "";
                    if (resetResults) databaseManager.clearResults();
                    if (resetResults){
                        resultObjects.clear();
                        homeRecyclerViewAdapter.clear();
                    }
                    do {
                        InfoUtil.PlaylistTuple tmp = infoUtil.getPlaylist(inputQuery, nextPageToken);
                        ArrayList<Video> tmp_vids = tmp.getVideos();
                        String tmp_token = tmp.getNextPageToken();
                        resultObjects.addAll(tmp_vids);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            databaseManager.addToResults(tmp_vids);
                            homeRecyclerViewAdapter.add(tmp_vids);
                            shimmerCards.stopShimmer();
                            shimmerCards.setVisibility(View.GONE);
                        });
                        if (tmp_token.isEmpty()) break;
                        if (tmp_token.equals(nextPageToken)) break;
                        nextPageToken = tmp_token;
                    }while (true);
                    break;
                }
                case "Default" : {
                    try {
                        if (resetResults) resultObjects.clear();
                        ArrayList<Video> video = infoUtil.getFromYTDL(inputQuery);
                        if (video != null) {
                            resultObjects.addAll(video);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (resetResults) databaseManager.clearResults();
                                homeRecyclerViewAdapter.add(video);
                                databaseManager.addToResults(resultObjects);
                            });
                        }

                        new Handler(Looper.getMainLooper()).post(() -> {
                            shimmerCards.stopShimmer();
                            shimmerCards.setVisibility(View.GONE);
                        });

                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        databaseManager.close();
    }


    public Video findVideo(String url) {
        for (int i = 0; i < resultObjects.size(); i++) {
            Video v = resultObjects.get(i);
            if ((v.getURL()).equals(url)) {
                return v;
            }
        }

        return null;
    }

    @SuppressLint("ResourceType")
    @Override
    public void onButtonClick(int position, String type) {
        Log.e(TAG, type);
        Video vid = resultObjects.get(position);
        vid.setDownloadedType(type);
        MaterialButton btn = recyclerView.findViewWithTag(vid.getVideoId()+"##"+type);
        if (downloading){
           try {
               if (btn.getTag(R.id.cancelDownload).equals("true")){
                   mainActivity.removeItemFromDownloadQueue(vid, type);
                   return;
               }
           }catch (Exception ignored){}
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("download_card", true)){
            selectedObjects.clear();
            selectedObjects.add(resultObjects.get(position));
            showConfigureDownloadCard(type);
        }else{
            downloadQueue.add(vid);
            updateDownloadingStatusOnResult(vid, type, true);
            if (isStoragePermissionGranted()){
                mainActivity.startDownloadService(downloadQueue, listener);
                downloadQueue.clear();
            }
        }
    }

    public boolean isStoragePermissionGranted() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }else{
            downloadQueue = new ArrayList<>();
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }

    public void updateDownloadStatusOnResult(Video v, String type, boolean downloaded) {
        if (v != null) {
            databaseManager = new DatabaseManager(context);
            try {
                databaseManager.updateDownloadStatusOnResult(v.getVideoId(), type, downloaded);
                databaseManager.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void updateDownloadingStatusOnResult(Video v, String type, boolean isDownloading) {
        if (v != null) {
            if (type.equals("audio")) v.setDownloadingAudio(isDownloading);
            else if (type.equals("video")) v.setDownloadingVideo(isDownloading);
            homeRecyclerViewAdapter.updateVideoListItem(v, resultObjects.indexOf(v));
            databaseManager = new DatabaseManager(context);
            try {
                databaseManager.updateDownloadingStatusOnResult(v.getVideoId(), type, isDownloading);
                databaseManager.close();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onCardClick(int position, boolean add) {
        Video video = resultObjects.get(position);
        if (add) selectedObjects.add(video); else selectedObjects.remove(video);
        if(selectedObjects.size() > 1){
            downloadAllFab.setVisibility(View.GONE);
            downloadFabs.setVisibility(View.VISIBLE);
        } else {
            downloadFabs.setVisibility(View.GONE);
            if (resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1) {
                downloadAllFab.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onClick(View v) {
        String viewIdName;
        try {
            viewIdName = v.getTag().toString();
        } catch (Exception e) {
            viewIdName = "";
        }

        if (!viewIdName.isEmpty()) {
            if (viewIdName.contains("audio") || viewIdName.contains("video")) {
                String[] buttonData = viewIdName.split("##");
                if (buttonData[0].equals("SELECT")) {
                    initSelectedDownload(buttonData[1]);
                }
            }
            if (viewIdName.equals("downloadAll")){
                //remove previously selected
                for (int i = 0; i < selectedObjects.size(); i++) {
                    Video vid = findVideo(selectedObjects.get(i).getURL());
                    homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(vid));
                }
                selectedObjects = new ArrayList<>();
                downloadFabs.setVisibility(View.GONE);

                bottomSheet = new BottomSheetDialog(fragmentContext);
                bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
                bottomSheet.setContentView(R.layout.home_download_all_bottom_sheet);

                TextInputLayout first = bottomSheet.findViewById(R.id.first_textinput);
                first.getEditText().setText(String.valueOf(1));

                TextInputLayout last = bottomSheet.findViewById(R.id.last_textinput);
                last.getEditText().setText(String.valueOf(resultObjects.size()));


                Button audio = bottomSheet.findViewById(R.id.bottomsheet_audio_button);
                audio.setOnClickListener(view -> {
                    int start = Integer.parseInt(first.getEditText().getText().toString());
                    int end = Integer.parseInt(last.getEditText().getText().toString());
                    initDownloadAll(bottomSheet, start, end, "audio");
                });

                Button video = bottomSheet.findViewById(R.id.bottomsheet_video_button);
                video.setOnClickListener(view -> {
                    int start = Integer.parseInt(first.getEditText().getText().toString());
                    int end = Integer.parseInt(last.getEditText().getText().toString());
                    initDownloadAll(bottomSheet, start, end, "video");
                });

                bottomSheet.show();
                bottomSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            }
        }
    }

    private void initDownloadAll(BottomSheetDialog bottomSheet, int start, int end, String type){
        if(start > end){
            TextInputLayout first = bottomSheet.findViewById(R.id.first_textinput);
            first.setError(getString(R.string.first_cant_be_larger_than_last));

            TextInputLayout last = bottomSheet.findViewById(R.id.last_textinput);
            last.setError(getString(R.string.last_cant_be_smaller_than_first));
            return;
        }
        bottomSheet.cancel();
        start--;
        end--;
        if (start <= 1) start = 0;

        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);

        if (sharedPreferences.getBoolean("download_card", true)){
            selectedObjects.clear();
            selectedObjects.addAll(resultObjects.subList(start, end + 1));
            showConfigureDownloadCard(type);
        }else{
            for (int i = start; i <= end; i++){
                Video vid = findVideo(resultObjects.get(i).getURL());
                vid.setDownloadedType(type);
                updateDownloadingStatusOnResult(vid, type, true);
                downloadQueue.add(vid);
            }

            if(isStoragePermissionGranted()){
                mainActivity.startDownloadService(downloadQueue, listener);
                downloadQueue.clear();
            }
        }
    }

    private void initSelectedDownload(String type){

        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (sharedPreferences.getBoolean("download_card", true)){
            showConfigureDownloadCard(type);
        }else{
            for (int i = 0; i < selectedObjects.size(); i++) {
                Video vid = findVideo(selectedObjects.get(i).getURL());
                vid.setDownloadedType(type);
                updateDownloadingStatusOnResult(vid, type, true);
                homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(vid));
                downloadQueue.add(vid);
            }
            selectedObjects = new ArrayList<>();
            homeRecyclerViewAdapter.clearCheckedVideos();
            downloadFabs.setVisibility(View.GONE);

            if(isStoragePermissionGranted()){
                mainActivity.startDownloadService(downloadQueue, listener);
                downloadQueue.clear();
            }
        }
    }

    private void showConfigureDownloadCard(String type){
        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        try{
            bottomSheet = new BottomSheetDialog(fragmentContext);
            bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);

            if (type.equals("audio")){
                bottomSheet.setContentView(R.layout.home_download_audio_bottom_sheet);

                TextInputLayout title = bottomSheet.findViewById(R.id.title_textinput);
                if (selectedObjects.size() > 1){
                    title.getEditText().setText(getString(R.string.mutliple_titles));
                    title.getEditText().setClickable(false);
                    title.getEditText().setLongClickable(false);
                }else{
                    title.getEditText().setText(selectedObjects.get(0).getTitle());
                    title.getEditText().addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                        @Override
                        public void afterTextChanged(Editable editable) {
                            int index = resultObjects.indexOf(selectedObjects.get(0));
                            resultObjects.get(index).setTitle(editable.toString());
                        }
                    });
                }

                TextInputLayout author = bottomSheet.findViewById(R.id.author_textinput);
                if (selectedObjects.size() > 1){
                    author.getEditText().setText(getString(R.string.mutliple_authors));
                    author.getEditText().setClickable(false);
                    author.getEditText().setLongClickable(false);
                }else{
                    author.getEditText().setText(selectedObjects.get(0).getAuthor());
                    author.getEditText().addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                        @Override
                        public void afterTextChanged(Editable editable) {
                            int index = resultObjects.indexOf(selectedObjects.get(0));
                            resultObjects.get(index).setAuthor(editable.toString());
                        }
                    });
                }


                String[] audioFormats = context.getResources().getStringArray(R.array.music_formats);
                TextInputLayout audioFormat = bottomSheet.findViewById(R.id.audio_format);
                AutoCompleteTextView autoCompleteTextView = bottomSheet.findViewById(R.id.audio_format_textview);
                String preference = sharedPreferences.getString("audio_format", "mp3");
                autoCompleteTextView.setText(preference, false);
                ((AutoCompleteTextView)audioFormat.getEditText()).setOnItemClickListener((adapterView, view, index, l) -> {
                    for (int i = 0; i < selectedObjects.size(); i++) {
                        Video vid = findVideo(selectedObjects.get(i).getURL());
                        vid.setAudioFormat(audioFormats[index]);
                    }
                    editor.putString("audio_format", audioFormats[index]);
                    editor.apply();
                });

            }else {
                bottomSheet.setContentView(R.layout.home_download_video_bottom_sheet);

                TextInputLayout title = bottomSheet.findViewById(R.id.title_textinput);
                if (selectedObjects.size() > 1){
                    title.getEditText().setText(getString(R.string.mutliple_titles));
                    title.getEditText().setClickable(false);
                    title.getEditText().setLongClickable(false);
                }else{
                    title.getEditText().setText(selectedObjects.get(0).getTitle());
                    title.getEditText().addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                        @Override
                        public void afterTextChanged(Editable editable) {
                            int index = resultObjects.indexOf(selectedObjects.get(0));
                            resultObjects.get(index).setTitle(editable.toString());
                        }
                    });
                }

                String[] videoFormats = context.getResources().getStringArray(R.array.video_formats);
                String[] videoQualities = context.getResources().getStringArray(R.array.video_quality);

                TextInputLayout videoFormat = bottomSheet.findViewById(R.id.video_format);
                AutoCompleteTextView autoCompleteTextView = bottomSheet.findViewById(R.id.video_format_textview);
                String preference = sharedPreferences.getString("video_format", "webm");
                autoCompleteTextView.setText(preference, false);
                ((AutoCompleteTextView)videoFormat.getEditText()).setOnItemClickListener((adapterView, view, index, l) -> {
                    for (int i = 0; i < selectedObjects.size(); i++) {
                        Video vid = findVideo(selectedObjects.get(i).getURL());
                        vid.setVideoFormat(videoFormats[index]);
                    }
                    editor.putString("video_format", videoFormats[index]);
                    editor.apply();
                });

                TextInputLayout videoQuality = bottomSheet.findViewById(R.id.video_quality);
                autoCompleteTextView = bottomSheet.findViewById(R.id.video_quality_textview);
                preference = sharedPreferences.getString("video_quality", "Best Quality");
                autoCompleteTextView.setText(preference, false);
                ((AutoCompleteTextView)videoQuality.getEditText()).setOnItemClickListener((adapterView, view, index, l) -> {
                    for (int i = 0; i < selectedObjects.size(); i++) {
                        Video vid = findVideo(selectedObjects.get(i).getURL());
                        vid.setVideoQuality(videoQualities[index]);
                    }
                    editor.putString("video_quality", videoQualities[index]);
                    editor.apply();
                });

                Chip embedSubs = bottomSheet.findViewById(R.id.embed_subtitles);
                embedSubs.setChecked(sharedPreferences.getBoolean("embed_subtitles", false));
                embedSubs.setOnClickListener(view -> {
                    if (embedSubs.isChecked()){
                        editor.putBoolean("embed_subtitles", true);
                    }else{
                        editor.putBoolean("embed_subtitles", false);
                    }
                    editor.apply();
                });

                Chip addChapters = bottomSheet.findViewById(R.id.add_chapters);
                addChapters.setChecked(sharedPreferences.getBoolean("add_chapters", false));
                addChapters.setOnClickListener(view -> {
                    if (addChapters.isChecked()){
                        editor.putBoolean("add_chapters", true);
                    }else{
                        editor.putBoolean("add_chapters", false);
                    }
                    editor.apply();
                });

                Chip saveThumbnail = bottomSheet.findViewById(R.id.save_thumbnail);
                saveThumbnail.setChecked(sharedPreferences.getBoolean("write_thumbnail", false));
                saveThumbnail.setOnClickListener(view -> {
                    if (saveThumbnail.isChecked()){
                        editor.putBoolean("write_thumbnail", true);
                    }else{
                        editor.putBoolean("write_thumbnail", false);
                    }
                    editor.apply();
                });
            }

            Button cancel = bottomSheet.findViewById(R.id.bottomsheet_cancel_button);
            cancel.setOnClickListener(view -> {
                bottomSheet.cancel();
            });

            Button download = bottomSheet.findViewById(R.id.bottomsheet_download_button);
            download.setOnClickListener(view -> {
                for (int i = 0; i < selectedObjects.size(); i++) {
                    Video vid = findVideo(selectedObjects.get(i).getURL());
                    vid.setDownloadedType(type);
                    updateDownloadingStatusOnResult(vid, type, true);
                    homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(vid));
                    downloadQueue.add(vid);
                }
                selectedObjects = new ArrayList<>();
                homeRecyclerViewAdapter.clearCheckedVideos();
                downloadFabs.setVisibility(View.GONE);

                if(isStoragePermissionGranted()){
                    mainActivity.startDownloadService(downloadQueue, listener);
                    downloadQueue.clear();
                }
                bottomSheet.cancel();
            });

            bottomSheet.show();
            bottomSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        }catch (Exception e){
            Toast.makeText(fragmentContext, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
