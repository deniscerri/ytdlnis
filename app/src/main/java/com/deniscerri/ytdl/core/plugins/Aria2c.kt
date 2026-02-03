package com.deniscerri.ytdl.core.plugins

object Aria2c : PluginBase() {
    override val pluginName: String get() = "aria2c"
    override val bundledZipName: String get() = "libaria2c.zip.so"
    override val bundledVersion: String get() = "v1.37.0 [BUNDLED]"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}