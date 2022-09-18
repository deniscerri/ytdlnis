package com.deniscerri.ytdl.page;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.deniscerri.ytdl.App;
import com.deniscerri.ytdl.BuildConfig;
import com.deniscerri.ytdl.MainActivity;
import com.deniscerri.ytdl.R;
import com.deniscerri.ytdl.adapter.HomeRecyclerViewAdapter;
import com.deniscerri.ytdl.api.YoutubeAPIManager;
import com.deniscerri.ytdl.database.DBManager;
import com.deniscerri.ytdl.database.Video;
import com.deniscerri.ytdl.page.settings.SettingsActivity;
import com.deniscerri.ytdl.util.NotificationUtil;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.yausername.youtubedl_android.DownloadProgressCallback;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class HomeFragment extends Fragment implements HomeRecyclerViewAdapter.OnItemClickListener, View.OnClickListener {
    private boolean downloading = false;
    private View fragmentView;
    private ProgressBar progressBar;
    private String inputQuery;
    private RecyclerView recyclerView;
    private HomeRecyclerViewAdapter homeRecyclerViewAdapter;
    private ShimmerFrameLayout shimmerCards;
    private CoordinatorLayout downloadFabs;
    private CoordinatorLayout downloadAllFab;
    private CoordinatorLayout homeFabs;
    private BottomSheetDialog bottomSheet;

    Context context;
    Activity activity;
    private MaterialToolbar topAppBar;

    private static final String TAG = "HomeFragment";

    private ArrayList<Video> resultObjects;
    private ArrayList<Video> selectedObjects;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private DBManager dbManager;
    private YoutubeAPIManager youtubeAPIManager;
    private Queue<Video> downloadQueue;

    MainActivity mainActivity;
    private final NotificationUtil notificationUtil = App.notificationUtil;

    private final DownloadProgressCallback callback = new DownloadProgressCallback() {
        @Override
        public void onProgressUpdate(float progress, long etaInSeconds, String line) {
            activity.runOnUiThread(() -> {
                progressBar.setProgress((int) progress);
                notificationUtil.updateDownloadNotification(NotificationUtil.DOWNLOAD_NOTIFICATION_ID,
                        line, (int) progress, downloadQueue.size(), downloadQueue.peek().getTitle());
            });
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

        compositeDisposable = new CompositeDisposable();
        downloadQueue = new LinkedList<>();
        resultObjects = new ArrayList<>();
        selectedObjects = new ArrayList<>();

        // Inflate the layout for this fragment
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false);
        context = fragmentView.getContext();
        activity = getActivity();
        mainActivity = (MainActivity) activity;


        //initViews
        shimmerCards = fragmentView.findViewById(R.id.shimmer_results_framelayout);
        topAppBar = fragmentView.findViewById(R.id.home_toolbar);
        recyclerView = fragmentView.findViewById(R.id.recycler_view_home);

        homeRecyclerViewAdapter = new HomeRecyclerViewAdapter(resultObjects, this, activity);
        recyclerView.setAdapter(homeRecyclerViewAdapter);
        recyclerView.setNestedScrollingEnabled(false);

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
        music_fab.setTag("SELECT##mp3");
        video_fab.setTag("SELECT##mp4");

        music_fab.setOnClickListener(this);
        video_fab.setOnClickListener(this);


        ExtendedFloatingActionButton download_all_fab = downloadAllFab.findViewById(R.id.download_all_fab);
        download_all_fab.setTag("downloadAll");
        download_all_fab.setOnClickListener(this);

        return fragmentView;
    }


    private void initCards() {
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        homeRecyclerViewAdapter.clear();
        try {
            Thread thread = new Thread(() -> {
                Handler uiHandler = new Handler(Looper.getMainLooper());
                dbManager = new DBManager(context);
                youtubeAPIManager = new YoutubeAPIManager(context);
                resultObjects = dbManager.getResults();

                if (resultObjects.size() == 0) {
                    try {
                        resultObjects = youtubeAPIManager.getTrending(context);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
                if (resultObjects != null) {
                    uiHandler.post(this::scrollToTop);
                    if (resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1) {
                        uiHandler.post(() -> downloadAllFab.setVisibility(View.VISIBLE));
                    }
                }

                uiHandler.post(() -> {
                    homeRecyclerViewAdapter.setVideoList(resultObjects, true);
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                });
            });
            thread.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void initMenu() {
        youtubeAPIManager = new YoutubeAPIManager(context);

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

        topAppBar.getMenu().findItem(R.id.search).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) topAppBar.getMenu().findItem(R.id.search).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        searchView.setQueryHint(getString(R.string.search_hint));

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
                recyclerView.removeAllViews();
                selectedObjects = new ArrayList<>();
                downloadAllFab.setVisibility(View.GONE);
                initCards();
            }else if(itemId == R.id.cancel_download){
                compositeDisposable.clear();
                mainActivity.stopDownloadService();
                topAppBar.getMenu().findItem(itemId).setVisible(false);
                downloadQueue = new LinkedList<>();
                downloading = false;

                String id = progressBar.getTag().toString().split("##progress")[0];
                String type = findVideo(id).getDownloadedType();
                MaterialButton theClickedButton = recyclerView.findViewWithTag(id + "##"+type);

                if (theClickedButton != null) {
                    if (type.equals("mp3")) {
                        theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_stopped));
                    } else {
                        theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_stopped));
                    }
                }
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
        Log.e(TAG, String.valueOf(homeRecyclerViewAdapter.getItemCount()));

        dbManager = new DBManager(context);
        youtubeAPIManager = new YoutubeAPIManager(context);
        scrollToTop();

        String type = "Search";
        Pattern p = Pattern.compile("^(https?)://(www.)?youtu(.be)?");
        Matcher m = p.matcher(inputQuery);

        if (m.find()) {
            type = "Video";
            if (inputQuery.contains("playlist?list=")) {
                type = "Playlist";
            }
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
                                resultObjects = youtubeAPIManager.search(query);
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                            dbManager.clearResults();
                            dbManager.addToResults(resultObjects);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                homeRecyclerViewAdapter.setVideoList(resultObjects, true);
                                //addEndofResultsText();
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
                                resultObjects.add(youtubeAPIManager.getVideo(query));
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }

                            dbManager.clearResults();
                            dbManager.addToResults(resultObjects);

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
                                homeRecyclerViewAdapter.setVideoList(new ArrayList<>(), true);
                                do {
                                    YoutubeAPIManager.PlaylistTuple tmp = youtubeAPIManager.getPlaylist(query, nextPageToken);
                                    ArrayList<Video> tmp_vids = tmp.getVideos();
                                    String tmp_token = tmp.getNextPageToken();
                                    resultObjects.addAll(tmp_vids);
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        homeRecyclerViewAdapter.setVideoList(tmp_vids, false);
                                        //addEndofResultsText();
                                        shimmerCards.stopShimmer();
                                        shimmerCards.setVisibility(View.GONE);
                                    });
                                    Thread.sleep(1000);
                                    dbManager.addToResults(tmp_vids);
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
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

    }


    public Video findVideo(String id) {
        for (int i = 0; i < resultObjects.size(); i++) {
            Video v = resultObjects.get(i);
            if ((v.getVideoId()).equals(id)) {
                return v;
            }
        }

        return null;
    }


    private void startDownload(Queue<Video> videos) {
        Video video;
        if(videos.size() == 0){
            mainActivity.stopDownloadService();
            return;
        }

        try {
            video = videos.peek();
        } catch (Exception e) {
            mainActivity.stopDownloadService();
            return;
        }

        String id = video.getVideoId();
        String url = "https://www.youtube.com/watch?v=" + id;
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        String type = video.getDownloadedType();
        File youtubeDLDir = getDownloadLocation(type);

        Log.e(TAG, youtubeDLDir.getAbsolutePath());

        MaterialButton clickedButton = null;

        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);


        boolean aria2 = sharedPreferences.getBoolean("aria2", false);
        if(aria2){
            request.addOption("--downloader", "libaria2c.so");
            request.addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"");
        }else{
            int concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1);
            request.addOption("-N", concurrentFragments);
        }



        String limitRate = sharedPreferences.getString("limit_rate", "");
        if(!limitRate.equals("")){
            request.addOption("-r", limitRate);
        }

        boolean writeThumbnail = sharedPreferences.getBoolean("write_thumbnail", false);
        if(writeThumbnail){
            request.addOption("--write-thumbnail");
        }

        if (type.equals("mp3")) {
            boolean removeNonMusic = sharedPreferences.getBoolean("remove_non_music", false);
            if(removeNonMusic){
                request.addOption("--sponsorblock-remove", "all");
            }
            request.addOption("--postprocessor-args", "-write_id3v1 1 -id3v2_version 3");
            request.addOption("--add-metadata");
            request.addOption("--no-mtime");
            request.addOption("-x");
            String format = sharedPreferences.getString("audio_format", "");
            request.addOption("--audio-format", format);

            if(format.equals("mp3") || format.equals("m4a") || format.equals("flac")){
                boolean embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false);
                if(embedThumb){
                    request.addOption("--embed-thumbnail");
                }
            }

            clickedButton = recyclerView.findViewWithTag(id + "##mp3");
        } else if (type.equals("mp4")) {
            boolean addChapters = sharedPreferences.getBoolean("add_chapters", false);
            if(addChapters){
                request.addOption("--sponsorblock-mark", "all");
            }
            boolean embedSubs = sharedPreferences.getBoolean("embed_subtitles", false);
            if(embedSubs){
                request.addOption("--embed-subs", "");
            }
            request.addOption("-f", "bestvideo+bestaudio/best");
            String format = sharedPreferences.getString("video_format", "");
            request.addOption("--merge-output-format", format);

            if(!format.equals("webm")){
                boolean embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false);
                if(embedThumb){
                    request.addOption("--embed-thumbnail");
                }
            }

            clickedButton = recyclerView.findViewWithTag(id + "##mp4");
        }

        request.addOption("-o", youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s");

        progressBar = fragmentView.findViewWithTag(id + "##progress");
        progressBar.setVisibility(View.VISIBLE);

        //scroll to Card
        recyclerView.scrollToPosition(resultObjects.indexOf(findVideo(id)));
        progressBar.setProgress(0);
        downloading = true;
        topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(true);

        Video theVideo = video;
        MaterialButton theClickedButton = clickedButton;

        if (theClickedButton != null) {
            if (type.equals("mp3")) {
                theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music));
            } else {
                theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video));
            }
        }

        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().execute(request, callback))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(youtubeDLResponse -> {
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);

                    if (theClickedButton != null) {
                        if (type.equals("mp3")) {
                            theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
                        } else {
                            theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded));
                        }
                    }


                    addToHistory(theVideo, new Date());
                    updateDownloadStatusOnResult(theVideo, type);
                    downloading = false;
                    topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(false);


                    // MEDIA SCAN
                    MediaScannerConnection.scanFile(context, new String[]{youtubeDLDir.getAbsolutePath()}, null, null);

                    // SCAN NEXT IN QUEUE
                    videos.remove();
                    startDownload(videos);
                }, e -> {
                    if (BuildConfig.DEBUG) Log.e(TAG, getString(R.string.failed_download), e);
                    Toast.makeText(context, R.string.failed_download, Toast.LENGTH_LONG).show();
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);
                    downloading = false;
                    topAppBar.getMenu().findItem(R.id.cancel_download).setVisible(false);

                    // SCAN NEXT IN QUEUE
                    videos.remove();
                    startDownload(videos);
                });
        compositeDisposable.add(disposable);
    }


    public void addToHistory(Video video, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        int year = cal.get(Calendar.YEAR);

        DateFormat formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time = formatter.format(date);
        String downloadedTime = day + " " + month + " " + year + " " + time;

        if (video != null) {
            dbManager = new DBManager(context);
            try {
                video.setDownloadedTime(downloadedTime);
                dbManager.addToHistory(video);
            } catch (Exception ignored) {
            }
        }
    }

    public void updateDownloadStatusOnResult(Video v, String type) {
        if (v != null) {
            dbManager = new DBManager(context);
            try {
                dbManager.updateDownloadStatusOnResult(v.getVideoId(), type);
            } catch (Exception ignored) {
            }
        }
    }


    @NonNull
    private File getDownloadLocation(String type) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        String downloadsDir;
        if (type.equals("mp3")) {
            downloadsDir = sharedPreferences.getString("music_path", getString(R.string.music_path));
        } else {
            downloadsDir = sharedPreferences.getString("video_path", getString(R.string.video_path));
        }

        File youtubeDLDir = new File(downloadsDir);
        if (!youtubeDLDir.exists()) {
            boolean isDirCreated = youtubeDLDir.mkdir();
            if (!isDirCreated) {
                Toast.makeText(context, R.string.failed_making_directory, Toast.LENGTH_LONG).show();
            }
        }
        return youtubeDLDir;
    }

    @Override
    public void onButtonClick(int position, String type) {
        Log.e(TAG, type);
        Video vid = resultObjects.get(position);
        vid.setDownloadedType(type);

        downloadQueue.add(vid);

        if (downloading) {
            Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_LONG).show();
            return;
        }
        if (isStoragePermissionGranted()){
            mainActivity.startDownloadService(vid.getTitle(), NotificationUtil.DOWNLOAD_NOTIFICATION_ID);
            startDownload(downloadQueue);
        }
    }

    public boolean isStoragePermissionGranted() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }else{
            downloadQueue = new LinkedList<>();
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
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
            if (viewIdName.contains("mp3") || viewIdName.contains("mp4")) {
                Log.e(TAG, viewIdName);
                String[] buttonData = viewIdName.split("##");
                if (buttonData[0].equals("SELECT")) {
                    for (int i = 0; i < selectedObjects.size(); i++) {
                        Video vid = findVideo(selectedObjects.get(i).getVideoId());
                        vid.setDownloadedType(buttonData[1]);
                        homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(vid));
                        downloadQueue.add(vid);
                    }
                    selectedObjects = new ArrayList<>();
                    downloadFabs.setVisibility(View.GONE);

                    if (downloading) {
                        Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if(isStoragePermissionGranted()){
                        mainActivity.startDownloadService(downloadQueue.peek().getTitle(), NotificationUtil.DOWNLOAD_NOTIFICATION_ID);
                        startDownload(downloadQueue);
                    }
                }
            }
            if (viewIdName.equals("downloadAll")){
                //remove previously selected
                for (int i = 0; i < selectedObjects.size(); i++) {
                    Video vid = findVideo(selectedObjects.get(i).getVideoId());
                    homeRecyclerViewAdapter.notifyItemChanged(resultObjects.indexOf(vid));
                }
                selectedObjects = new ArrayList<>();
                downloadFabs.setVisibility(View.GONE);

                bottomSheet = new BottomSheetDialog(context);
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
                    initDownloadAll(bottomSheet, start, end, "mp3");
                });

                Button video = bottomSheet.findViewById(R.id.bottomsheet_video_button);
                video.setOnClickListener(view -> {
                    int start = Integer.parseInt(first.getEditText().getText().toString());
                    int end = Integer.parseInt(last.getEditText().getText().toString());
                    initDownloadAll(bottomSheet, start, end, "mp4");
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
            Video vid = findVideo(resultObjects.get(i).getVideoId());
            vid.setDownloadedType(type);
            downloadQueue.add(vid);
        }

        if (downloading) {
            Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_LONG).show();
            return;
        }
        if(isStoragePermissionGranted()){
            mainActivity.startDownloadService(downloadQueue.peek().getTitle(), NotificationUtil.DOWNLOAD_NOTIFICATION_ID);
            startDownload(downloadQueue);
        }
    }
}