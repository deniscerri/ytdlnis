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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
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
import com.deniscerri.ytdl.util.NotificationUtil;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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
    private LinearLayout homeLinearLayout;
    private RecyclerView recyclerView;
    private HomeRecyclerViewAdapter homeRecyclerViewAdapter;
    private NestedScrollView scrollView;
    private ShimmerFrameLayout shimmerCards;
    private LayoutInflater layoutinflater;

    Context context;
    Activity activity;
    private MaterialToolbar topAppBar;

    private static final String TAG = "HomeFragment";

    private ArrayList<Video> resultObjects;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private DBManager dbManager;
    private YoutubeAPIManager youtubeAPIManager;
    private Queue<Video> downloadQueue;

    MainActivity mainActivity;
    private static final NotificationUtil notificationUtil = App.notificationUtil;

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

        // Inflate the layout for this fragment
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false);
        context = fragmentView.getContext();
        activity = getActivity();
        mainActivity = (MainActivity) activity;
        layoutinflater = LayoutInflater.from(context);


        //initViews
        homeLinearLayout = fragmentView.findViewById(R.id.linearLayout1);
        shimmerCards = fragmentView.findViewById(R.id.shimmer_results_framelayout);
        topAppBar = fragmentView.findViewById(R.id.home_toolbar);
        scrollView = fragmentView.findViewById(R.id.home_scrollview);
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
        return fragmentView;
    }


    private void initCards() {
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        homeRecyclerViewAdapter.clear();
        homeLinearLayout.removeAllViews();
        try {
            Thread thread = new Thread(() -> {
                Handler uiHandler = new Handler(Looper.getMainLooper());
                dbManager = new DBManager(context);
                youtubeAPIManager = new YoutubeAPIManager(context);
                resultObjects = dbManager.getResults();
                boolean trending = false;

                if (resultObjects.size() == 0) {
                    trending = true;
                    try {
                        resultObjects = youtubeAPIManager.getTrending(context);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
                if (resultObjects != null) {
                    scrollToTop();

                    if (trending) {
                        uiHandler.post(this::addTrendingText);

                    } else {
                        if (resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1) {
                            createDownloadAllCard();
                        }
                    }
                }

                uiHandler.post(() -> {
                    homeRecyclerViewAdapter.setVideoList(resultObjects);
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
        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                scrollView.setVisibility(View.GONE);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                scrollView.setVisibility(View.VISIBLE);
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
                inputQuery = query.trim();
                parseQuery();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        topAppBar.setOnClickListener(view -> scrollToTop());

        topAppBar.setOnMenuItemClickListener((MenuItem m) -> {
            switch (m.getItemId()){
                case R.id.delete_results:
                    dbManager.clearResults();
                    recyclerView.removeAllViews();
                    initCards();
                    return true;
                case R.id.refresh_results:
                    recyclerView.removeAllViews();
                    initCards();
            }
            return true;
        });

    }

    public void handleIntent(Intent intent) {
        inputQuery = intent.getStringExtra(Intent.EXTRA_TEXT);
    }

    public void scrollToTop() {
        scrollView.smoothScrollTo(-100,-100);
        new Handler(Looper.getMainLooper()).post(() -> ((AppBarLayout) topAppBar.getParent()).setExpanded(true, true));
    }

    private void parseQuery() {
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        homeRecyclerViewAdapter.clear();
        homeLinearLayout.removeAllViews();

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

                            dbManager.addToResults(resultObjects);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                homeRecyclerViewAdapter.setVideoList(resultObjects);
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


                            dbManager.addToResults(resultObjects);

                            new Handler(Looper.getMainLooper()).post(() -> {
                                homeRecyclerViewAdapter.setVideoList(resultObjects);
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
                                resultObjects = youtubeAPIManager.getPlaylist(query, "");
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                            dbManager.addToResults(resultObjects);
                            // DOWNLOAD ALL BUTTON
                            if (resultObjects.size() > 1) {
                                createDownloadAllCard();
                            }

                            new Handler(Looper.getMainLooper()).post(() -> {
                                homeRecyclerViewAdapter.setVideoList(resultObjects);
                                //addEndofResultsText();
                                shimmerCards.stopShimmer();
                                shimmerCards.setVisibility(View.GONE);
                            });
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

    private void addTrendingText(){
        TextView trendingText = new TextView(context);
        trendingText.setText(R.string.Trending);
        trendingText.setPadding(70, 0 , 0, 0);
        homeLinearLayout.addView(trendingText);
    }

    private void createDownloadAllCard() {
        RelativeLayout r = new RelativeLayout(context);
        layoutinflater.inflate(R.layout.download_all_card, r);

        CardView card = r.findViewById(R.id.downloadall_card_view);

        LinearLayout buttonLayout = card.findViewById(R.id.downloadall_button_layout);
        Button musicBtn = buttonLayout.findViewById(R.id.downloadall_music);
        musicBtn.setTag("ALL##mp3");

        Button videoBtn = buttonLayout.findViewById(R.id.downloadall_video);
        videoBtn.setTag("ALL##mp4");

        musicBtn.setOnClickListener(this);
        videoBtn.setOnClickListener(this);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> homeLinearLayout.addView(r));
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
            video = videos.remove();
        } catch (Exception e) {
            return;
        }

        if (!isStoragePermissionGranted()) {
            Toast.makeText(context, R.string.try_again_after_permission, Toast.LENGTH_LONG).show();
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

        if (type.equals("mp3")) {
            request.addOption("--embed-thumbnail");
            request.addOption("--sponsorblock-remove", "all");
            request.addOption("--postprocessor-args", "-write_id3v1 1 -id3v2_version 3");
            request.addOption("--add-metadata");
            request.addOption("--no-mtime");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");

            clickedButton = recyclerView.findViewWithTag(id + "##mp3");
        } else if (type.equals("mp4")) {
            request.addOption("--embed-thumbnail");
            request.addOption("--sponsorblock-mark", "all");
            request.addOption("--embed-subs", "");
            request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");

            clickedButton = recyclerView.findViewWithTag(id + "##mp4");
        }
        request.addOption("-o", youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s");

        progressBar = fragmentView.findViewWithTag(id + "##progress");
        progressBar.setVisibility(View.VISIBLE);

        //scroll to Card
        View view = fragmentView.findViewWithTag(id + "##progress");
        scrollView.requestChildFocus(view, view);
        progressBar.setProgress(0);
        downloading = true;

        Video theVideo = video;
        MaterialButton theClickedButton = clickedButton;
        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().execute(request, callback))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(youtubeDLResponse -> {
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);

                    //TODO show download finished

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


                    // MEDIA SCAN
                    MediaScannerConnection.scanFile(context, new String[]{youtubeDLDir.getAbsolutePath()}, null, null);

                    // SCAN NEXT IN QUEUE
                    startDownload(videos);
                }, e -> {
                    if (BuildConfig.DEBUG) Log.e(TAG, getString(R.string.failed_download), e);
                    Toast.makeText(context, R.string.failed_download, Toast.LENGTH_LONG).show();
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);

                    //TODO notification failed
                    downloading = false;

                    // SCAN NEXT IN QUEUE
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
            downloadsDir = sharedPreferences.getString("music_path", "");
        } else {
            downloadsDir = sharedPreferences.getString("video_path", "");
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

    public boolean isStoragePermissionGranted() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
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
        mainActivity.startDownloadService(vid.getTitle());
        startDownload(downloadQueue);
    }

    @Override
    public void onCardClick(CardView card) {

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
                if (buttonData[0].equals("ALL")) {
                    for (int i = 0; i < resultObjects.size(); i++) {
                        Video vid = findVideo(resultObjects.get(i).getVideoId());
                        vid.setDownloadedType(buttonData[1]);
                        downloadQueue.add(vid);
                    }

                    if (downloading) {
                        Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_LONG).show();
                        return;
                    }
                    mainActivity.startDownloadService(downloadQueue.peek().getTitle());
                    startDownload(downloadQueue);
                }
            }
        }
    }
}