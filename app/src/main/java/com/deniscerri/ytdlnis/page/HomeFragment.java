package com.deniscerri.ytdlnis.page;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
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
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.deniscerri.ytdlnis.MainActivity;
import com.deniscerri.ytdlnis.R;
import com.deniscerri.ytdlnis.adapter.HomeRecyclerViewAdapter;
import com.deniscerri.ytdlnis.util.InfoUtil;
import com.deniscerri.ytdlnis.database.DBManager;
import com.deniscerri.ytdlnis.database.Video;
import com.deniscerri.ytdlnis.service.DownloadInfo;
import com.deniscerri.ytdlnis.service.IDownloaderListener;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HomeFragment extends Fragment implements HomeRecyclerViewAdapter.OnItemClickListener, View.OnClickListener {
    private boolean downloading = false;
    private View fragmentView;
    private LinearProgressIndicator progressBar;
    private String inputQuery;
    private RecyclerView recyclerView;
    private HomeRecyclerViewAdapter homeRecyclerViewAdapter;
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
    private DBManager dbManager;
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

        homeRecyclerViewAdapter = new HomeRecyclerViewAdapter(resultObjects, this, activity);
        recyclerView.setAdapter(homeRecyclerViewAdapter);

        initMenu();

        if (inputQuery != null) {
            parseQuery();
            inputQuery = null;
        } else {
            initCards();
        }

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

        return fragmentView;
    }


    private void initCards() {
        homeRecyclerViewAdapter.clear();
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        Handler uiHandler = new Handler(Looper.getMainLooper());
        try {
            Thread thread = new Thread(() -> {
                dbManager = new DBManager(context);
                resultObjects = dbManager.getResults();
                String playlistTitle = "";
                try {
                    playlistTitle = resultObjects.get(0).getPlaylistTitle();
                }catch(Exception ignored){}
                if (resultObjects.size() == 0 || (playlistTitle.equals(getString(R.string.trendingPlaylist)) && !downloading)) {
                    try {
                        infoUtil = new InfoUtil(context);
                        resultObjects = infoUtil.getTrending(context);
                        dbManager.addToResults(resultObjects);

                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }else {
                    if (!downloading){
                        homeRecyclerViewAdapter.setVideoList(resultObjects, true);
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
                    homeRecyclerViewAdapter.setVideoList(resultObjects, true);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                });

                dbManager.close();
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
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                homeFabs.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
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

        dbManager = new DBManager(context);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                topAppBar.getMenu().findItem(R.id.search).collapseActionView();
                downloadAllFab.setVisibility(View.GONE);
                downloadFabs.setVisibility(View.GONE);
                selectedObjects = new ArrayList<>();
                inputQuery = query.trim();
                parseQuery();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) { return false; }
        });

        topAppBar.setOnClickListener(view -> scrollToTop());

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            int itemId = m.getItemId();
            if(itemId == R.id.delete_results){
                dbManager.clearResults();
                dbManager.close();
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
        inputQuery = intent.getStringExtra(Intent.EXTRA_TEXT);
    }

    public void scrollToTop() {
        recyclerView.scrollToPosition(0);
        new Handler(Looper.getMainLooper()).post(() -> ((AppBarLayout) topAppBar.getParent()).setExpanded(true, true));
    }

    private void parseQuery() {
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        homeRecyclerViewAdapter.clear();
        infoUtil = new InfoUtil(context);
        dbManager = new DBManager(context);
        scrollToTop();

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
                    Thread thread = new Thread(new Runnable() {
                        private final String query;

                        {
                            this.query = inputQuery;
                        }

                        @Override
                        public void run() {
                            try {
                                resultObjects = infoUtil.search(query);
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                            dbManager.clearResults();
                            dbManager.addToResults(resultObjects);
                            dbManager.close();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                homeRecyclerViewAdapter.setVideoList(resultObjects, true);
                                shimmerCards.stopShimmer();
                                shimmerCards.setVisibility(View.GONE);
                            });
                        }
                    });
                    thread.start();
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

                    Thread thread = new Thread(new Runnable() {
                        private final String query;

                        {
                            this.query = inputQuery;
                        }

                        @Override
                        public void run() {
                            try {
                                resultObjects.clear();
                                resultObjects.add(infoUtil.getVideo(query));
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }

                            dbManager.clearResults();
                            dbManager.addToResults(resultObjects);
                            dbManager.close();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                homeRecyclerViewAdapter.setVideoList(resultObjects, true);
                                shimmerCards.stopShimmer();
                                shimmerCards.setVisibility(View.GONE);
                            });
                        }
                    });
                    thread.start();
                    break;
                }
                case "Playlist": {
                    inputQuery = inputQuery.split("list=")[1];
                    Thread thread = new Thread(new Runnable() {
                        private final String query;

                        {
                            this.query = inputQuery;
                        }

                        @Override
                        public void run() {
                            try {
                                String nextPageToken = "";
                                dbManager.clearResults();
                                dbManager.close();
                                resultObjects.clear();
                                homeRecyclerViewAdapter.setVideoList(new ArrayList<>(), true);
                                do {
                                    InfoUtil.PlaylistTuple tmp = infoUtil.getPlaylist(query, nextPageToken);
                                    ArrayList<Video> tmp_vids = tmp.getVideos();
                                    String tmp_token = tmp.getNextPageToken();
                                    resultObjects.addAll(tmp_vids);
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        homeRecyclerViewAdapter.setVideoList(tmp_vids, false);
                                        shimmerCards.stopShimmer();
                                        shimmerCards.setVisibility(View.GONE);
                                    });
                                    dbManager.addToResults(tmp_vids);
                                    dbManager.close();
                                    if (tmp_token.isEmpty()) break;
                                    if (tmp_token.equals(nextPageToken)) break;
                                    nextPageToken = tmp_token;
                                }while (true);

                                // DOWNLOAD ALL BUTTON
                                if (resultObjects.size() > 1) {
                                    new Handler(Looper.getMainLooper()).post(() -> downloadAllFab.setVisibility(View.VISIBLE));
                                }

                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    });
                    thread.start();
                    break;
                }
                case "Default" : {
                    Thread thread = new Thread(new Runnable() {
                        private final String query;

                        {
                            this.query = inputQuery;
                        }

                        @Override
                        public void run() {
                            try {
                                resultObjects.clear();
                                ArrayList<Video> video = infoUtil.getFromYTDL(query);
                                if (video != null) {
                                    resultObjects.addAll(video);
                                    dbManager.clearResults();
                                    dbManager.addToResults(resultObjects);
                                    dbManager.close();
                                }

                                new Handler(Looper.getMainLooper()).post(() -> {
                                    homeRecyclerViewAdapter.setVideoList(resultObjects, true);
                                    shimmerCards.stopShimmer();
                                    shimmerCards.setVisibility(View.GONE);
                                });

                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    });
                    thread.start();
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

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
        downloadQueue.add(vid);
        updateDownloadingStatusOnResult(vid, type, true);
        if (isStoragePermissionGranted()){
            mainActivity.startDownloadService(downloadQueue, listener);
            downloadQueue.clear();
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
            dbManager = new DBManager(context);
            try {
                dbManager.updateDownloadStatusOnResult(v.getVideoId(), type, downloaded);
                dbManager.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void updateDownloadingStatusOnResult(Video v, String type, boolean isDownloading) {
        if (v != null) {
            if (type.equals("audio")) v.setDownloadingAudio(isDownloading);
            else if (type.equals("video")) v.setDownloadingVideo(isDownloading);
            homeRecyclerViewAdapter.updateVideoListItem(v, resultObjects.indexOf(v));
            dbManager = new DBManager(context);
            try {
                dbManager.updateDownloadingStatusOnResult(v.getVideoId(), type, isDownloading);
                dbManager.close();
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
                    for (int i = 0; i < selectedObjects.size(); i++) {
                        Video vid = findVideo(selectedObjects.get(i).getURL());
                        vid.setDownloadedType(buttonData[1]);
                        updateDownloadingStatusOnResult(vid, buttonData[1], true);
                        homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(vid));
                        downloadQueue.add(vid);
                    }
                    selectedObjects = new ArrayList<>();
                    homeRecyclerViewAdapter.clearCheckedVideos();
                    downloadFabs.setVisibility(View.GONE);

                    if (downloading) {
                        Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if(isStoragePermissionGranted()){
                        mainActivity.startDownloadService(downloadQueue, listener);
                        downloadQueue.clear();
                    }
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
        if (start <= 1) start = 0;

        for (int i = start; i < end; i++){
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
