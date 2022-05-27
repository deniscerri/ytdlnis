package com.yausername.youtubedl_android;

import java.util.List;

public class YoutubeDLResponse {

    private List<String> command;
    private int exitCode;
    private String out;
    private String err;
    private long elapsedTime;

    public YoutubeDLResponse(List<String> command, int exitCode, long elapsedTime, String out, String err) {
        this.command = command;
        this.elapsedTime = elapsedTime;
        this.exitCode = exitCode;
        this.out = out;
        this.err = err;
    }

    public List<String> getCommand() {
        return command;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getOut() {
        return out;
    }

    public String getErr() {
        return err;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }
}
