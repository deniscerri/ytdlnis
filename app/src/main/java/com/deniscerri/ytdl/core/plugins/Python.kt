package com.deniscerri.ytdl.core.plugins

object Python : PluginBase() {
    override val executableName: String get() = "python"
    override val pluginFolderName: String get() = "python"
    override val bundledZipName: String get() = "libpython.zip.so"
    override val bundledVersion: String get() = "v3.12"
    override val githubRepo: String  get() = "deniscerri/ytdlnis-plugins"
    override val githubPackageName: String  get() = "python"
}