package com.deniscerri.ytdl.core.packages

object QuickJS : PackageBase() {
    override val executableName: String = "qjs"
    override val packageFolderName: String = "quickjs"
    override val bundledZipName: String = "libqjs.zip.so"
    override val bundledVersion: String = "2025-04-26"
    override val canUninstall: Boolean = false
    override val githubRepo: String  = ""
    override val githubPackageName: String  = ""
    override val apkPackage: String = ""
}