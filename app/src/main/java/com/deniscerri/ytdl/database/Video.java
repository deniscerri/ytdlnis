package com.deniscerri.ytdl.database;

public class Video {
    private int id;
    private String videoId;
    private String title;
    private String author;
    private String thumb;
    private String downloadedType;
    private String downloadedTime;

    public Video(String videoId, String title, String author, String thumb, String downloadedType, String downloadedTime) {
        this.videoId = videoId;
        this.title = title;
        this.author = author;
        this.thumb = thumb;
        this.downloadedType = downloadedType;
        this.downloadedTime = downloadedTime;
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

    public void setVideoId(String videoId) {
        this.videoId = videoId;
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

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getThumb() {
        return thumb;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
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
}
