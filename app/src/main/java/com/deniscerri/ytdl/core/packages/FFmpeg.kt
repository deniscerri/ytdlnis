package com.deniscerri.ytdl.core.packages

object FFmpeg : PackageBase() {
    override val executableName: String = "ffmpeg"
    override val packageFolderName: String = "ffmpeg"
    override val bundledZipName: String = "libffmpeg.zip.so"
    override val canUninstall: Boolean = false
    override val bundledVersion: String = "v7.0.1"
    override val githubRepo: String  = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  = "ffmpeg"
    override val apkPackage: String = "com.deniscerri.ytdl.ffmpeg"
}