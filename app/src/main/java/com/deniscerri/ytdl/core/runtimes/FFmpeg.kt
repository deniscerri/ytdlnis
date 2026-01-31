package com.deniscerri.ytdl.core.runtimes

object FFmpeg : BaseRuntime() {
    override val runtimeName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}