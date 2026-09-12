package com.deniscerri.ytdl.core.packages

object Deno : PackageBase() {
    override val executableName: String = "deno"
    override val packageFolderName: String = "deno"
    override val bundledZipName: String = "libdeno.zip.so"
    override val canUninstall: Boolean = true
    override val bundledVersion: String = ""
    override val githubRepo: String  = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  = "deno"
    override val apkPackage: String = "com.deniscerri.ytdl.deno"
}