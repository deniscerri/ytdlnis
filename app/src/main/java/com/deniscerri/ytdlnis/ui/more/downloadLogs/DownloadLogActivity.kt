package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deniscerri.ytdlnis.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File


class DownloadLogActivity : AppCompatActivity() {
    private lateinit var content: TextView
    private lateinit var contentScrollView : ScrollView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var observer: FileObserver
    private lateinit var copyLog : ExtendedFloatingActionButton
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_log)
        context = baseContext

        topAppBar = findViewById(R.id.title)
        topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        content = findViewById(R.id.content)
        content.movementMethod = ScrollingMovementMethod()
        contentScrollView = findViewById(R.id.content_scrollview)

        copyLog = findViewById(R.id.copy_log)
        copyLog.setOnClickListener {
            val clipboard: ClipboardManager =
                getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("Download Log", content.text)
            clipboard.setPrimaryClip(clip)
        }

        val path = intent.getStringExtra("path")
        if (path == null) {
            onBackPressedDispatcher.onBackPressed()
        }

        val file = File(path!!)
        topAppBar.title = file.name
        content.text = file.readText()

        if(Build.VERSION.SDK_INT < 29){
            observer = object : FileObserver(file.absolutePath, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    runOnUiThread{
                        content.text = File(path).readText()
                        content.scrollTo(0, content.height)
                    }
                }
            }
            observer.startWatching();
        }else{
            observer = object : FileObserver(file, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    runOnUiThread{
                        content.text = File(path).readText()
                        content.scrollTo(0, content.height)
                        contentScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
            observer.startWatching();
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observer.stopWatching()
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }
}