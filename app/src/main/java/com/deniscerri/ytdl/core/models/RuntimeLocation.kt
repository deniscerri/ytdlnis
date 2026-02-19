package com.deniscerri.ytdl.core.models

import java.io.File

data class RuntimeLocation(
    val binDir: File,
    val binDirExists: Boolean,
    val ldLibraryDir: File,
    val ldLibraryDirExists: Boolean,
    val isDownloaded: Boolean,
    val exePath: String?
)