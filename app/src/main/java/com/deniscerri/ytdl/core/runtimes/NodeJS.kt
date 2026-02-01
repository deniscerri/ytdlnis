package com.deniscerri.ytdl.core.runtimes

object NodeJS : BaseRuntime() {
    override val runtimeName: String get() = "node"
    override val bundledZipName: String get() = "libnode.zip.so"
    override val manifestURL: String  get() = ""

    @JvmStatic
    fun getInstance() = this
}