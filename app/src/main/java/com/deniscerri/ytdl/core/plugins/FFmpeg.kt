package com.deniscerri.ytdl.core.plugins

object FFmpeg : PluginBase() {
    override val pluginName: String get() = "ffmpeg"
    override val bundledZipName: String get() = "libffmpeg.zip.so"
    override val bundledVersion: String get() = "v7.1.1 [BUNDLED]"
    override val manifestURL: String  get() = ""


    @JvmStatic
    fun getInstance() = this
}