package com.deniscerri.ytdl.core.runtimes

object Python : BaseRuntime() {
    override val runtimeName: String get() = "python"
    override val bundledZipName: String get() = "libpython.zip.so"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}