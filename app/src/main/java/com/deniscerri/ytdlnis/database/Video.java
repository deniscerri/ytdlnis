package com.deniscerri.ytdlnis.database;

import android.os.Parcel;
import android.os.Parcelable;

public class Video implements Parcelable, Cloneable {
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
    private String downloadPath;
    private String website;
    private boolean downloadingAudio;
    private boolean downloadingVideo;
    private boolean queuedDownload;
    private String playlistTitle;

    // RESULTS OBJECT
    public Video(String videoId, String url, String title, String author, String duration, String thumb,
                 int downloadedAudio, int downloadedVideo, int isPlaylistItem, String website, int downloadingAudio, int downloadingVideo, String playlistTitle) {
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
        this.downloadingAudio = intToBoolean(downloadingAudio);
        this.downloadingVideo = intToBoolean(downloadingVideo);
        this.playlistTitle = playlistTitle;
    }

    //HISTORY OBJECT
    public Video(int id, String url, String title, String author, String duration, String thumb,
                  String downloadedType, String downloadedTime, String downloadPath, String website, int queued) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.author = author;
        this.duration = duration;
        this.thumb = thumb;
        this.downloadedType = downloadedType;
        this.downloadedTime = downloadedTime;
        this.downloadPath = downloadPath;
        this.website = website;
        this.queuedDownload = (queued == 1);
    }

    private boolean intToBoolean(int i){
        return i == 1;
    }

    protected Video(Parcel in) {
        id = in.readInt();
        videoId = in.readString();
        title = in.readString();
        author = in.readString();
        duration = in.readString();
        thumb = in.readString();
        url = in.readString();
        downloadedType = in.readString();
        downloadedAudio = in.readInt();
        downloadedVideo = in.readInt();
        downloadedTime = in.readString();
        isPlaylistItem = in.readInt();
        downloadPath = in.readString();
        website = in.readString();
        playlistTitle = in.readString();
    }

    public static final Creator<Video> CREATOR = new Creator<Video>() {
        @Override
        public Video createFromParcel(Parcel in) {
            return new Video(in);
        }

        @Override
        public Video[] newArray(int size) {
            return new Video[size];
        }
    };

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

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getDownloadedAudio() {
        return downloadedAudio;
    }

    public void setDownloadedAudio(int downloadedAudio) {
        this.downloadedAudio = downloadedAudio;
    }

    public int getDownloadedVideo() {
        return downloadedVideo;
    }

    public void setDownloadedVideo(int downloadedVideo) {
        this.downloadedVideo = downloadedVideo;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public boolean isDownloadingAudio() {
        return downloadingAudio;
    }

    public void setDownloadingAudio(boolean downloadingAudio) {
        this.downloadingAudio = downloadingAudio;
    }

    public boolean isDownloadingVideo() {
        return downloadingVideo;
    }

    public void setDownloadingVideo(boolean downloadingVideo) {
        this.downloadingVideo = downloadingVideo;
    }

    public boolean isDownloading() {
        return this.downloadingAudio || this.downloadingVideo;
    }

    public String getPlaylistTitle() {
        return playlistTitle;
    }

    public void setPlaylistTitle(String playlistTitle) {
        this.playlistTitle = playlistTitle;
    }

    public boolean isQueuedDownload() {
        return queuedDownload;
    }

    public void setQueuedDownload(boolean queuedDownload) {
        this.queuedDownload = queuedDownload;
    }

    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(id);
        parcel.writeString(videoId);
        parcel.writeString(title);
        parcel.writeString(author);
        parcel.writeString(duration);
        parcel.writeString(thumb);
        parcel.writeString(url);
        parcel.writeString(downloadedType);
        parcel.writeInt(downloadedAudio);
        parcel.writeInt(downloadedVideo);
        parcel.writeString(downloadedTime);
        parcel.writeInt(isPlaylistItem);
        parcel.writeString(downloadPath);
        parcel.writeString(website);
    }

    @Override
    public String toString() {
        return "Video{" +
                "id=" + id +
                ", videoId='" + videoId + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", duration='" + duration + '\'' +
                ", thumb='" + thumb + '\'' +
                ", url='" + url + '\'' +
                ", downloadedType='" + downloadedType + '\'' +
                ", downloadedAudio=" + downloadedAudio +
                ", downloadedVideo=" + downloadedVideo +
                ", downloadedTime='" + downloadedTime + '\'' +
                ", isPlaylistItem=" + isPlaylistItem +
                ", downloadPath='" + downloadPath + '\'' +
                ", website='" + website + '\'' +
                ", downloadingAudio=" + downloadingAudio +
                ", downloadingVideo=" + downloadingVideo +
                '}';
    }
}
