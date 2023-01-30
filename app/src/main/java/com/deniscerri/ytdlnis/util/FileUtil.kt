package com.deniscerri.ytdlnis.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

class FileUtil() {

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

    fun formatPath(path: String) : String {
        var dataValue = path
        if (dataValue.startsWith("/storage/")) return dataValue
        dataValue = dataValue.replace("content://com.android.externalstorage.documents/tree/", "")
        dataValue = dataValue.replace("%3A".toRegex(), "/")
        try {
            dataValue = URLDecoder.decode(dataValue, StandardCharsets.UTF_8.name())
        } catch (ignored: Exception) {
        }
        val pieces = dataValue.split("/").toTypedArray()
        var formattedPath = StringBuilder("/storage/")
        if (pieces[0].equals("primary")){
            formattedPath.append("emulated/0/")
        }else{
            formattedPath.append(pieces[0]).append("/")
        }
        pieces.forEachIndexed { i, it ->
            if (i > 0 && it.isNotEmpty()){
                formattedPath.append(it).append("/")
            }
        }
        return formattedPath.toString()
    }

    private fun scanMedia(destDir: String, context: Context) : String {
        val files = ArrayList<File>()
        val path = File(formatPath(destDir))

        try {
            for (file in path.listFiles()!!) {
                if (file.isFile) {
                    files.add(file)
                }
            }

            val paths = arrayOfNulls<String>(files.size)
            for (i in files.indices) paths[i] = files[i].absolutePath
            MediaScannerConnection.scanFile(context, paths, null, null)
            return paths[0]!!
        }catch (e: Exception){
            e.printStackTrace()
        }

        return context.getString(R.string.unfound_file);
    }
     fun moveFile(originDir: File, context: Context, destDir: String, progress: (p: Int) -> Unit) : String {
        originDir.listFiles()?.forEach {
            if (it.name.equals("rList")){
                it.delete()
                return@forEach
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                val f = File(formatPath(destDir)+"/"+it.name)
                if (!f.exists()) f.mkdir()
                Files.move(it.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
                progress(100)
            }else{
                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                val destination = Uri.parse(destDir).run {
                    DocumentsContract.buildChildDocumentsUriUsingTree(this, DocumentsContract.getTreeDocumentId(this))
                }

                val destUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    destination,
                    mimeType,
                    it.name
                ) ?: return@forEach

                val inputStream = it.inputStream()
                val outputStream = context.contentResolver.openOutputStream(destUri) ?: return@forEach
                val fileLength = it.length()
                var bytesCopied: Long = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytes = inputStream.read(buffer)

                try {
                    while (bytes >= 0) {
                        outputStream.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        progress((bytesCopied * 100 / fileLength).toInt())
                        bytes = inputStream.read(buffer)
                    }
                }catch (e : Exception){
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }

                inputStream.close()
                outputStream.close()

                it.delete()
            }
        }
         originDir.delete()
         return scanMedia(destDir, context)
    }

    fun convertFileSize(s: Long): String{
        if (s <= 0) return "?"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (log10(s.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(s / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }
}