package com.deniscerri.ytdlnis.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import okhttp3.internal.closeQuietly
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow


object FileUtil {
    fun deleteFile(path: String){
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    internal object Compare {
        fun max(a: File, b: File): File {
            return if (a.length() > b.length()) a else b
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
        val formattedPath = StringBuilder("/storage/")
        if (pieces[0] == "primary"){
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

    private fun scanMedia(files: List<File>, context: Context) : String {

        try {
            val paths = files.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, paths, null, null)
            return files.reduce(Compare::max).absolutePath
        }catch (e: Exception){
            e.printStackTrace()
        }

        return context.getString(R.string.unfound_file);
    }
    @Throws(Exception::class)
     fun moveFile(originDir: File, context: Context, destDir: String, keepCache: Boolean, progress: (p: Int) -> Unit) : String {
        val fileList = mutableListOf<File>()
        val dir = File(formatPath(destDir))
        if (!dir.exists()) dir.mkdirs()
        originDir.listFiles()?.forEach {
            Log.e("zz", it.name)
            val destFile = File(dir.absolutePath + "/${it.name}")

            try {
                if (it.name.matches("(^config.*.\\.txt\$)|(rList)|(part-Frag)".toRegex())){
                    it.delete()
                    return@forEach
                }

                if(it.name.contains(".part-Frag")) return@forEach

                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                val dest = Uri.parse(destDir).run {
                    DocumentsContract.buildDocumentUriUsingTree(
                        this,
                        DocumentsContract.getTreeDocumentId(this)
                    )
                }
                val destUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    dest,
                    mimeType,
                    it.name
                ) ?: return@forEach

                val inputStream = it.inputStream()
                val outputStream =
                    context.contentResolver.openOutputStream(destUri) ?: return@forEach
                inputStream.copyTo(outputStream)
                inputStream.closeQuietly()
                outputStream.closeQuietly()

                fileList.add(destFile)
            }catch (e: java.lang.Exception) {

                if (destFile.absolutePath.contains("/storage/emulated/0/Download")
                    || destFile.absolutePath.contains("/storage/emulated/0/Documents")
                ){
                    if (Build.VERSION.SDK_INT >= 26 ){
                        Files.move(it.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }else{
                        it.renameTo(destFile)
                    }
                    fileList.add(destFile)
                }
                return@forEach
            }catch(e : Exception){
                return@forEach
            }

        }
        if (!keepCache){
            originDir.deleteRecursively()
        }
        return scanMedia(fileList, context)
    }

    fun getLogFile(context: Context, item: DownloadItem) : File {
        val titleRegex = Regex("[^A-Za-z\\d ]")
        val title = item.title.ifEmpty { item.url }
        return File(context.filesDir.absolutePath + """/logs/${item.id} - ${titleRegex.replace(title, "").take(150)}##${item.type}##${item.format.format_id}.log""")
    }
    fun getLogFileForTerminal(context: Context, command: String) : File {
        val titleRegex = Regex("[^A-Za-z\\d ]")
        return File(context.filesDir.absolutePath + """/logs/Terminal - ${titleRegex.replace(command.take(30), "")}##terminal.log""")
    }

    fun getDefautAudioPath() : String{
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + File.separator + "YTDLnis/Audio"
    }

    fun getDefautVideoPath() : String{
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + File.separator + "YTDLnis/Video"
    }

    fun getDefaultCommandPath() : String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + File.separator + "YTDLnis/Command"
    }

    fun convertFileSize(s: Long): String{
        if (s <= 0) return "?"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (log10(s.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(s / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }
}