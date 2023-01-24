package com.deniscerri.ytdlnis.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.service.IDownloaderService
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class CustomCommandActivity : AppCompatActivity() {
    private var topAppBar: MaterialToolbar? = null
    private var isDownloadServiceRunning = false
    private var output: TextView? = null
    private var input: EditText? = null
    private var fab: ExtendedFloatingActionButton? = null
    private var cancelFab: ExtendedFloatingActionButton? = null
    private var iDownloaderService: IDownloaderService? = null
    private var scrollView: ScrollView? = null
    var context: Context? = null
//    private val serviceConnection: ServiceConnection = object : ServiceConnection {
//        override fun onServiceConnected(className: ComponentName, service: IBinder) {
//            downloaderService = (service as LocalBinder).service
//            iDownloaderService = service
//            isDownloadServiceRunning = true
//            try {
//                val listeners = ArrayList<IDownloaderListener>()
//                listeners.add(listener)
//                iDownloaderService!!.addActivity(this@CustomCommandActivity, listeners)
//                listener.onDownloadStart(iDownloaderService!!.info)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//
//        override fun onServiceDisconnected(componentName: ComponentName) {
//            downloaderService = null
//            iDownloaderService = null
//            isDownloadServiceRunning = false
//        }
//    }
//    var listener: IDownloaderListener = object : IDownloaderListener {
//        override fun onDownloadStart(info: DownloadInfo) {
//            input!!.isEnabled = false
//            output!!.text = ""
//            swapFabs()
//        }
//
//        override fun onDownloadProgress(info: DownloadInfo) {
//            val newInfo = info.outputLine
//            if (newInfo.contains("[download]")) {
//                val temp = output!!.text.toString()
//                output!!.text =
//                    temp.substring(0, temp.lastIndexOf(System.getProperty("line.separator")!!) - 2)
//            }
//            output!!.append(
//                """${info.outputLine}
//"""
//            )
//            output!!.scrollTo(0, output!!.height)
//            scrollView!!.fullScroll(View.FOCUS_DOWN)
//        }
//
//        @SuppressLint("SetTextI18n")
//        override fun onDownloadError(info: DownloadInfo) {
//            output!!.append(
//                """
//
//    ${info.outputLine}
//    """.trimIndent()
//            )
//            scrollView!!.scrollTo(0, scrollView!!.maxScrollAmount)
//            input!!.setText("yt-dlp ")
//            input!!.isEnabled = true
//            swapFabs()
//        }
//
//        @SuppressLint("SetTextI18n")
//        override fun onDownloadEnd(info: DownloadInfo) {
//            output!!.append(info.outputLine)
//            scrollView!!.scrollTo(0, scrollView!!.maxScrollAmount)
//            // MEDIA SCAN
//            MediaScannerConnection.scanFile(context, arrayOf("/storage"), null, null)
//            input!!.setText("yt-dlp ")
//            input!!.isEnabled = true
//            swapFabs()
//        }
//
//        override fun onDownloadCancel(downloadInfo: DownloadInfo) {}
//        override fun onDownloadCancelAll(downloadInfo: DownloadInfo) {}
//        override fun onDownloadServiceEnd() {
//            stopDownloadService()
//        }
//    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_command)
        context = baseContext
        scrollView = findViewById(R.id.custom_command_scrollview)
        topAppBar = findViewById(R.id.custom_command_toolbar)
        topAppBar!!.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        output = findViewById(R.id.custom_command_output)
        output!!.setTextIsSelectable(true)
        input = findViewById(R.id.command_edittext)
        input!!.requestFocus()
        fab = findViewById(R.id.command_fab)
        fab!!.setOnClickListener {
            if (isStoragePermissionGranted) {
                startDownloadService(
                    input!!.text.toString(),
                    NotificationUtil.COMMAND_DOWNLOAD_NOTIFICATION_ID
                )
            }
        }
        cancelFab = findViewById(R.id.cancel_command_fab)
        cancelFab!!.setOnClickListener {
            cancelDownloadService()
            swapFabs()
            input!!.isEnabled = true
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type
        Log.e(TAG, "$action $type")
        if (action == Intent.ACTION_SEND && type != null) {
            Log.e(TAG, action)
            val txt = "yt-dlp " + intent.getStringExtra(Intent.EXTRA_TEXT)
            input!!.setText(txt)
        }
    }

    private fun swapFabs() {
        val cancel = cancelFab!!.visibility
        val start = fab!!.visibility
        cancelFab!!.visibility = start
        fab!!.visibility = cancel
    }


    private fun startDownloadService(command: String?, id: Int) {
        if (isDownloadServiceRunning) return
        //val serviceIntent = Intent(context, DownloaderService::class.java)
//        serviceIntent.putExtra("command", command)
//        serviceIntent.putExtra("id", id)
        //context!!.applicationContext.bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    fun stopDownloadService() {
//        if (!isDownloadServiceRunning) return
//        iDownloaderService!!.removeActivity(this)
//        context!!.applicationContext.unbindService(serviceConnection)
//        downloaderService!!.stopForeground(true)
//        downloaderService!!.stopSelf()
//        isDownloadServiceRunning = false
    }

    fun cancelDownloadService() {
        if (!isDownloadServiceRunning) return
        iDownloaderService!!.cancelDownload(false)
        stopDownloadService()
    }

    private val isStoragePermissionGranted: Boolean
        get() = if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
            false
        }

    companion object {
        private const val TAG = "CustomCommandActivity"
    }
}