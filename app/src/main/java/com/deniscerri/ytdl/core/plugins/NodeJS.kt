package com.deniscerri.ytdl.core.plugins

object NodeJS : PluginBase() {
    override val executableName: String get() = "node"
    override val pluginFolderName: String get() = "node"
    override val bundledZipName: String get() = "libnode.zip.so"
    override val bundledVersion: String get() = "v25.3.0"
    override val githubRepositoryPackageURL: String  get() = ""
}