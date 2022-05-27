package com.yausername.youtubedl_android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yausername.youtubedl_common.SharedPrefsHelper;
import com.yausername.youtubedl_common.utils.ZipUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class YoutubeDL {

    private static final YoutubeDL INSTANCE = new YoutubeDL();
    protected static final String baseName = "youtubedl-android";
    private static final String packagesRoot = "packages";
    private static final String pythonBinName = "libpython.bin.so";
    private static final String pythonLibName = "libpython.zip.so";
    private static final String pythonDirName = "python";
    private static final String ffmpegDirName = "ffmpeg";
    protected static final String youtubeDLDirName = "yt-dlp";
    private static final String youtubeDLBin = "__main__.py";
    protected static final String youtubeDLFile = "yt_dlp.zip";
    private static final String pythonLibVersion = "pythonLibVersion";

    private boolean initialized = false;
    private File pythonPath;
    private File youtubeDLPath;
    private File binDir;

    private String ENV_LD_LIBRARY_PATH;
    private String ENV_SSL_CERT_FILE;
    private String ENV_PYTHONHOME;

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    private YoutubeDL(){
    }

    public static YoutubeDL getInstance() {
        return INSTANCE;
    }

    synchronized public void init(Context appContext) throws YoutubeDLException {
        if (initialized) return;

        File baseDir = new File(appContext.getNoBackupFilesDir(), baseName);
        if(!baseDir.exists()) baseDir.mkdir();

        File packagesDir = new File(baseDir, packagesRoot);
        binDir = new File(appContext.getApplicationInfo().nativeLibraryDir);
        pythonPath = new File(binDir, pythonBinName);
        File pythonDir = new File(packagesDir, pythonDirName);
        File ffmpegDir = new File(packagesDir, ffmpegDirName);

        File youtubeDLDir = new File(baseDir, youtubeDLDirName);
        youtubeDLPath = new File(youtubeDLDir, youtubeDLBin);

        ENV_LD_LIBRARY_PATH = pythonDir.getAbsolutePath() + "/usr/lib" + ":" + ffmpegDir.getAbsolutePath() + "/usr/lib";
        ENV_SSL_CERT_FILE = pythonDir.getAbsolutePath() + "/usr/etc/tls/cert.pem";
        ENV_PYTHONHOME = pythonDir.getAbsolutePath() + "/usr";

        initPython(appContext, pythonDir);
        initYoutubeDL(appContext, youtubeDLDir);

        initialized = true;
    }

    protected void initYoutubeDL(Context appContext, File youtubeDLDir) throws YoutubeDLException {
        if (!youtubeDLDir.exists()) {
            youtubeDLDir.mkdirs();
            try {
                ZipUtils.unzip(appContext.getResources().openRawResource(R.raw.yt_dlp), youtubeDLDir);
            } catch (Exception e) {
                FileUtils.deleteQuietly(youtubeDLDir);
                throw new YoutubeDLException("failed to initialize", e);
            }
        }
    }

    protected void initPython(Context appContext, File pythonDir) throws YoutubeDLException {
        File pythonLib = new File(binDir, pythonLibName);
        // using size of lib as version
        String pythonSize = String.valueOf(pythonLib.length());
        if (!pythonDir.exists() || shouldUpdatePython(appContext, pythonSize)) {
            FileUtils.deleteQuietly(pythonDir);
            pythonDir.mkdirs();
            try {
                ZipUtils.unzip(pythonLib, pythonDir);
            } catch (Exception e) {
                FileUtils.deleteQuietly(pythonDir);
                throw new YoutubeDLException("failed to initialize", e);
            }
            updatePython(appContext, pythonSize);
        }
    }

    private boolean shouldUpdatePython(@NonNull Context appContext, @NonNull String version) {
        return !version.equals(SharedPrefsHelper.get(appContext, pythonLibVersion));
    }

    private void updatePython(@NonNull Context appContext, @NonNull String version) {
        SharedPrefsHelper.update(appContext, pythonLibVersion, version);
    }

    private void assertInit() {
        if (!initialized) throw new IllegalStateException("instance not initialized");
    }

    public VideoInfo getInfo(String url) throws YoutubeDLException, InterruptedException {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        return getInfo(request);
    }

    @NonNull
    public VideoInfo getInfo(YoutubeDLRequest request) throws YoutubeDLException, InterruptedException {
        request.addOption("--dump-json");
        YoutubeDLResponse response = execute(request, null);

        VideoInfo videoInfo;
        try {
            videoInfo = objectMapper.readValue(response.getOut(), VideoInfo.class);
        } catch (IOException e) {
            throw new YoutubeDLException("Unable to parse video information", e);
        }

        if(videoInfo == null){
            throw new YoutubeDLException("Failed to fetch video information");
        }

        return videoInfo;
    }

    public YoutubeDLResponse execute(YoutubeDLRequest request) throws YoutubeDLException, InterruptedException {
        return execute(request, null);
    }

    public YoutubeDLResponse execute(YoutubeDLRequest request, @Nullable DownloadProgressCallback callback) throws YoutubeDLException, InterruptedException {
        assertInit();

        // disable caching unless explicitly requested
        if(request.getOption("--cache-dir") == null){
            request.addOption("--no-cache-dir");
        }

        YoutubeDLResponse youtubeDLResponse;
        Process process;
        int exitCode;
        StringBuffer outBuffer = new StringBuffer(); //stdout
        StringBuffer errBuffer = new StringBuffer(); //stderr
        long startTime = System.currentTimeMillis();

        List<String> args = request.buildCommand();
        List<String> command = new ArrayList<>();
        command.addAll(Arrays.asList(pythonPath.getAbsolutePath(), youtubeDLPath.getAbsolutePath()));
        command.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> env = processBuilder.environment();
        env.put("LD_LIBRARY_PATH", ENV_LD_LIBRARY_PATH);
        env.put("SSL_CERT_FILE", ENV_SSL_CERT_FILE);
        env.put("PATH",  System.getenv("PATH") + ":" + binDir.getAbsolutePath());
        env.put("PYTHONHOME", ENV_PYTHONHOME);

        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new YoutubeDLException(e);
        }

        InputStream outStream = process.getInputStream();
        InputStream errStream = process.getErrorStream();

        StreamProcessExtractor stdOutProcessor = new StreamProcessExtractor(outBuffer, outStream, callback);
        StreamGobbler stdErrProcessor = new StreamGobbler(errBuffer, errStream);

        try {
            stdOutProcessor.join();
            stdErrProcessor.join();
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            throw e;
        }

        String out = outBuffer.toString();
        String err = errBuffer.toString();

        if (exitCode > 0) {
            throw new YoutubeDLException(err);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        youtubeDLResponse = new YoutubeDLResponse(command, exitCode, elapsedTime, out, err);

        return youtubeDLResponse;
    }

    synchronized public UpdateStatus updateYoutubeDL(Context appContext) throws YoutubeDLException {
        assertInit();
        try {
            return YoutubeDLUpdater.update(appContext);
        } catch (IOException e) {
            throw new YoutubeDLException("failed to update youtube-dl", e);
        }
    }

    @Nullable
    public String version(Context appContext) {
        return YoutubeDLUpdater.version(appContext);
    }

    public enum UpdateStatus {
        DONE, ALREADY_UP_TO_DATE;
    }
}
