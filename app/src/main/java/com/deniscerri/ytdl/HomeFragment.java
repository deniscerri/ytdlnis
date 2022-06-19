package com.deniscerri.ytdl;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.os.Bundle;
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
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.deniscerri.ytdl.api.YoutubeAPIManager;
import com.deniscerri.ytdl.database.DBManager;
import com.deniscerri.ytdl.database.Video;
import com.squareup.picasso.Picasso;
import com.yausername.youtubedl_android.DownloadProgressCallback;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static final String TAG = "HomeFragment";

    private ArrayList<Video> resultObjects;
    private Queue<Video> downloadQueue;
    private int[] positions = {0,0};

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private DBManager dbManager;
    private YoutubeAPIManager youtubeAPIManager;

    private final DownloadProgressCallback callback = new DownloadProgressCallback() {
        @Override
        public void onProgressUpdate(float progress, long etaInSeconds, String line) {
            requireActivity().runOnUiThread(() -> {
                progressBar.setProgress((int) progress);
            });
        }
    };


    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";


    public HomeFragment() {
        // Required empty public constructor
    }

    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
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

        compositeDisposable = new CompositeDisposable();

        // Inflate the layout for this fragment
        fragmentView = inflater.inflate(R.layout.fragment_home, container, false);

        initViews();

        fragmentView.setOnScrollChangeListener((view, i, i1, i2, i3) -> positions = new int[]{i,i1});

        if(inputQuery != null){
            parseQuery();
            inputQuery = null;
            return fragmentView;
        }

        dbManager = new DBManager(requireContext());
        resultObjects = dbManager.merrRezultatet();

        if(resultObjects != null){
            scrollView.post(() -> scrollView.scrollTo(positions[0], positions[1]));
            if(resultObjects.size() > 1 && resultObjects.get(1).getIsPlaylistItem() == 1){
                createDownloadAllCard();
            }
            for(int i = 0; i < resultObjects.size(); i++){
                createCard(resultObjects.get(i));
            }
        }


        return fragmentView;
    }


    private void initViews(){
        linearLayout = fragmentView.findViewById(R.id.linearLayout1);
        scrollView = fragmentView.findViewById(R.id.scrollView1);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        menu.clear();
        inflater.inflate(R.menu.main_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);

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

        menu.findItem(R.id.search).setOnActionExpandListener(onActionExpandListener);
        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        searchView.setQueryHint("Kërko nga YouTube");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                menu.findItem(R.id.search).collapseActionView();
                inputQuery = query.trim();
                parseQuery();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_results:
                dbManager.clearResults();
                linearLayout.removeAllViews();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }


    public void handleIntent(Intent intent){
        inputQuery = intent.getStringExtra(Intent.EXTRA_TEXT);
    }

    public void scrollToTop(){
        fragmentView.scrollTo(0,0);
    }

    private void parseQuery() {
        linearLayout.removeAllViews();

        resultObjects = new ArrayList<>();
        dbManager = new DBManager(requireContext());
        youtubeAPIManager = new YoutubeAPIManager(requireContext());

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
                    Thread thread = new Thread(() -> {
                        try {
                            resultObjects = youtubeAPIManager.search(inputQuery);
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    });
                    thread.start();
                    thread.join();
                    for(int i = 0; i < resultObjects.size(); i++){
                        createCard(resultObjects.get(i));
                    }
                    dbManager.shtoVideoRezultat(resultObjects);
                    break;
                }case "Video": {
                    String[] el = inputQuery.split("/");
                    inputQuery = el[el.length -1];

                    if(inputQuery.contains("watch?v=")){
                        inputQuery = inputQuery.substring(8);
                    }

                    Thread thread = new Thread(() -> {
                        try {
                            resultObjects.add(youtubeAPIManager.getVideo(inputQuery));
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    });
                    thread.start();
                    thread.join();

                    dbManager.shtoVideoRezultat(resultObjects);
                    createCard(resultObjects.get(0));
                    break;
                }case "Playlist": {
                    inputQuery = inputQuery.split("list=")[1];
                    Thread thread = new Thread(() -> {
                        try {
                            resultObjects = youtubeAPIManager.getPlaylist(inputQuery, "");
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    });
                    thread.start();
                    thread.join();

                    dbManager.shtoVideoRezultat(resultObjects);

                    // DOWNLOAD ALL BUTTON
                    if(resultObjects.size() > 1){
                        createDownloadAllCard();
                    }

                    for(int i  = 0 ; i < resultObjects.size(); i++){
                        createCard(resultObjects.get(i));
                    }
                    break;
                }
            }
        }catch(Exception e){
           Log.e(TAG, e.toString());
        }

    }

    private void createDownloadAllCard(){
        RelativeLayout r = new RelativeLayout(getContext());
        r.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
        r.getLayoutParams().height = getDp(90);

        CardView card = new CardView(requireContext());
        card.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(getDp(10));
        card.setCardElevation(10);
        card.setMaxCardElevation(12);
        card.setPreventCornerOverlap(true);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.black));

        // TITLE  ----------------------------------
        TextView videoTitle = new TextView(getContext());
        videoTitle.setLayoutParams(new RelativeLayout.LayoutParams(getDp(300), getDp(100)));
        int padding = getDp(20);
        videoTitle.setPadding(padding, padding, padding, padding);

        videoTitle.setText("Shkarko të gjitha");
        videoTitle.setTextSize(getDp(5));
        videoTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoTitle.setTypeface(null, Typeface.BOLD);
        videoTitle.setShadowLayer(2f, 4f, 4f, ContextCompat.getColor(requireContext(), R.color.black));

        // BUTTONS -------------------------------------------
        LinearLayout buttonLayout = new LinearLayout(requireContext());
        buttonLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        buttonLayout.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 0, getDp(20), getDp(10));

        Button musicBtn = new Button(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getDp(48), getDp(48)
        );
        params.setMargins(10,0, 10, 10);
        musicBtn.setLayoutParams(params);
        //musicBtn.setIconSize(getDp(24));
        musicBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_music));
        musicBtn.setTag("ALL##mp3");

        Button videoBtn = new Button(requireContext());
        videoBtn.setLayoutParams(params);
        //videoBtn.setIconSize(getDp(24));
        videoBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_video));
        videoBtn.setTag("ALL##mp4");

        buttonLayout.addView(musicBtn);
        buttonLayout.addView(videoBtn);

        musicBtn.setOnClickListener(this);
        videoBtn.setOnClickListener(this);

        card.addView(videoTitle);
        card.addView(buttonLayout);

        r.addView(card);

        linearLayout.addView(r);


    }

    private int getDp(int value){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int getSp(int value){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private void createCard(Video video){

        RelativeLayout r = new RelativeLayout(getContext());
        r.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
        r.getLayoutParams().height = getDp(230);

        CardView card = new CardView(requireContext());
        card.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(getDp(10));
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
        videoTitle.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, getDp(100)));
        int padding = getDp(20);
        videoTitle.setPadding(padding, padding, padding, padding);
        String title = video.getTitle();

        title = title.replace("&amp;", "&").replace("&quot;", "\"");


        if(title.length() > 50){
            title = title.substring(0, 40) + "...";
        }

        videoTitle.setText(title);
        videoTitle.setTextSize(getSp(5));
        videoTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoTitle.setTypeface(null, Typeface.BOLD);
        videoTitle.setShadowLayer(2f, 4f, 4f, ContextCompat.getColor(requireContext(), R.color.black));

        // AUTHOR ----------------------------------

        TextView videoAuthor = new TextView(getContext());
        videoAuthor.setGravity(Gravity.BOTTOM);
        videoAuthor.setLayoutParams(new RelativeLayout.LayoutParams(getDp(200), getDp(200)));
        videoAuthor.setPadding(getDp(20), 0, 0, getDp(10));
        videoAuthor.setShadowLayer(1.5f, 4f, 4f, R.color.black);

        String author = video.getAuthor();

        videoAuthor.setText(author);
        videoAuthor.setTextSize(getSp(3));
        videoAuthor.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        videoAuthor.setShadowLayer(2f, 4f, 4f, ContextCompat.getColor(requireContext(), R.color.black));
        videoAuthor.setTypeface(null, Typeface.BOLD);

        // BUTTONS ----------------------------------
        String videoID = video.getVideoId();


        LinearLayout buttonLayout = new LinearLayout(requireContext());
        buttonLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        buttonLayout.setGravity(Gravity.BOTTOM | Gravity.RIGHT);

        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(getDp(10), 0, getDp(10), getDp(10));

        Button musicBtn = new Button(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getDp(48), getDp(48)
        );
        params.setMargins(0,0, getDp(10), 0);
        musicBtn.setLayoutParams(params);
        //musicBtn.setIconSize(getDp(24));
        musicBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_music));
        musicBtn.setTag(videoID + "##mp3");

        Button videoBtn = new Button(requireContext());
        videoBtn.setLayoutParams(params);
        //videoBtn.setIconSize(getDp(24));
        videoBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_video));
        videoBtn.setTag(videoID + "##mp4");

        buttonLayout.addView(musicBtn);
        buttonLayout.addView(videoBtn);

        musicBtn.setOnClickListener(this);
        videoBtn.setOnClickListener(this);

        // PROGRESS BAR ----------------------------------------------------

        ProgressBar progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, getDp(5));
        progressParams.setMargins(0, r.getLayoutParams().height - 70, 0 ,0);
        progressBar.setLayoutParams(progressParams);
        progressBar.setBackgroundTintMode(PorterDuff.Mode.SRC_IN);
        progressBar.setScaleY(2);

        progressBar.setY(card.getY() - progressBar.getHeight() / 2);
        progressBar.setTag(videoID + "##progress");

        if(video.getDownloadedTime() != null){
            progressBar.setProgress(100);
            progressBar.setVisibility(View.VISIBLE);
        }


        // Adding all layouts to the card

        card.addView(thumbnail);
        card.addView(videoTitle);
        card.addView(videoAuthor);
        card.addView(buttonLayout);
        card.addView(progressBar);

        card.setTag(videoID + "##card");


        r.addView(card);

        linearLayout.addView(r);
    }


    @Override
    public void onClick(View v) {
        //do what you want to do when button is clicked
        String viewIdName = v.getTag().toString();
        Log.e(TAG, viewIdName);
        if(viewIdName.contains("mp3") || viewIdName.contains("mp4")){
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
        Video video = null;
        try{
            video = videos.remove();
        }catch(Exception e){
            return;
        }

        if (downloading) {
            Toast.makeText(getContext(), "Nuk mund te filloj! Nje shkarkim tjeter është në punë!", Toast.LENGTH_LONG).show();
            return;
        }

        if (!isStoragePermissionGranted()) {
            Toast.makeText(getContext(), "Pranoje Lejen dhe provo përsëri!", Toast.LENGTH_LONG).show();
            return;
        }
        String id = video.getVideoId();
        String url = "https://www.youtube.com/watch?v=" + id;
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        String type = video.getDownloadedType();
        File youtubeDLDir = getDownloadLocation(type);

        Log.e(TAG, youtubeDLDir.getAbsolutePath());

        if(type.equals("mp3")){
            request.addOption("--embed-thumbnail");
            request.addOption("--postprocessor-args", "-write_id3v1 1 -id3v2_version 3");
            request.addOption("--add-metadata");
            request.addOption("--no-mtime");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");
        }else if(type.equals("mp4")){
            request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
        }
        request.addOption("-o", youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s");

        View card = fragmentView.findViewWithTag(id+"##card");
        int[] cardPosition = new int[2];
        card.getLocationInWindow(cardPosition);
        scrollView.post(() -> scrollView.scrollTo(cardPosition[0]-1000, cardPosition[1]-1000));

        progressBar = fragmentView.findViewWithTag(id+"##progress");
        progressBar.setVisibility(View.VISIBLE);

        showStart();
        downloading = true;

        Video theVideo = video;
        Disposable disposable = Observable.fromCallable(() -> YoutubeDL.getInstance().execute(request, callback))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(youtubeDLResponse -> {
                    progressBar.setProgress(100);
                    Toast.makeText(getContext(), "Shkarkimi i be me Sukses!", Toast.LENGTH_LONG).show();
                    addToHistory(theVideo, new Date());
                    downloading = false;

                    // MEDIA SCAN
                    MediaScannerConnection.scanFile(requireContext(), new String[]{youtubeDLDir.getAbsolutePath()}, null, null);

                    // SCAN NEXT IN QUEUE
                    startDownload(videos);
                }, e -> {
                    if(BuildConfig.DEBUG) Log.e(TAG,  "Deshtim ne shkarkim! :(", e);
                    Toast.makeText(getContext(), "Deshtim ne shkarkim! :(", Toast.LENGTH_LONG).show();
                    downloading = false;
                });
        compositeDisposable.add(disposable);

    }


    public void addToHistory(Video video, Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        String month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        int year = cal.get(Calendar.YEAR);

        DateFormat formatter = new SimpleDateFormat("HH:mm");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time = formatter.format(date);
        String downloadedTime = day + " " + month + " " + year + " " + time;

        if(video != null){
            dbManager = new DBManager(requireContext());
            try{
                String id = video.getVideoId();
                String title = video.getTitle();
                String author = video.getAuthor();
                String thumb = video.getThumb();
                String downloadedType = video.getDownloadedType();
                video.setDownloadedTime(downloadedTime);

                dbManager.shtoVideoHistori(video);
                dbManager.shkoKohenRezultatit(id, downloadedTime);


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
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE);
        String downloadsDir;
        if(type.equals("mp3")){
            downloadsDir = sharedPreferences.getString("music_path", "");
        }else{
            downloadsDir = sharedPreferences.getString("video_path", "");
        }
        File youtubeDLDir = new File(downloadsDir);
        if (!youtubeDLDir.exists()) youtubeDLDir.mkdir();
        return youtubeDLDir;
    }

    private void showStart() {
        Toast.makeText(getContext(), "Shkarkimi Filloi!", Toast.LENGTH_LONG).show();
        progressBar.setProgress(0);
    }

    public boolean isStoragePermissionGranted() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }

}