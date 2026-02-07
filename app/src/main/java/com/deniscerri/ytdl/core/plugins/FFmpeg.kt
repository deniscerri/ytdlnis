package com.deniscerri.ytdl.core.plugins

object FFmpeg : PluginBase() {
    override val executableName: String get() = "ffmpeg"
    override val pluginFolderName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    override val bundledVersion: String get() = "v7.1.1"
    override val githubRepositoryPackageURL: String  get() = ""
}