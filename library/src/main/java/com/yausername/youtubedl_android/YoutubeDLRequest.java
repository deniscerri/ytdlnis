package com.yausername.youtubedl_android;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YoutubeDLRequest {

    private List<String> urls;
    private YoutubeDLOptions options = new YoutubeDLOptions();

    public YoutubeDLRequest(String url) {
        this.urls = Arrays.asList(url);
    }

    public YoutubeDLRequest(@NonNull  List<String> urls) {
        this.urls = urls;
    }

    public YoutubeDLRequest addOption(@NonNull String key, @NonNull String value){
        options.addOption(key, value);
        return this;
    }

    public YoutubeDLRequest addOption(@NonNull String key, @NonNull Number value){
        options.addOption(key, value);
        return this;
    }

    public YoutubeDLRequest addOption(String key){
        options.addOption(key);
        return this;
    }

    public Object getOption(String key){
        return options.getOption(key);
    }

    public List<String> buildCommand(){
        List<String> command = new ArrayList<>();
        command.addAll(options.buildOptions());
        command.addAll(urls);
        return command;
    }

}
