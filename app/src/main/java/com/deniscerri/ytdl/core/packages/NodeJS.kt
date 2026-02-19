package com.deniscerri.ytdl.core.packages

object NodeJS : PackageBase() {
    override val executableName: String get() = "node"
    override val packageFolderName: String get() = "node"
    override val bundledZipName: String get() = "libnode.zip.so"
    override val canUninstall: Boolean = true
    override val bundledVersion: String get() = ""
    override val githubRepo: String  get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  get() = "nodejs"
    override val apkPackage: String get() = "com.deniscerri.ytdl.nodejs"
}