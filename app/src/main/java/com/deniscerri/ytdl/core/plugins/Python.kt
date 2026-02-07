package com.deniscerri.ytdl.core.plugins

object Python : PluginBase() {
    override val executableName: String get() = "python"
    override val pluginFolderName: String get() = "python"
    override val bundledZipName: String get() = "libpython.zip.so"
    override val bundledVersion: String get() = "v3.12.11"
    override val githubRepositoryPackageURL: String  get() = ""
}