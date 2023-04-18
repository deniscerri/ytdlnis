package com.deniscerri.ytdlnis.ui.more

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.work.TerminalDownloadWorker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates


class TerminalActivity : AppCompatActivity() {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var notificationUtil: NotificationUtil
    private var output: TextView? = null
    private var input: EditText? = null
    private var fab: ExtendedFloatingActionButton? = null
    private var scrollView: ScrollView? = null
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var downloadID by Delegates.notNull<Int>()
    private lateinit var downloadFile : File
    private lateinit var observer: FileObserver
    private lateinit var imm : InputMethodManager
    private lateinit var uiUtil: UiUtil
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        downloadID = System.currentTimeMillis().toInt() % 100000
        downloadFile = File(cacheDir.absolutePath + "/$downloadID.txt")
        if (! downloadFile.exists()) downloadFile.createNewFile()
        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        uiUtil = UiUtil(FileUtil())

        context = baseContext
        scrollView = findViewById(R.id.custom_command_scrollview)
        topAppBar = findViewById(R.id.custom_command_toolbar)
        topAppBar!!.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        sharedPreferences = getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)


        bottomAppBar = findViewById(R.id.bottomAppBar)
        lifecycleScope.launch {
            val templateCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalNumber()
            }
            if (templateCount == 0) bottomAppBar.menu.getItem(0).isEnabled = false

            val shortcutCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalShortcutNumber()
            }
            if (shortcutCount == 0) bottomAppBar.menu.getItem(1).isEnabled = false
        }
        bottomAppBar.setOnMenuItemClickListener {
            when(it.itemId){
                R.id.command_templates -> {
                    lifecycleScope.launch {
                        uiUtil.showCommandTemplates(this@TerminalActivity, commandTemplateViewModel){ template ->
                            input!!.text.insert(input!!.selectionStart, template.content + " ")
                            input!!.postDelayed({
                                input!!.requestFocus()
                                imm.showSoftInput(input!!, 0)
                            }, 200)
                        }
                    }
                }
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
                downloadFile.appendText("~ $ ${input!!.text}\n")
                imm.hideSoftInputFromWindow(input?.windowToken, 0)
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

        if(Build.VERSION.SDK_INT < 29){
            observer = object : FileObserver(downloadFile.absolutePath, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    runOnUiThread{
                        try {
                            val newText = downloadFile.readText()
                            output!!.text = newText
                            output!!.scrollTo(0, output!!.height)
                            scrollView!!.fullScroll(View.FOCUS_DOWN)
                        }catch (ignored: Exception) {}
                    }
                }
            }
            observer.startWatching()
        }else{
            observer = object : FileObserver(downloadFile, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    runOnUiThread{
                        try {
                            val newText = downloadFile.readText()
                            output!!.text = newText
                            output!!.scrollTo(0, output!!.height)
                            scrollView!!.fullScroll(View.FOCUS_DOWN)
                        }catch (ignored: Exception) {}
                    }
                }
            }
            observer.startWatching()
        }

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(downloadID.toString())
            .observe(this){ list ->
                list.forEach {work ->
                    if (work.state == WorkInfo.State.SUCCEEDED || work.state == WorkInfo.State.FAILED || work.state == WorkInfo.State.CANCELLED) {
                        input!!.setText("yt-dlp ")
                        input!!.visibility = View.VISIBLE
                        input!!.requestFocus()
                        input!!.setSelection(input!!.text.length)
                        hideCancelFab()
                    }
                }
            }
        initMenu()
    }

    private fun initMenu() {
        topAppBar?.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.export_clipboard) {
                lifecycleScope.launch(Dispatchers.IO){
                    val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setText(output?.text)
                }
            }
            true
        }
    }

    override fun onDestroy() {
        downloadFile.delete()
        super.onDestroy()
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

        val workRequest = OneTimeWorkRequestBuilder<TerminalDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putInt("id", downloadID)
                    .putString("command", cmd)
                    .build()
            )
            .addTag("terminal")
            .build()

        WorkManager.getInstance(this).beginUniqueWork(
            downloadID.toString(),
            ExistingWorkPolicy.KEEP,
            workRequest
        ).enqueue()
    }

    private fun cancelDownload() {
        YoutubeDL.getInstance().destroyProcessById(downloadID.toString())
        WorkManager.getInstance(this).cancelUniqueWork(downloadID.toString())
        notificationUtil.cancelDownloadNotification(downloadID)
    }

    companion object {
        private const val TAG = "CustomCommandActivity"
    }
}