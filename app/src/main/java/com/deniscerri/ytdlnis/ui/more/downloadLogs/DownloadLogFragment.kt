package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.viewmodel.LogViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.neo.highlight.core.Highlight
import com.neo.highlight.util.listener.HighlightTextWatcher
import com.neo.highlight.util.scheme.ColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern


class DownloadLogFragment : Fragment() {
    private lateinit var content: TextView
    private lateinit var contentScrollView : ScrollView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var copyLog : ExtendedFloatingActionButton
    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
        return inflater.inflate(R.layout.fragment_download_log, container, false)
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topAppBar = view.findViewById(R.id.title)
        topAppBar.setNavigationOnClickListener {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }

        content = view.findViewById(R.id.content)
        content.movementMethod = ScrollingMovementMethod()
        content.setTextIsSelectable(true)
        contentScrollView = view.findViewById(R.id.content_scrollview)
        val bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)

        topAppBar.setOnClickListener {
            contentScrollView.scrollTo(0,0)
            bottomAppBar?.menu?.get(1)?.isVisible = true
        }


        copyLog = view.findViewById(R.id.copy_log)
        copyLog.setOnClickListener {
            val clipboard: ClipboardManager =
                mainActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(content.text)
            Snackbar.make(bottomAppBar, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
        }

        val id = arguments?.getLong("logID")
        if (id == null) {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }


        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]

        CoroutineScope(Dispatchers.IO).launch {
            val logItem = logViewModel.getItemById(id!!)
            topAppBar.title = logItem.title
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
        content.isFocusable = true
        content.addTextChangedListener(highlightWatcher)
        content.setHorizontallyScrolling(false)

        bottomAppBar?.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId){
                R.id.wrap -> {
                    var scrollView = view.findViewById<HorizontalScrollView>(R.id.horizontalscroll_output)
                    if(scrollView != null){
                        val parent = (scrollView.parent as ViewGroup)
                        scrollView.removeAllViews()
                        parent.removeView(scrollView)
                        parent.addView(content, 0)
                    }else{
                        val parent = content.parent as ViewGroup
                        parent.removeView(content)
                        scrollView = HorizontalScrollView(requireContext())
                        scrollView.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scrollView.addView(content)
                        scrollView.id = R.id.horizontalscroll_output
                        parent.addView(scrollView, 0)
                    }
                    content.setHorizontallyScrolling(!content.canScrollHorizontally(1))
                }

                R.id.scroll_down -> {
                    m.isVisible = false
                    contentScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
            true
        }

//        contentScrollView.setOnScrollChangeListener { view, sx, sy, osx, osy ->
//            if (sy < osy){
//                if (contentScrollView.canScrollVertically(1)){
//                    shouldScroll = false
//                    content.movementMethod = LinkMovementMethod.getInstance()
//                    bottomAppBar?.menu?.get(1)?.isVisible = true
//                }else{
//                    shouldScroll = true
//                    content.movementMethod = ScrollingMovementMethod()
//                    bottomAppBar?.menu?.get(1)?.isVisible = false
//                }
//            }
//        }


        logViewModel.getLogFlowByID(id!!).observe(viewLifecycleOwner){logItem ->
            lifecycleScope.launch {
                content.text = logItem.content
                content.scrollTo(content.scrollX, content.height)
                contentScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }
}