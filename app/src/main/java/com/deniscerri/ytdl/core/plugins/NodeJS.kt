package com.deniscerri.ytdl.core.plugins

object NodeJS : PluginBase() {
    override val pluginName: String get() = "node"
    override val bundledZipName: String get() = "libnode.zip.so"
    override val bundledVersion: String get() = "v25.3.0 [BUNDLED]"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}