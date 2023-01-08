package com.deniscerri.ytdlnis.util

import java.io.File

class FileUtil {
    fun deleteFile(path: String){
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    fun exists(path: String) : Boolean {
        val file = File(path)
        if (path.isEmpty()) return false
        return file.exists()
    }
}