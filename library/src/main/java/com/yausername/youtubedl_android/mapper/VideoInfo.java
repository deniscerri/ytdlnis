package com.yausername.youtubedl_android.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoInfo {

    private String id;
    private String fulltitle;
    private String title;
    @JsonProperty("upload_date")
    private String uploadDate;
    @JsonProperty("display_id")
    private String displayId;
    private int duration;
    private String description;
    private String thumbnail;
    private String license;

    @JsonProperty("view_count")
    private String viewCount;
    @JsonProperty("like_count")
    private String likeCount;
    @JsonProperty("dislike_count")
    private String dislikeCount;
    @JsonProperty("repost_count")
    private String repostCount;
    @JsonProperty("average_rating")
    private String averageRating;


    @JsonProperty("uploader_id")
    private String uploaderId;
    private String uploader;

    @JsonProperty("player_url")
    private String playerUrl;
    @JsonProperty("webpage_url")
    private String webpageUrl;
    @JsonProperty("webpage_url_basename")
    private String webpageUrlBasename;

    private String resolution;
    private int width;
    private int height;
    private String format;
    @JsonProperty("format_id")
    private String formatId;
    private String ext;

    @JsonProperty("http_headers")
    private Map<String, String> httpHeaders;
    private ArrayList<String> categories;
    private ArrayList<String> tags;
    @JsonProperty("requested_formats")
    private ArrayList<VideoFormat> requestedFormats;
    private ArrayList<VideoFormat> formats;
    private ArrayList<VideoThumbnail> thumbnails;
    //private ArrayList<VideoSubtitle> subtitles;
    @JsonProperty("manifest_url")
    private String manifestUrl;
    private String url;

    public String getId() {
        return id;
    }

    public String getFulltitle() {
        return fulltitle;
    }

    public String getTitle() {
        return title;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public String getDisplayId() {
        return displayId;
    }

    public int getDuration() {
        return duration;
    }

    public String getDescription() {
        return description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public String getLicense() {
        return license;
    }

    public String getViewCount() {
        return viewCount;
    }

    public String getLikeCount() {
        return likeCount;
    }

    public String getDislikeCount() {
        return dislikeCount;
    }

    public String getRepostCount() {
        return repostCount;
    }

    public String getAverageRating() {
        return averageRating;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getUploader() {
        return uploader;
    }

    public String getPlayerUrl() {
        return playerUrl;
    }

    public String getWebpageUrl() {
        return webpageUrl;
    }

    public String getWebpageUrlBasename() {
        return webpageUrlBasename;
    }

    public String getResolution() {
        return resolution;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getFormat() {
        return format;
    }

    public String getFormatId() {
        return formatId;
    }

    public String getExt() {
        return ext;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public ArrayList<String> getCategories() {
        return categories;
    }

    public ArrayList<String> getTags() {
        return tags;
    }

    public ArrayList<VideoFormat> getRequestedFormats() {
        return requestedFormats;
    }

    public ArrayList<VideoFormat> getFormats() {
        return formats;
    }

    public ArrayList<VideoThumbnail> getThumbnails() {
        return thumbnails;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public String getUrl() {
        return url;
    }
}
