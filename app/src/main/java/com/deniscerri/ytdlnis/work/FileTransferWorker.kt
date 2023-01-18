package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import java.io.File

class FileTransferWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val originDir = File(inputData.getString(originDir)!!)
        val downLocation = inputData.getString(downLocation)
        val fileTitle = inputData.getString(title) ?: ""
        val destDir = Uri.parse(downLocation).run {
            DocumentsContract.buildChildDocumentsUriUsingTree(this, DocumentsContract.getTreeDocumentId(this))
        }

        val fileUtil = FileUtil()

        val notificationUtil = NotificationUtil(context)
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val title = "Moving $fileTitle to ${fileUtil.formatPath(downLocation!!)}"
        val id : Int = SystemClock.uptimeMillis().toInt()
        val notification = notificationUtil.createFileTransferNotification(pendingIntent, title)
        val foregroundInfo = ForegroundInfo(id, notification)
        setForeground(foregroundInfo)

        fileUtil.moveFile(originDir, context, destDir){ progress ->
            setProgressAsync(workDataOf("progress" to progress))
            notificationUtil.updateFileTransferNotification(id, progress)
        }

        return Result.success()
    }


    companion object {
        const val downLocation = "downLocation"
        const val originDir = "originDir"
        const val title = "title"
    }
}