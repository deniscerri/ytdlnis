package com.deniscerri.ytdl.core.plugins

object FFmpeg : PluginBase() {
    override val executableName: String get() = "ffmpeg"
    override val pluginFolderName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    override val bundledVersion: String get() = "v7.0.1"
    override val packageGithubRepo: String  get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  get() = "ffmpeg"
}