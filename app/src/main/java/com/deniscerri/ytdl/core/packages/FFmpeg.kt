package com.deniscerri.ytdl.core.packages

object FFmpeg : PackageBase() {
    override val executableName: String get() = "ffmpeg"
    override val packageFolderName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    override val canUninstall: Boolean = false
    override val bundledVersion: String get() = "v7.0.1"
    override val githubRepo: String  get() = "deniscerri/ytdlnis-plugins"
    override val githubPackageName: String  get() = "ffmpeg"
}