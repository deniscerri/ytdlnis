package com.deniscerri.ytdlnis.database;

import java.io.Serializable;

public class Video implements Serializable {
    private int id;
    private String videoId;
    private String title;
    private String author;
    private String duration;
    private String thumb;
    private String url;
    private String downloadedType;
    private int downloadedAudio;
    private int downloadedVideo;
    private String downloadedTime;
    private int isPlaylistItem;
    private String website;

    // RESULTS OBJECT
    public Video(String videoId, String url, String title, String author, String duration, String thumb,
                 int downloadedAudio, int downloadedVideo, int isPlaylistItem, String website) {
        this.videoId = videoId;
        this.url = url;
        this.title = title;
        this.author = author;
        this.duration = duration;
        this.thumb = thumb;
        this.downloadedVideo = downloadedVideo;
        this.downloadedAudio = downloadedAudio;
        this.isPlaylistItem = isPlaylistItem;
        this.website = website;
    }

    //HISTORY OBJECT
    public Video(int id, String videoId, String title, String author, String duration, String thumb,
                  String downloadedType, String downloadedTime, int isPlaylistItem) {
        this.id = id;
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

    public String getURL(){ return url; }

    public void setURL(String url) { this.url = url;}

    public String getWebsite() { return website; }

    public void setWebsite(String site){ this.website = site; }
}
