package com.deniscerri.ytdlnis.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.service.IDownloaderService
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CustomCommandActivity : AppCompatActivity() {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var workManager: WorkManager
    private var isDownloadServiceRunning = false
    private var output: TextView? = null
    private var input: EditText? = null
    private var fab: ExtendedFloatingActionButton? = null
    private var cancelFab: ExtendedFloatingActionButton? = null
    private var iDownloaderService: IDownloaderService? = null
    private var scrollView: ScrollView? = null
    var context: Context? = null

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
            startDownload(
                input!!.text.toString()
            )
        }
        cancelFab = findViewById(R.id.cancel_command_fab)
        cancelFab!!.setOnClickListener {
            cancelDownload()
            input!!.isEnabled = true
        }
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        notificationUtil = NotificationUtil(this)
        workManager = WorkManager.getInstance(this)
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


    private fun startDownload(command: String?) {
        if (isDownloadServiceRunning) return
        downloadViewModel.startTerminalDownload(command!!)
        output!!.text = ""
        workManager.getWorkInfosByTagLiveData("terminal")
            .observe(this){ list ->
                list.forEach {
                    if(it.progress.getString("output") != null){
                        output?.append("\n" + it.progress.getString("output") + "\n")
                        output?.scrollTo(0, output!!.height)
                        scrollView?.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }

    }

    private fun cancelDownload() {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO){ downloadViewModel.getTerminalDownload(); }
            YoutubeDL.getInstance().destroyProcessById(id.toString())
            WorkManager.getInstance(this@CustomCommandActivity).cancelUniqueWork(id.toString())
            notificationUtil.cancelDownloadNotification(id.toInt())
        }
    }

    companion object {
        private const val TAG = "CustomCommandActivity"
    }
}