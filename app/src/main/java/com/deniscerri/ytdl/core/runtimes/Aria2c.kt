package com.deniscerri.ytdl.core.runtimes

object Aria2c : BaseRuntime() {
    override val runtimeName: String get() = "aria2c"
    override val bundledZipName: String get() = "libaria2c.zip.so"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}