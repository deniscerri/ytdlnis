package com.deniscerri.ytdl.core.packages

object QuickJS : PackageBase() {
    override val executableName: String get() = "qjs"
    override val packageFolderName: String get() = "quickjs"
    override val bundledZipName: String get() = "libqjs.zip.so"
    override val bundledVersion: String get() = "2025-04-26"
    override val canUninstall: Boolean = false
    override val githubRepo: String  get() = ""
    override val githubPackageName: String  get() = ""
}