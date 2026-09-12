package com.deniscerri.ytdl.core.packages

object Aria2c : PackageBase() {
    override val executableName: String = "aria2c"
    override val packageFolderName: String = "aria2c"
    override val bundledZipName: String = "libaria2c.zip.so"
    override val canUninstall: Boolean = false
    override val bundledVersion: String = "v1.37.0"
    override val githubRepo: String  = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  = "aria2c"
    override val apkPackage: String = "com.deniscerri.ytdl.aria2c"
}