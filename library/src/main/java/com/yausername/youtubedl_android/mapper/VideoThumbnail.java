package com.yausername.youtubedl_android.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoThumbnail {
    private String url;
    private String id;

    public String getUrl() {
        return url;
    }

    public String getId() {
        return id;
    }
}
