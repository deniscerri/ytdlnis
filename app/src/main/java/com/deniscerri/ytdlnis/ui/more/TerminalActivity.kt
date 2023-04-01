package com.deniscerri.ytdlnis.ui.more

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.work.TerminalDownloadWorker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.yausername.youtubedl_android.YoutubeDL
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        downloadID = System.currentTimeMillis().toInt() % 100000

        context = baseContext
        scrollView = findViewById(R.id.custom_command_scrollview)
        topAppBar = findViewById(R.id.custom_command_toolbar)
        topAppBar!!.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        sharedPreferences = getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)


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

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(downloadID.toString())
            .observe(this){ list ->
                list.forEach {work ->
                    if (work.state == WorkInfo.State.SUCCEEDED || work.state == WorkInfo.State.FAILED || work.state == WorkInfo.State.CANCELLED) {
                        input!!.visibility = View.VISIBLE
                        input!!.requestFocus()
                        hideCancelFab()
                    }
                    val line = work.progress.getString("output") ?: return@observe
                    val id = work.progress.getInt("id", 0)
                    if(id == 0) return@observe
                    runOnUiThread {
                        try {
                            output!!.text = "${output!!.text}\n$line"
                            output!!.scrollTo(0, output!!.height)
                            scrollView!!.fullScroll(View.FOCUS_DOWN)
                        }catch (ignored: Exception) {}
                    }
                }
            }
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
                    bottomSheet.cancel()
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
        notificationUtil.cancelDownloadNotification(downloadID.toInt())
    }

    companion object {
        private const val TAG = "CustomCommandActivity"
        private val compositeDisposable = CompositeDisposable()

    }
}