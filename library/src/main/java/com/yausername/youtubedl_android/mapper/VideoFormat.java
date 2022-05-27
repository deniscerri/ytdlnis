package com.yausername.youtubedl_android.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoFormat {

    private int asr;
    private int tbr;
    private int abr;
    private String format;
    @JsonProperty("format_id")
    private String formatId;
    @JsonProperty("format_note")
    private String formatNote;
    private String ext;
    private int preference;
    private String vcodec;
    private String acodec;
    private int width;
    private int height;
    private long filesize;
    private int fps;
    private String url;
    @JsonProperty("manifest_url")
    private String manifestUrl;
    @JsonProperty("http_headers")
    private Map<String, String> httpHeaders;

    public int getAsr() {
        return asr;
    }

    public int getTbr() {
        return tbr;
    }

    public int getAbr() {
        return abr;
    }

    public String getFormat() {
        return format;
    }

    public String getFormatId() {
        return formatId;
    }

    public String getFormatNote() {
        return formatNote;
    }

    public String getExt() {
        return ext;
    }

    public int getPreference() {
        return preference;
    }

    public String getVcodec() {
        return vcodec;
    }

    public String getAcodec() {
        return acodec;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getFilesize() {
        return filesize;
    }

    public int getFps() {
        return fps;
    }

    public String getUrl() {
        return url;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }
}
