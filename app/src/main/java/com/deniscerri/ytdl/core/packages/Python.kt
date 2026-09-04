package com.deniscerri.ytdl.core.packages

import com.deniscerri.ytdl.BuildConfig

object Python : PackageBase() {
    override val executableName: String get() = "python"
    override val packageFolderName: String get() = "python"
    override val bundledZipName: String get() = "libpython.zip.so"
    override val bundledVersion: String get() = if (BuildConfig.FLAVOR == "izzy") "v3.12.11" else "v3.14.6"
    override val canUninstall: Boolean = false
    override val githubRepo: String  get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  get() = "python"
    override val apkPackage: String get() = "com.deniscerri.ytdl.python"
}