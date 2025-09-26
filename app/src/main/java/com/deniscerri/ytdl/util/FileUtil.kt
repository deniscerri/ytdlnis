package com.deniscerri.ytdl.util

import android.R.attr.mimeType
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.callback.FolderCallback
import com.anggrayudi.storage.file.copyFolderTo
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.moveFileTo
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.Extensions.getMediaDuration
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import okhttp3.internal.format
import okhttp3.internal.lowercase
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
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
            deleteFileFromMediaStore(path)
        }
    }

    private fun deleteFileFromMediaStore(path: String) {
        val contentResolver = App.instance.contentResolver
        val file = File(path)
        val uri = MediaStore.Files.getContentUri("external")

        val selection: String
        val selectionArgs: Array<String>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val parentPath = file.parentFile?.absolutePath.orEmpty()
            val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
            if (parentPath.startsWith(primaryRoot)) {
                val trimmed = parentPath
                    .removePrefix(primaryRoot)
                    .removePrefix(File.separator)
                val relativePath = if (trimmed.isEmpty()) "" else "$trimmed${File.separator}"
                selection = MediaStore.MediaColumns.RELATIVE_PATH + " =? AND " +
                            MediaStore.MediaColumns.DISPLAY_NAME + " =?"
                selectionArgs = arrayOf(relativePath, file.name)
            } else {
                // Non-primary storage: fall back to DATA query
                selection = MediaStore.MediaColumns.DATA + " =?"
                selectionArgs = arrayOf(file.absolutePath)
            }
        } else {
            selection = MediaStore.MediaColumns.DATA + " =?"
            selectionArgs = arrayOf(file.absolutePath)
        }
        contentResolver.delete(uri, selection, selectionArgs)
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
        dataValue = dataValue.replace("raw:/storage/", "")
        dataValue = dataValue.replace("^/document/".toRegex(), "")
        dataValue = dataValue.replace("^primary:".toRegex(), "primary/")
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
                    if (
                        it.name.matches("(^config.*.\\.txt\$)|(rList)|(.*.part-Frag.*)|(.*.live_chat)|(.*.ytdl)".toRegex())
                        || it.length() == 0L
                        ){
                        return@forEach
                    }

                    runCatching {
                        if (File(formatPath(destDir)).canWrite()){
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

                                    ff.copyTo(File(newFileName),false)
                                    ff.delete()
                                    fileList.add(newFileName)
                                }
                            }
                            return@forEach
                        }
                    }

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
                                            val destUri = moveFileInputStream(it, context, dst) ?: return@forEach
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
                            curr.moveFileTo(context, dst!!, callback = object : FileCallback() {
                                override fun onFailed(errorCode: ErrorCode) {
                                    //if its usb?
                                    runCatching {
                                        val destUri = moveFileInputStream(it, context, dst) ?: return
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
                }

            }
            if (!keepCache){
                originDir.deleteRecursively()
            }
            return@withContext scanMedia(fileList, context)
        }
    }

    private fun moveFileInputStream(it: File, context: Context, dst: DocumentFile) : Uri? {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

        val destUri = DocumentsContract.createDocument(
            context.contentResolver,
            dst.uri,
            mimeType,
            it.name
        ) ?: return null

        val inputStream = it.inputStream()
        val outputStream =
            context.contentResolver.openOutputStream(destUri) ?: return null
        inputStream.copyTo(outputStream)
        inputStream.closeQuietly()
        outputStream.closeQuietly()

        return destUri
    }

    fun scanMedia(files: List<String>, context: Context) : List<String> {
        try {
            val paths = files.distinct().sortedByDescending { File(it).length() }
            runCatching {
                paths.forEach {
                    MediaScannerConnection.scanFile(context, arrayOf(it), null, null)
                }
            }
            return paths.sortedBy { File(it).lastModified() }
        }catch (e: Exception){
            e.printStackTrace()
        }

        return listOf()
    }

    fun getBackupPath(context: Context) : String {
        val preference = PreferenceManager.getDefaultSharedPreferences(context).getString("backup_path", "")
        return if (preference.isNullOrBlank() || !File(formatPath(preference)).canWrite()) {
            getDefaultApplicationPath() + "/Backups"
        }else {
            formatPath(preference)
        }
    }

    fun getCachePath(context: Context) : String {
        val preference = PreferenceManager.getDefaultSharedPreferences(context).getString("cache_path", "")
        if (preference.isNullOrBlank() || !File(formatPath(preference)).canWrite()) {
            val externalPath = context.getExternalFilesDir(null)
            return if (externalPath == null){
                context.cacheDir.absolutePath + "/downloads/"
            }else{
                externalPath.absolutePath + "/downloads/"
            }
        }else {
            return formatPath(preference)
        }
    }

    fun deleteConfigFiles(request: YoutubeDLRequest) {
        runCatching {
            request.getArguments("--config")?.forEach {
                if (it != null) File(it).delete()
            }
            request.getArguments("--config-locations")?.forEach {
                if (it != null) File(it).delete()
            }
            request.getArguments("-o")?.firstOrNull { it?.startsWith("infojson:") == true }?.apply {
                File(this.removePrefix("infojson:")).delete()
            }
        }
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

    fun getDefaultApplicationPath() : String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator + "YTDLnis"
    }

    fun getDownloadArchivePath(context: Context) : String {
        var folder = PreferenceManager.getDefaultSharedPreferences(context).getString("download_archive_path", "")!!
        if (folder == "") {
            val externalPath = context.getExternalFilesDir(null)
            folder =  if (externalPath == null){
                context.cacheDir.absolutePath + File.separator
            }else{
                externalPath.absolutePath + File.separator
            }
        }
        return "${formatPath(folder)}download_archive.txt"
    }

    fun getDefaultTerminalPath() : String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator + "YTDLnis/TERMINAL_CACHE"
    }

    fun getCookieFile(context : Context, ignoreIfExists: Boolean = false,  path: (path: String) -> Unit){
        val cookiesFile = File(context.cacheDir, "cookies.txt")
        if (ignoreIfExists || cookiesFile.exists()){
            path(cookiesFile.absolutePath)
        }
    }

    fun convertFileSize(s: Long): String{
        if (s <= 1) return "?"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (log10(s.toDouble()) / log10(1024.0)).toInt()
        val symbols = DecimalFormatSymbols(Locale.US)
        return "${DecimalFormat("#,##0.#", symbols).format(s / 1024.0.pow(digitGroups.toDouble()))} ${units[digitGroups]}"
    }


    fun openFileIntent(context: Context, downloadPath: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(downloadPath))
        println(uri)

        if (uri == null){
            Toast.makeText(context, "Error opening file!", Toast.LENGTH_SHORT).show()
        }else{
            val ext = downloadPath.substring(downloadPath.lastIndexOf(".") + 1).lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)

            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            context.startActivity(intent)
        }

    }

    fun shareFileIntent(context: Context, paths: List<String>){
        val uris : ArrayList<Uri> = arrayListOf()
        paths.runCatching {
            this.forEach {path ->
                val uri = DocumentFile.fromSingleUri(context, Uri.parse(path)).run{
                    if (this?.exists() == true){
                        this.uri
                    }else if (File(path).exists()){
                        FileProvider.getUriForFile(context, context.packageName + ".fileprovider",
                            File(path))
                    }else null
                }
                if (uri != null) uris.add(uri)
            }

        }

        if (uris.isEmpty()){
            Toast.makeText(context, "Error sharing files!", Toast.LENGTH_SHORT).show()
        }else{
            Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = if (uris.size == 1) uris[0].let { context.contentResolver.getType(it) } ?: "media/*" else "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }.run{
                context.startActivity(Intent.createChooser(this, context.getString(R.string.share)))
            }
        }
    }

    fun hasAllFilesAccess() : Boolean {
        if (Build.VERSION.SDK_INT < 30) return true
        return Environment.isExternalStorageManager()
    }

}