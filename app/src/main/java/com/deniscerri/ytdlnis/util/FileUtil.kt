package com.deniscerri.ytdlnis.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.callback.FolderCallback
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.file.copyFolderTo
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.getBasePath
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.math.log10
import kotlin.math.pow


object FileUtil {

    fun deleteFile(path: String){
        runCatching {
            if (!File(path).delete()){
                DocumentFile.fromSingleUri(App.instance, Uri.parse(path))?.delete()
            }
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


    @Throws(Exception::class)
     suspend fun moveFile(originDir: File, context: Context, destDir: String, keepCache: Boolean, progress: (p: Int) -> Unit) : List<String> {
        return withContext(Dispatchers.Main){
            val fileList = mutableListOf<String>()
            val dir = File(formatPath(destDir))
            if (!dir.exists()) dir.mkdirs()
            originDir.walk().forEach {
                if (it.isDirectory && it.absolutePath == originDir.absolutePath) return@forEach
                var destFile: DocumentFile
                try {
                    if (it.name.matches("(^config.*.\\.txt\$)|(rList)|(part-Frag)|(live_chat)".toRegex())){
                        it.delete()
                        return@forEach
                    }

                    if(it.name.contains(".part-Frag")) return@forEach


                    val curr = DocumentFile.fromFile(it)
                    val dst =  DocumentFile.fromTreeUri(context, destDir.toUri())

                    if (it.isDirectory){
                        withContext(Dispatchers.IO){
                            curr.copyFolderTo(context, dst!!, skipEmptyFiles = false, callback = object : FolderCallback() {
                                override fun onStart(folder: DocumentFile, totalFilesToCopy: Int, workerThread: Thread): Long {
                                    return 500 // update progress every half second
                                }

                                override fun onParentConflict(destinationFolder: DocumentFile, action: ParentFolderConflictAction, canMerge: Boolean) {
                                    if (canMerge){
                                        action.confirmResolution(ConflictResolution.MERGE)
                                    }else{
                                        action.confirmResolution(ConflictResolution.CREATE_NEW)
                                    }
                                }

                                override fun onReport(report: Report) {
                                    progress(report.progress.toInt())
                                }

                                override fun onCompleted(result: Result) {
                                    fileList.addAll(result.folder.listFiles().map { f -> f.getAbsolutePath(context) })
                                    it.deleteRecursively()
                                }

                                override fun onFailed(errorCode: ErrorCode) {
                                    //if its usb?
                                    runCatching {
                                        it.walkTopDown().forEach { f ->
                                            if (f.isDirectory) return@forEach

                                            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension) ?: "*/*"

                                            val destUri = DocumentsContract.createDocument(
                                                context.contentResolver,
                                                dst.uri,
                                                mimeType,
                                                f.name
                                            ) ?: return@runCatching

                                            val inputStream = f.inputStream()
                                            val outputStream =
                                                context.contentResolver.openOutputStream(destUri) ?: return@runCatching
                                            inputStream.copyTo(outputStream)
                                            inputStream.closeQuietly()
                                            outputStream.closeQuietly()

                                            fileList.add(DocumentFile.fromSingleUri(context, destUri)!!.getAbsolutePath(context))
                                        }

                                        it.deleteRecursively()
                                    }
                                    super.onFailed(errorCode)
                                }

                            })
                        }
                    }else{
                        withContext(Dispatchers.IO){
                            curr.copyFileTo(context, dst!!, callback = object : FileCallback() {
                                override fun onFailed(errorCode: ErrorCode) {
                                    //if its usb?
                                    runCatching {
                                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                                        val destUri = DocumentsContract.createDocument(
                                            context.contentResolver,
                                            dst.uri,
                                            mimeType,
                                            it.name
                                        ) ?: return@runCatching

                                        val inputStream = it.inputStream()
                                        val outputStream =
                                            context.contentResolver.openOutputStream(destUri) ?: return@runCatching
                                        inputStream.copyTo(outputStream)
                                        inputStream.closeQuietly()
                                        outputStream.closeQuietly()

                                        fileList.add(DocumentFile.fromSingleUri(context, destUri)!!.getAbsolutePath(context))
                                        it.delete()
                                    }
                                    super.onFailed(errorCode)
                                }

                                override fun onConflict(
                                    destinationFile: DocumentFile,
                                    action: FileConflictAction
                                ) {
                                    action.confirmResolution(ConflictResolution.CREATE_NEW)
                                }

                                override fun onStart(file: Any, workerThread: Thread): Long {
                                    return 500 // update progress every 1 second
                                }

                                override fun onReport(report: Report) {
                                    progress(report.progress.toInt())
                                }

                                override fun onCompleted(result: Any) {
                                    destFile = (result as DocumentFile)
                                    fileList.add(destFile.getAbsolutePath(context))
                                    it.delete()
                                    super.onCompleted(result)
                                }
                            })
                        }
                    }
                }catch (e: Exception) {
                    Log.e("error", e.message.toString())
                    val files = it.listFiles()?.filter { fil -> !fil.isDirectory }?.toTypedArray() ?: arrayOf(it)
                    for (ff in files){
                        val newFile =  File(dir.absolutePath + "/${ff.absolutePath.removePrefix(originDir.absolutePath)}")
                        runCatching {
                            newFile.parentFile?.mkdirs()
                        }
                        if (Build.VERSION.SDK_INT >= 26 ) {
                            var newFileName = newFile.absolutePath
                            var counter = 1
                            while (Files.exists(File(newFileName).toPath())) {
                                // If the file already exists in the destination directory, add a number to differentiate it
                                newFileName = newFile.absolutePath.replace(newFile.nameWithoutExtension, newFile.nameWithoutExtension+" ($counter)")
                                counter++
                            }

                            fileList.add(Files.move(
                                ff.toPath(),
                                File(newFileName).toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            ).absolutePathString())
                            ff.delete()
                            fileList.add(newFileName)
                        }else{
                            var newFileName = newFile.absolutePath
                            var counter = 1
                            while (File(newFileName).exists()) {
                                // If the file already exists in the destination directory, add a number to differentiate it
                                newFileName = newFile.absolutePath.replace(newFile.nameWithoutExtension, newFile.nameWithoutExtension+" ($counter)")
                                counter++
                            }

                            ff.renameTo(File(newFileName))
                            fileList.add(newFileName)
                        }
                    }
                    return@forEach
                }

            }
            if (!keepCache){
                originDir.deleteRecursively()
            }
            return@withContext scanMedia(fileList, context)
        }
    }
    private fun scanMedia(files: List<String>, context: Context) : List<String> {
        try {
            val paths = files.sortedByDescending { File(it).length() }
            runCatching {MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
            return paths
        }catch (e: Exception){
            e.printStackTrace()
        }

        return listOf(context.getString(R.string.unfound_file))
    }

    fun getCachePath() : String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator + "YTDLnis/.CACHE"
    }

    fun getDefaultAudioPath() : String{
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator + "YTDLnis/Audio"
    }

    fun getDefaultVideoPath() : String{
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator + "YTDLnis/Video"
    }

    fun getDefaultCommandPath() : String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator + "YTDLnis/Command"
    }

    fun getCookieFile(context : Context, path: (path: String) -> Unit){
        val cookiesFile = File(context.cacheDir, "cookies.txt")
        if (cookiesFile.exists()) path(cookiesFile.absolutePath)
    }

    fun convertFileSize(s: Long): String{
        if (s <= 0) return "?"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (log10(s.toDouble()) / log10(1024.0)).toInt()
        val symbols = DecimalFormatSymbols(Locale.US)
        return "${DecimalFormat("#,##0.#", symbols).format(s / 1024.0.pow(digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}