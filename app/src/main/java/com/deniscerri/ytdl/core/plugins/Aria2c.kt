package com.deniscerri.ytdl.core.plugins

object Aria2c : PluginBase() {
    override val executableName: String get() = "aria2c"
    override val pluginFolderName: String get() = "aria2c"
    override val bundledZipName: String get() = "libaria2c.zip.so"
    override val bundledVersion: String get() = "v1.37.0"
    override val githubRepositoryPackageURL: String  get() = ""
}