package com.deniscerri.ytdlnis.ui

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import java.io.File
import java.util.regex.Pattern


class CustomCommandActivity : AppCompatActivity() {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var notificationUtil: NotificationUtil
    private var output: TextView? = null
    private var input: EditText? = null
    private var fab: ExtendedFloatingActionButton? = null
    private var cancelFab: ExtendedFloatingActionButton? = null
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
            input!!.isEnabled = false
            output!!.text = ""
            swapFabs()
            startDownload(
                input!!.text.toString()
            )
        }
        cancelFab = findViewById(R.id.cancel_command_fab)
        cancelFab!!.setOnClickListener {
            cancelDownload()
            input!!.isEnabled = true
        }
        notificationUtil = NotificationUtil(this)
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


    private fun startDownload(command: String?) {
        var cmd: String = ""
        if (command!!.contains("yt-dlp")) cmd = command.replace("yt-dlp", "")
        else cmd = command
        cmd = cmd.trim()

        downloadID = System.currentTimeMillis().toInt()

        val theIntent = Intent(this, CustomCommandActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, theIntent, PendingIntent.FLAG_IMMUTABLE)

        val commandNotification: Notification =
            notificationUtil.createDownloadServiceNotification(
                pendingIntent,
                getString(R.string.terminal),
                downloadID,
                NotificationUtil.COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID
            )
        with(NotificationManagerCompat.from(this)){
            if (ActivityCompat.checkSelfPermission(
                    context!!,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(downloadID, commandNotification)
            }
        }
        val commandRegex = "(-\\S+)|(\\S+)"
        val request = YoutubeDLRequest(emptyList())

        val tempFolder = StringBuilder(context!!.cacheDir.absolutePath + """/${command}##terminal""")
        val tempFileDir = File(tempFolder.toString())
        tempFileDir.delete()
        tempFileDir.mkdir()

        Log.e(TAG, cmd)
        val m = Pattern.compile(commandRegex).matcher(cmd)
        while (m.find()) {
            if (m.group(1) != null) {
                request.addOption(m.group(1)!!)
            } else {
                request.addOption(m.group(2)!!)
            }
        }

        request.addOption("-P", tempFileDir.absolutePath)

        cancelFab!!.visibility = View.VISIBLE
        fab!!.visibility = View.GONE
        val disposable: Disposable = Observable.fromCallable {
            try{
                YoutubeDL.getInstance().execute(request, downloadID.toString()){ progress, _, line ->
                    Log.e(TAG, line)
                    runOnUiThread {
                        output!!.append("\n" + line)
                        output!!.scrollTo(0, output!!.height)
                        scrollView!!.fullScroll(View.FOCUS_DOWN)
                    }

                    val title: String = getString(R.string.terminal)
                    notificationUtil.updateDownloadNotification(
                        downloadID,
                        line, progress.toInt(), 0, title,
                        NotificationUtil.COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID
                    )
                }
            }catch (e: Exception){
                e.printStackTrace()
                runOnUiThread {
                    output!!.append("\n" + e.message)
                    output!!.scrollTo(0, output!!.height)
                    scrollView!!.fullScroll(View.FOCUS_DOWN)

                    input!!.isEnabled = true

                    cancelFab!!.visibility = View.GONE
                    fab!!.visibility = View.VISIBLE
                }
            }

        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                scrollView!!.scrollTo(0, scrollView!!.maxScrollAmount);
                input!!.setText("yt-dlp ");
                input!!.isEnabled = true;

                cancelFab!!.visibility = View.GONE
                fab!!.visibility = View.VISIBLE

                //move file from internal to set download directory
                try {
                    moveFile(tempFileDir.absoluteFile, getString(R.string.command_path)){ }
                }catch (e: Exception){
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
                notificationUtil.cancelDownloadNotification(downloadID)
            }) { e ->
                e.printStackTrace()
                scrollView!!.scrollTo(0, scrollView!!.maxScrollAmount)
                input!!.setText("yt-dlp ")
                input!!.isEnabled = true

                cancelFab!!.visibility = View.GONE
                fab!!.visibility = View.VISIBLE

                tempFileDir.delete()
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()

                Log.e(DownloadWorker.TAG, context?.getString(R.string.failed_download), e)
                notificationUtil.cancelDownloadNotification(downloadID)
            }
        compositeDisposable.add(disposable)

    }
    @Throws(Exception::class)
    private fun moveFile(originDir: File, downLocation: String, progress: (progress: Int) -> Unit){
        val fileUtil = FileUtil()
        fileUtil.moveFile(originDir, context!!, downLocation){ p ->
            progress(p)
        }
    }

    private fun cancelDownload() {
        lifecycleScope.launch {
            compositeDisposable.dispose()
            YoutubeDL.getInstance().destroyProcessById(downloadID.toString())
            notificationUtil.cancelDownloadNotification(downloadID)
        }
    }

    companion object {
        private const val TAG = "CustomCommandActivity"
        private var downloadID = System.currentTimeMillis().toInt()
        private val compositeDisposable = CompositeDisposable()

    }
}