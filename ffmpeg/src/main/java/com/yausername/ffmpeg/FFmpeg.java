package com.yausername.ffmpeg;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_common.SharedPrefsHelper;
import com.yausername.youtubedl_common.utils.ZipUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class FFmpeg {

    private static final FFmpeg INSTANCE = new FFmpeg();
    protected static final String baseName = "youtubedl-android";
    private static final String packagesRoot = "packages";
    private static final String ffmegDirName = "ffmpeg";
    private static final String ffmpegLibName = "libffmpeg.zip.so";
    private static final String ffmpegLibVersion = "ffmpegLibVersion";

    private boolean initialized = false;
    private File binDir;

    private FFmpeg(){
    }

    public static FFmpeg getInstance(){
        return INSTANCE;
    }

    synchronized public void init(Context appContext) throws YoutubeDLException {
        if (initialized) return;

        File baseDir = new File(appContext.getNoBackupFilesDir(), baseName);
        if(!baseDir.exists()) baseDir.mkdir();

        binDir = new File(appContext.getApplicationInfo().nativeLibraryDir);

        File packagesDir = new File(baseDir, packagesRoot);
        File ffmpegDir = new File(packagesDir, ffmegDirName);
        initFFmpeg(appContext, ffmpegDir);

        initialized = true;
    }

    private void initFFmpeg(Context appContext, File ffmpegDir) throws YoutubeDLException {
        File ffmpegLib = new File(binDir, ffmpegLibName);
        // using size of lib as version
        String ffmpegSize = String.valueOf(ffmpegLib.length());
        if (!ffmpegDir.exists() || shouldUpdateFFmpeg(appContext, ffmpegSize)) {
            FileUtils.deleteQuietly(ffmpegDir);
            ffmpegDir.mkdirs();
            try {
                ZipUtils.unzip(ffmpegLib, ffmpegDir);
            } catch (Exception e) {
                FileUtils.deleteQuietly(ffmpegDir);
                throw new YoutubeDLException("failed to initialize", e);
            }
            updateFFmpeg(appContext, ffmpegSize);
        }
    }

    private boolean shouldUpdateFFmpeg(@NonNull Context appContext, @NonNull String version) {
        return !version.equals(SharedPrefsHelper.get(appContext, ffmpegLibVersion));
    }

    private void updateFFmpeg(@NonNull Context appContext, @NonNull String version) {
        SharedPrefsHelper.update(appContext, ffmpegLibVersion, version);
    }
}
