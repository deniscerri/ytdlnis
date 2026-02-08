package com.deniscerri.ytdl.core.plugins

object NodeJS : PluginBase() {
    override val executableName: String get() = "node"
    override val pluginFolderName: String get() = "node"
    override val bundledZipName: String get() = "libnode.zip.so"
    override val bundledVersion: String get() = ""
    override val githubRepo: String  get() = "deniscerri/ytdlnis-plugins"
    override val githubPackageName: String  get() = "nodejs"
}