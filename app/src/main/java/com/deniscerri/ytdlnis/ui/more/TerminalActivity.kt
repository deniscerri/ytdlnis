package com.deniscerri.ytdlnis.ui.more

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class TerminalActivity : AppCompatActivity() {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var notificationUtil: NotificationUtil
    private var output: TextView? = null
    private var input: EditText? = null
    private var fab: ExtendedFloatingActionButton? = null
    private var scrollView: ScrollView? = null
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        context = baseContext
        scrollView = findViewById(R.id.custom_command_scrollview)
        topAppBar = findViewById(R.id.custom_command_toolbar)
        topAppBar!!.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]


        bottomAppBar = findViewById(R.id.bottomAppBar)
        bottomAppBar.setOnMenuItemClickListener {
            when(it.itemId){
                R.id.command_templates -> showCommandTemplates()
                R.id.shortcuts -> showShortcuts()
                else -> {}
            }
            true
        }

        output = findViewById(R.id.custom_command_output)
        output!!.setTextIsSelectable(true)
        input = findViewById(R.id.command_edittext)
        input!!.requestFocus()
        fab = findViewById(R.id.command_fab)
        fab!!.setOnClickListener {
            if (fab!!.text == getString(R.string.run_command)){
                input!!.visibility = View.GONE
                output!!.text = "${output!!.text}\n~ $ ${input!!.text}\n"
                showCancelFab()
                startDownload(
                    input!!.text.toString()
                )
            }else {
                cancelDownload()
                input!!.visibility = View.VISIBLE
                hideCancelFab()
            }
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


    private fun hideCancelFab() {
        fab!!.text = getString(R.string.run_command)
        fab!!.setIconResource(R.drawable.ic_baseline_keyboard_arrow_right_24)
    }
    private fun showCancelFab() {
        fab!!.text = getString(R.string.cancel_download)
        fab!!.setIconResource(R.drawable.ic_cancel)
    }

    private fun showCommandTemplates(){
        lifecycleScope.launch {
            val bottomSheet = BottomSheetDialog(this@TerminalActivity)
            bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            bottomSheet.setContentView(R.layout.command_template_list)

            val linearLayout = bottomSheet.findViewById<LinearLayout>(R.id.command_list_linear_layout)
            val list = withContext(Dispatchers.IO){
                commandTemplateViewModel.getAll()
            }

            linearLayout!!.removeAllViews()
            list.forEach {template ->
                val item = layoutInflater.inflate(R.layout.command_template_item, linearLayout, false) as ConstraintLayout
                item.findViewById<TextView>(R.id.title).text = template.title
                item.findViewById<TextView>(R.id.content).text = template.content
                item.setOnClickListener {
                    input!!.text.insert(input!!.selectionStart, template.content + " ")
                }
                linearLayout.addView(item)
            }

            bottomSheet.show()
            bottomSheet.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun showShortcuts() {
        lifecycleScope.launch {
            val bottomSheet = BottomSheetDialog(this@TerminalActivity)
            bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            bottomSheet.setContentView(R.layout.template_shortcuts_list)

            val chipGroup = bottomSheet.findViewById<ChipGroup>(R.id.shortcutsChipGroup)
            val shortcutList = withContext(Dispatchers.IO){
                commandTemplateViewModel.getAllShortcuts()
            }

            chipGroup!!.removeAllViews()
            shortcutList.forEach {shortcut ->
                val chip = layoutInflater.inflate(R.layout.suggestion_chip, chipGroup, false) as Chip
                chip.text = shortcut.content
                chip.setOnClickListener {
                    input!!.text.insert(input!!.selectionStart, shortcut.content + " ")
                }
                chipGroup.addView(chip)
            }

            bottomSheet.show()
            bottomSheet.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun startDownload(command: String?) {
        val cmd = if (command!!.contains("yt-dlp")) command.replace("yt-dlp", "")
        else command

        downloadID = System.currentTimeMillis().toInt()

        val theIntent = Intent(this, TerminalActivity::class.java)
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
        val request = YoutubeDLRequest(emptyList())

        val tempFolder = StringBuilder(context!!.cacheDir.absolutePath + """/${System.currentTimeMillis()}##terminal""")
        val tempFileDir = File(tempFolder.toString())
        tempFileDir.delete()
        tempFileDir.mkdir()

        request.addOption(
            "--config-locations",
            File(cacheDir, "config${System.currentTimeMillis()}.txt").apply {
                writeText(cmd)
            }.absolutePath
        )


        request.addOption("-P", tempFileDir.absolutePath)

        showCancelFab()
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

                    input!!.visibility = View.VISIBLE

                    hideCancelFab()
                }
            }

        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                output!!.scrollTo(0, output!!.height)
                scrollView!!.fullScroll(View.FOCUS_DOWN)
                input!!.setText("yt-dlp ")
                input!!.visibility = View.VISIBLE

                hideCancelFab()
                notificationUtil.cancelDownloadNotification(downloadID)
            }) { e ->
                e.printStackTrace()
                output!!.scrollTo(0, output!!.height)
                scrollView!!.fullScroll(View.FOCUS_DOWN)
                input!!.setText("yt-dlp ")
                input!!.visibility = View.VISIBLE

                hideCancelFab();

                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                Log.e(DownloadWorker.TAG, context?.getString(R.string.failed_download), e)
                notificationUtil.cancelDownloadNotification(downloadID)
            }
        compositeDisposable.add(disposable)
    }

    private fun cancelDownload() {
        YoutubeDL.getInstance().destroyProcessById(downloadID.toString())
        WorkManager.getInstance(this).cancelUniqueWork(downloadID.toString())
        notificationUtil.cancelDownloadNotification(downloadID)
    }

    companion object {
        private const val TAG = "CustomCommandActivity"
        private var downloadID = System.currentTimeMillis().toInt()
        private val compositeDisposable = CompositeDisposable()

    }
}