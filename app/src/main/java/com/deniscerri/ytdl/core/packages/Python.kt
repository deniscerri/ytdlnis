package com.deniscerri.ytdl.core.packages

import com.deniscerri.ytdl.BuildConfig

object Python : PackageBase() {
    override val executableName: String = "python"
    override val packageFolderName: String = "python"
    override val bundledZipName: String = "libpython.zip.so"
    override val bundledVersion: String = if (BuildConfig.FLAVOR == "izzy") "v3.12.11" else "v3.14.6"
    override val canUninstall: Boolean = false
    override val githubRepo: String  = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  = "python"
    override val apkPackage: String = "com.deniscerri.ytdl.python"
}