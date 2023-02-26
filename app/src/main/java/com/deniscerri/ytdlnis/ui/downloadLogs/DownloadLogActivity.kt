package com.deniscerri.ytdlnis.ui.downloadLogs

import android.content.Context
import android.os.Bundle
import android.os.FileObserver
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.deniscerri.ytdlnis.R
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.runBlocking
import java.io.File


class DownloadLogActivity : AppCompatActivity() {
    private lateinit var content: TextView
    private lateinit var contentScrollView : NestedScrollView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var observer: FileObserver
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
        contentScrollView = findViewById(R.id.content_scrollview)

        val path = intent.getStringExtra("path")
        if (path == null) {
            onBackPressedDispatcher.onBackPressed()
        }

        val file = File(path!!)
        topAppBar.title = file.name
        content.text = file.readText()

        observer = object : FileObserver(file, MODIFY) {
            override fun onEvent(event: Int, p: String?) {
                runOnUiThread{
                    content.text = File(path).readText()
                    content.scrollTo(0, content.height)
                }
            }
        }
        observer.startWatching();
    }

    override fun onDestroy() {
        super.onDestroy()
        observer.stopWatching()
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }
}