package com.deniscerri.ytdlnis.service;

import com.deniscerri.ytdlnis.database.Video;

import java.util.LinkedList;

public class DownloadInfo {
    private Video video;
    private int progress;
    private LinkedList<Video> downloadQueue;
    private String outputLine;
    private String downloadStatus;
    private String downloadPath;
    private String downloadType;

    public DownloadInfo(){}

    public Video getVideo() {
        return video;
    }

    public void setVideo(Video video) {
        this.video = video;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public LinkedList<Video> getDownloadQueue() {
        return downloadQueue;
    }

    public void setDownloadQueue(LinkedList<Video> downloadQueue) {
        this.downloadQueue = downloadQueue;
        this.video = downloadQueue.peek();
    }

    public String getOutputLine() {
        return outputLine;
    }

    public void setOutputLine(String outputLine) {
        this.outputLine = outputLine;
    }

    public String getDownloadStatus(){
        return downloadStatus;
    }

    public void setDownloadStatus(String status){
        downloadStatus = status;
    }

    public String getDownloadPath(){
        return downloadPath;
    }

    public void setDownloadPath(String path){
        this.downloadPath = path;
    }

    public String getDownloadType() {
        return downloadType;
    }

    public void setDownloadType(String downloadType) {
        this.downloadType = downloadType;
    }
}
