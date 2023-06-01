package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.neo.highlight.core.Highlight
import com.neo.highlight.util.listener.HighlightTextWatcher
import com.neo.highlight.util.scheme.ColorScheme
import java.io.File
import java.util.regex.Pattern


class DownloadLogFragment : Fragment() {
    private lateinit var content: TextView
    private lateinit var contentScrollView : ScrollView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var observer: FileObserver
    private lateinit var copyLog : ExtendedFloatingActionButton
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
        return inflater.inflate(R.layout.fragment_download_log, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topAppBar = view.findViewById(R.id.title)
        topAppBar.setNavigationOnClickListener {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }

        content = view.findViewById(R.id.content)
        content.setTextIsSelectable(true)
        contentScrollView = view.findViewById(R.id.content_scrollview)

        copyLog = view.findViewById(R.id.copy_log)
        copyLog.setOnClickListener {
            val clipboard: ClipboardManager =
                mainActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(content.text)
        }

        val path = arguments?.getString("logpath")
        if (path == null) {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }else{
            arguments?.remove("logpath")
        }

        val file = File(path!!)
        topAppBar.title = file.name
        content.text = file.readText()

        if(Build.VERSION.SDK_INT < 29){
            observer = object : FileObserver(file.absolutePath, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    mainActivity.runOnUiThread{
                        val newText = File(path).readText()
                        content.text = newText
                        content.scrollTo(0, content.height)
                        contentScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
            observer.startWatching();
        }else{
            observer = object : FileObserver(file, MODIFY) {
                override fun onEvent(event: Int, p: String?) {
                    mainActivity.runOnUiThread{
                        val newText = File(path).readText()
                        content.text = newText
                        content.scrollTo(0, content.height)
                        contentScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
            observer.startWatching();
        }

        //init syntax highlighter
        val highlight = Highlight()
        val highlightWatcher = HighlightTextWatcher()

        val schemes = listOf(
            ColorScheme(Pattern.compile("([\"'])(?:\\\\1|.)*?\\1"), Color.parseColor("#FC8500")),
            ColorScheme(Pattern.compile("yt-dlp"), Color.parseColor("#00FF00")),
            ColorScheme(Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})"), Color.parseColor("#b5942f")),
            ColorScheme(Pattern.compile("\\d+(\\.\\d)?%"), Color.parseColor("#43a564"))
        )

        highlight.addScheme(
            *schemes.map { it }.toTypedArray()
        )
        highlightWatcher.addScheme(
            *schemes.map { it }.toTypedArray()
        )
        highlight.setSpan(content)
        content.addTextChangedListener(highlightWatcher)
    }

    override fun onDestroy() {
        super.onDestroy()
        observer.stopWatching()
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }
}