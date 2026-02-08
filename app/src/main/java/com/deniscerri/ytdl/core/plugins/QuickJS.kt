package com.deniscerri.ytdl.core.plugins

object QuickJS : PluginBase() {
    override val executableName: String get() = "qjs"
    override val pluginFolderName: String get() = "quickjs"
    override val bundledZipName: String get() = "libqjs.zip.so"
    override val bundledVersion: String get() = "2025-04-26"
    override val githubRepo: String  get() = ""
    override val githubPackageName: String  get() = ""
}