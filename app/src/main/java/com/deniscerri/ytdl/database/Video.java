package com.deniscerri.ytdl.database;

public class Video {
    private int id;
    private String videoId;
    private String title;
    private String author;
    private String duration;
    private String thumb;
    private String downloadedType;
    private int downloadedAudio;
    private int downloadedVideo;
    private String downloadedTime;
    private int isPlaylistItem;


    // RESULTS OBJECT
    public Video(String videoId, String title, String author, String duration, String thumb,
                 int downloadedAudio, int downloadedVideo, int isPlaylistItem) {
        this.videoId = videoId;
        this.title = title;
        this.author = author;
        this.duration = duration;
        this.thumb = thumb;
        this.downloadedVideo = downloadedVideo;
        this.downloadedAudio = downloadedAudio;
        this.isPlaylistItem = isPlaylistItem;
    }

    //HISTORY OBJECT
    public Video(String videoId, String title, String author, String duration, String thumb,
                  String downloadedType, String downloadedTime, int isPlaylistItem) {
        this.videoId = videoId;
        this.title = title;
        this.author = author;
        this.duration = duration;
        this.thumb = thumb;
        this.downloadedType = downloadedType;
        this.downloadedTime = downloadedTime;
        this.isPlaylistItem = isPlaylistItem;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public String getDuration() {
        return duration;
    }

    public String getThumb() {
        return thumb;
    }

    public String getDownloadedType() {
        return downloadedType;
    }

    public void setDownloadedType(String downloadedType) {
        this.downloadedType = downloadedType;
    }

    public String getDownloadedTime() {
        return downloadedTime;
    }

    public void setDownloadedTime(String downloadedTime) {
        this.downloadedTime = downloadedTime;
    }

    public int getIsPlaylistItem() {
        return isPlaylistItem;
    }

    public void setIsPlaylistItem(int playlistItem) {
        this.isPlaylistItem = playlistItem;
    }

    public int isAudioDownloaded() {
        return downloadedAudio;
    }

    public int isVideoDownloaded() {
        return downloadedVideo;
    }
}
