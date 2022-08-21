package com.deniscerri.ytdl;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.deniscerri.ytdl.api.YoutubeAPIManager;
import com.deniscerri.ytdl.database.DBManager;
import com.deniscerri.ytdl.database.Video;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;
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


public class HomeFragment extends Fragment implements View.OnClickListener{
    private boolean downloading = false;
    private View fragmentView;
    private ProgressBar progressBar;
    private String inputQuery;
    private LinearLayout linearLayout;
    private ScrollView scrollView;
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
    private NotificationCompat.Builder download_notification;
    private NotificationManagerCompat notificationManager;
    private Queue<Video> downloadQueue;

    private final DownloadProgressCallback callback = new DownloadProgressCallback() {
        @Override
        public void onProgressUpdate(float progress, long etaInSeconds, String line) {
            activity.runOnUiThread(() -> {
                progressBar.setProgress((int) progress);

                String contentText = "";
                if(progressBar.getProgress() > 0){
                    contentText += progressBar.getProgress() + "%";
                }

                String eta = convertETASecondsToTime(etaInSeconds);
                if(!eta.equals("0sec")){
                    contentText = contentText+ "  Estimated Time: " + eta;
                }

                if(downloadQueue.size() > 0){
                    contentText = contentText + "\n" + downloadQueue.size() + " items left";
                }


                download_notification.setProgress(100,(int) progress,false)
                                .setContentText(contentText);
                notificationManager.notify(Integer.parseInt(App.DOWNLOAD_CHANNEL_ID), download_notification.build());

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

    public String convertETASecondsToTime(long eta){
        String time = "";
        int s = (int) eta;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        s %= 60;
        if(h > 0){
            time+=h+"hr ";
        }else if(m > 0){
            if(m < 10) time +="0";
            time+=m+"min";
        }
        if(s < 10 && s > 0 && m > 0) time +="0";
        if(s < 0) s = 0;
        time+=s+"sec";

        return time;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        compositeDisposable = new CompositeDisposable();

        // Inflate the layout for this fragment
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false);
        context = fragmentView.getContext();
        activity = getActivity();
        layoutinflater = LayoutInflater.from(context);

        //initViews
        linearLayout = fragmentView.findViewById(R.id.linearLayout1);
        scrollView = fragmentView.findViewById(R.id.scrollView1);
        shimmerCards = fragmentView.findViewById(R.id.shimmer_results_framelayout);
        topAppBar = fragmentView.findViewById(R.id.home_toolbar);

        SwipeRefreshLayout swipeRefreshLayout = fragmentView.findViewById(R.id.swiperefreshhome);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            initCards();
            swipeRefreshLayout.setRefreshing(false);
        });

        FloatingActionButton fab = fragmentView.findViewById(R.id.fab_home);
        fab.setOnClickListener(this);

        scrollView.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if( y > 500){
                fab.show();
            }else{
                fab.hide();
            }
        });

        initMenu();

        if(inputQuery != null){
            parseQuery();
            inputQuery = null;
        }else{
            initCards();
        }

        return fragmentView;
    }



    private void initCards(){
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        linearLayout.removeAllViews();
        try{
            Thread thread = new Thread(() -> {
                Handler uiHandler = new Handler(Looper.getMainLooper());
                dbManager = new DBManager(context);
                youtubeAPIManager = new YoutubeAPIManager(context);
                resultObjects = dbManager.getResults();
                boolean trending = false;

                if(resultObjects.size() == 0){
                    trending = true;
                    try {
                        resultObjects = youtubeAPIManager.getTrending(context);
                    }catch(Exception e){
                        Log.e(TAG, e.toString());
                    }
                }

                TextView trendingText = new TextView(context);

                if(resultObjects != null){
                    scrollToTop();

                    if(trending){
                        trendingText.setText(R.string.Trending);
                        trendingText.setPadding(30, 0 ,0 ,0);
                        uiHandler.post(() -> linearLayout.addView(trendingText));

                    }else{
                        if(resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1){
                            createDownloadAllCard();
                        }
                    }

                    for(int i = 0; i < resultObjects.size(); i++){
                        createCard(resultObjects.get(i));
                    }
                }

                uiHandler.post(() -> {
                    addEndofResultsText();
                    shimmerCards.stopShimmer();
                    shimmerCards.setVisibility(View.GONE);
                });
            });
            thread.start();
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }
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
            if (m.getItemId() == R.id.delete_results) {
                dbManager.clearResults();
                linearLayout.removeAllViews();
                initCards();
                return true;
            }
            return true;
        });

    }

    public void handleIntent(Intent intent){
        inputQuery = intent.getStringExtra(Intent.EXTRA_TEXT);
    }

    public void scrollToTop(){
        scrollView.smoothScrollTo(0,0);
    }

    private void parseQuery() {
        shimmerCards.startShimmer();
        shimmerCards.setVisibility(View.VISIBLE);
        linearLayout.removeAllViews();

        resultObjects = new ArrayList<>();
        dbManager = new DBManager(context);
        youtubeAPIManager = new YoutubeAPIManager(context);
        scrollToTop();

        String type = "Search";
        Pattern p = Pattern.compile("^(https?)://(www.)?youtu(.be)?");
        Matcher m = p.matcher(inputQuery);

        if(m.find()){
            type = "Video";
            if (inputQuery.contains("list=")) {
                type = "Playlist";
            }
        }

        Log.e(TAG, inputQuery + " "+ type);

        try {
            switch (type) {
                case "Search": {
                    Thread thread = new Thread(new Runnable(){
                        private final String query;
                        {
                            this.query = inputQuery;
                        }
                        @Override
                        public void run(){
                            try {
                                resultObjects = youtubeAPIManager.search(query);
                            }catch(Exception e){
                                Log.e(TAG, e.toString());
                            }
                            Log.e(TAG, resultObjects.toString());

                            for(int i = 0; i < resultObjects.size(); i++){
                                createCard(resultObjects.get(i));
                            }
                            dbManager.addToResults(resultObjects);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                addEndofResultsText();
                                shimmerCards.stopShimmer();
                                shimmerCards.setVisibility(View.GONE);
                            });
                        }
                    });
                    thread.start();
                    break;
                }case "Video": {
                    String[] el = inputQuery.split("/");
                    inputQuery = el[el.length -1];

                    if(inputQuery.contains("watch?v=")){
                        inputQuery = inputQuery.substring(8);
                    }

                    Thread thread = new Thread(new Runnable(){
                        private final String query;
                        {
                            this.query = inputQuery;
                        }
                        @Override
                        public void run(){
                            try {
                                resultObjects.add(youtubeAPIManager.getVideo(query));
                            }catch(Exception e){
                                Log.e(TAG, e.toString());
                            }

                            dbManager.addToResults(resultObjects);
                            createCard(resultObjects.get(0));

                            new Handler(Looper.getMainLooper()).post(() -> {
                                shimmerCards.stopShimmer();
                                shimmerCards.setVisibility(View.GONE);
                            });
                        }
                    });
                    thread.start();
                    break;
                }case "Playlist": {
                    inputQuery = inputQuery.split("list=")[1];
                    Thread thread = new Thread(new Runnable() {
                        private final String query;
                        {
                            this.query = inputQuery;
                        }

                        @Override
                        public void run(){
                            try {
                                resultObjects = youtubeAPIManager.getPlaylist(query, "");
                            }catch(Exception e){
                                Log.e(TAG, e.toString());
                            }
                            dbManager.addToResults(resultObjects);
                            // DOWNLOAD ALL BUTTON
                            if(resultObjects.size() > 1){
                                createDownloadAllCard();
                            }
                            for(int i  = 0 ; i < resultObjects.size(); i++){
                                createCard(resultObjects.get(i));
                            }
                            new Handler(Looper.getMainLooper()).post(() -> {
                                addEndofResultsText();
                                shimmerCards.stopShimmer();
                                shimmerCards.setVisibility(View.GONE);
                            });
                        }
                    });
                    thread.start();
                    break;
                }
            }
        }catch(Exception e){
           Log.e(TAG, e.toString());
        }


    }

    private void addEndofResultsText(){
        TextView padding = new TextView(context);
        int dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, fragmentView.getResources().getDisplayMetrics());
        padding.setHeight(dp);
        padding.setText(R.string.end_of_results);
        padding.setGravity(Gravity.CENTER);
        linearLayout.addView(padding);
    }

    private void createDownloadAllCard(){
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
        uiHandler.post(() -> linearLayout.addView(r));
    }

    private void createCard(Video video){
        RelativeLayout r = new RelativeLayout(context);
        layoutinflater.inflate(R.layout.result_card, r);

        CardView card = r.findViewById(R.id.result_card_view);
        // THUMBNAIL ----------------------------------
        ImageView thumbnail = card.findViewById(R.id.result_image_view);
        String imageURL= video.getThumb();

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> Picasso.get().load(imageURL).into(thumbnail));
        thumbnail.setColorFilter(Color.argb(70, 0, 0, 0));

        // TITLE  ----------------------------------
        TextView videoTitle = card.findViewById(R.id.result_title);
        String title = video.getTitle();

        if(title.length() > 100){
            title = title.substring(0, 40) + "...";
        }
        videoTitle.setText(title);

        // Bottom Info ----------------------------------

        TextView bottomInfo = card.findViewById(R.id.result_info_bottom);
        String info = video.getAuthor() + " • " + video.getDuration();
        bottomInfo.setText(info);

        // BUTTONS ----------------------------------
        String videoID = video.getVideoId();

        LinearLayout buttonLayout = card.findViewById(R.id.download_button_layout);

        MaterialButton musicBtn = buttonLayout.findViewById(R.id.download_music);
        musicBtn.setTag(videoID + "##mp3");

        MaterialButton videoBtn = buttonLayout.findViewById(R.id.download_video);
        videoBtn.setTag(videoID + "##mp4");

        musicBtn.setOnClickListener(this);
        videoBtn.setOnClickListener(this);

        // PROGRESS BAR ----------------------------------------------------

        ProgressBar progressBar = card.findViewById(R.id.download_progress);
        progressBar.setVisibility(View.GONE);
        progressBar.setTag(videoID + "##progress");

        if(video.isAudioDownloaded() == 1){
            musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
        }
        if(video.isVideoDownloaded() == 1){
            videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded));
        }

        card.setTag(videoID + "##card");
        uiHandler.post(() -> linearLayout.addView(r));
    }


    @Override
    public void onClick(View v) {
        //do what you want to do when button is clicked
        String viewIdName;
        try{
            viewIdName = v.getTag().toString();
        }catch(Exception e){
            viewIdName = "";
        }

        if(!viewIdName.isEmpty()){
            if(viewIdName.contains("mp3") || viewIdName.contains("mp4")){
                Log.e(TAG, viewIdName);
                String[] buttonData = viewIdName.split("##");
                downloadQueue = new LinkedList<>();
                if(buttonData[0].equals("ALL")){
                    for (int i = 0; i < resultObjects.size(); i++){
                        Video vid = findVideo(resultObjects.get(i).getVideoId());
                        vid.setDownloadedType(buttonData[1]);
                        downloadQueue.add(vid);
                    }

                }else{
                    Video vid = findVideo(buttonData[0]);
                    vid.setDownloadedType(buttonData[1]);
                    downloadQueue.add(vid);
                }

                startDownload(downloadQueue);

            }
        }else{
            if (v.getId() == R.id.fab_home) {
                scrollView.smoothScrollBy(0, 0);
                scrollToTop();
            }
        }
    }

    public Video findVideo(String id){
        for(int i = 0; i < resultObjects.size(); i++){
            Video v = resultObjects.get(i);
            if((v.getVideoId()).equals(id)){
                return v;
            }
        }

        return null;
    }




    private void startDownload(Queue<Video> videos) {
        Video video;
        try{
            video = videos.remove();
        }catch(Exception e){
            return;
        }

        if (downloading) {
            Toast.makeText(context, R.string.download_already_started, Toast.LENGTH_LONG).show();
            return;
        }

        if (!isStoragePermissionGranted()) {
            Toast.makeText(context, R.string.try_again_after_permission, Toast.LENGTH_LONG).show();
            return;
        }
        String id = video.getVideoId();
        String url = "https://www.youtube.com/watch?v=" + id;
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        String type = video.getDownloadedType();
        File youtubeDLDir = getDownloadLocation(type);

        Log.e(TAG, youtubeDLDir.getAbsolutePath());

        MaterialButton clickedButton = null;

        if(type.equals("mp3")){
            request.addOption("--embed-thumbnail");
            request.addOption("--postprocessor-args", "-write_id3v1 1 -id3v2_version 3");
            request.addOption("--add-metadata");
            request.addOption("--no-mtime");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");

            clickedButton = linearLayout.findViewWithTag(id+"##mp3");
        }else if(type.equals("mp4")){
            request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");

            clickedButton = linearLayout.findViewWithTag(id+"##mp4");
        }
        request.addOption("-o", youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s");

        progressBar = fragmentView.findViewWithTag(id+"##progress");
        progressBar.setVisibility(View.VISIBLE);

        //scroll to Card
        View view = fragmentView.findViewWithTag(id+"##progress");
        view.getParent().requestChildFocus(view,view);

        showStart(video);
        downloading = true;

        Video theVideo = video;
        MaterialButton theClickedButton = clickedButton;
        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().execute(request, callback))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(youtubeDLResponse -> {
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);

                    download_notification.setContentText(getString(R.string.download_success))
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_download_done))
                            .setProgress(0,0,false);
                    notificationManager.notify(Integer.parseInt(App.DOWNLOAD_CHANNEL_ID), download_notification.build());

                    if(theClickedButton != null){
                        if(type.equals("mp3")){
                            theClickedButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
                        }else{
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
                    if(BuildConfig.DEBUG) Log.e(TAG,  getString(R.string.failed_download), e);
                    Toast.makeText(context, R.string.failed_download, Toast.LENGTH_LONG).show();
                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);

                    download_notification.setContentText(getString(R.string.failed_download))
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setProgress(0,0,false);
                    notificationManager.notify(Integer.parseInt(App.DOWNLOAD_CHANNEL_ID), download_notification.build());

                    downloading = false;

                    // SCAN NEXT IN QUEUE
                    startDownload(videos);
                });
        compositeDisposable.add(disposable);
    }


    public void addToHistory(Video video, Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        int year = cal.get(Calendar.YEAR);

        DateFormat formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time = formatter.format(date);
        String downloadedTime = day + " " + month + " " + year + " " + time;

        if(video != null){
            dbManager = new DBManager(context);
            try{
                video.setDownloadedTime(downloadedTime);
                dbManager.addToHistory(video);
            }catch(Exception ignored){}
        }
    }

    public void updateDownloadStatusOnResult(Video v, String type){
        if(v != null){
            dbManager = new DBManager(context);
            try{
                dbManager.updateDownloadStatusOnResult(v.getVideoId(), type);
            }catch(Exception ignored){}
        }
    }


    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    @NonNull
    private File getDownloadLocation(String type) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        String downloadsDir;
        if(type.equals("mp3")){
            downloadsDir = sharedPreferences.getString("music_path", "");
        }else{
            downloadsDir = sharedPreferences.getString("video_path", "");
        }
        File youtubeDLDir = new File(downloadsDir);
        if (!youtubeDLDir.exists()){
            boolean isDirCreated = youtubeDLDir.mkdir();
            if(!isDirCreated){
                Toast.makeText(context, R.string.failed_making_directory, Toast.LENGTH_LONG).show();
            }
        }
        return youtubeDLDir;
    }

    private void showStart(Video video) {
        progressBar.setProgress(0);

        //NOTIFICATION BUILDER
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent;
        pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        int PROGRESS_MAX = 100;
        int PROGRESS_CURR = progressBar.getProgress();

        download_notification = new NotificationCompat.Builder(context, App.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_download))
                .setContentTitle(video.getTitle())
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(PROGRESS_MAX, PROGRESS_CURR, false);

        notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(Integer.parseInt(App.DOWNLOAD_CHANNEL_ID), download_notification.build());
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

}