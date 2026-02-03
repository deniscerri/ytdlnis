package com.deniscerri.ytdl.core.plugins

object Python : PluginBase() {
    override val pluginName: String get() = "python"
    override val bundledZipName: String get() = "libpython.zip.so"
    override val bundledVersion: String get() = "v3.12.11 [BUNDLED]"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}