package com.deniscerri.ytdl.ui.more.downloadLogs

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.LogViewModel
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadLogFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var copyLog: ExtendedFloatingActionButton
    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var logID: Long? = null

    private var autoScroll: Boolean = true
    private var scrollDownBtn: MenuItem? = null
    private var rawLogContent: String = ""
    private var isWebViewLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_download_log, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topAppBar = view.findViewById(R.id.title)
        topAppBar.setNavigationOnClickListener {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }

        webView = view.findViewById(R.id.log_webview)
        val bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)
        copyLog = view.findViewById(R.id.copy_log)
        val slider = view.findViewById<Slider>(R.id.textsize_seekbar)

        // WebSettings configuration
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.addJavascriptInterface(WebAppInterface(), "Android")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        topAppBar.setOnClickListener {
            webView.evaluateJavascript("window.scrollTo(0, 0);", null)
            bottomAppBar?.menu?.children?.firstOrNull { it.itemId == R.id.scroll_down }?.isVisible = true
        }

        copyLog.setOnClickListener {
            val clipboard: ClipboardManager =
                mainActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(rawLogContent)
            Snackbar.make(bottomAppBar, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG)
                .setAnchorView(bottomAppBar)
                .show()
        }

        logID = arguments?.getLong("logID")
        if (logID == null || logID == 0L) {
            mainActivity.onBackPressedDispatcher.onBackPressed()
            return
        }

        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val logItem = logViewModel.getItemById(logID!!) ?: throw Exception()
                withContext(Dispatchers.Main) {
                    topAppBar.title = logItem.title
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Log is deleted!", Toast.LENGTH_SHORT).show()
                    mainActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebViewLoaded = true
                if (rawLogContent.isNotBlank()) {
                    updateLogInWebView(rawLogContent)
                }
            }
        }

        scrollDownBtn = bottomAppBar?.menu?.children?.firstOrNull { it.itemId == R.id.scroll_down }

        val zoomValue = sharedPreferences.getFloat("log_zoom", 2f)

        bottomAppBar?.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.wrap -> {
                    val currentWrap = sharedPreferences.getBoolean("wrap_text_log", false)
                    val newWrap = !currentWrap
                    sharedPreferences.edit().putBoolean("wrap_text_log", newWrap).apply()

                    val cssClass = if (newWrap) "pre-wrap" else "pre"
                    webView.evaluateJavascript("setWrap('$cssClass');", null)
                }

                R.id.scroll_down -> {
                    m.isVisible = false
                    autoScroll = true
                    scrollToBottom()
                }

                R.id.text_size -> {
                    slider?.isVisible = !(slider?.isVisible ?: false)
                }

                R.id.export_file -> {
                    logViewModel.exportToFile(logID!!) { f ->
                        if (f == null) {
                            Snackbar.make(bottomAppBar, getString(R.string.couldnt_parse_file), Snackbar.LENGTH_LONG)
                                .setAnchorView(bottomAppBar)
                                .show()
                        } else {
                            val snack = Snackbar.make(bottomAppBar, getString(R.string.backup_created_successfully), Snackbar.LENGTH_LONG)
                            snack.setAnchorView(bottomAppBar)
                            snack.setAction(R.string.share) {
                                FileUtil.shareFileIntent(requireContext(), listOf(f.absolutePath))
                            }
                            snack.show()
                        }
                    }
                }
            }
            true
        }

        slider?.apply {
            valueFrom = 0f
            valueTo = 10f
            value = zoomValue
            addOnChangeListener { _, valChoice, _ ->
                val fontSizePx = valChoice + 13f
                webView.evaluateJavascript("setFontSize(${fontSizePx});", null)
                sharedPreferences.edit(true) {
                    putFloat("log_zoom", valChoice)
                }
            }
        }

        val fontSize = sharedPreferences.getFloat("log_zoom", 2f) + 13f
        val whiteSpace = if (sharedPreferences.getBoolean("wrap_text_log", false)) "pre-wrap" else "pre"
        val baseHtml = buildHtmlWrapper(fontSize, whiteSpace)

        webView.loadDataWithBaseURL("https://appassets.androidplatform.net/", baseHtml, "text/html", "UTF-8", null)

        // Real-time Log Updates
        logViewModel.getLogFlowByID(logID!!).observe(viewLifecycleOwner) { logItem ->
            if (logItem != null && logItem.content.isNotBlank()) {
                rawLogContent = logItem.content
                if (isWebViewLoaded) {
                    updateLogInWebView(logItem.content)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkerEventBus.events.collectLatest { event ->
                    val progressBar = requireView().findViewById<LinearProgressIndicator>(R.id.progress)
                    if (event.logItemID == logID) {
                        progressBar.isVisible = event.progress < 100
                        progressBar.setProgressCompat(event.progress, true)
                    }
                }
            }
        }
    }

    private fun updateLogInWebView(fullText: String) {
        // Escapes text for Javascript safety
        val escaped = fullText
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")

        webView.evaluateJavascript("updateContent(`$escaped`, $autoScroll);", null)
    }

    private fun scrollToBottom() {
        webView.evaluateJavascript("scrollToBottom();", null)
    }

    private fun buildHtmlWrapper(fontSizePx: Float, whiteSpaceMode: String): String {
        val wordBreakMode = if (whiteSpaceMode == "pre-wrap") "break-all" else "normal"
        val overflowXMode = if (whiteSpaceMode == "pre-wrap") "hidden" else "auto"

        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            :root {
                --text-color: #000000;
            }

            @media (prefers-color-scheme: dark) {
                :root {
                    --text-color: #FFFFFF;
                }
            }
        
            html, body {
                background-color: transparent;
                margin: 0;
                padding: 0;
                width: 100%;
                overflow-x: $overflowXMode;
            }
            #log-content {
                font-family: monospace;
                font-size: ${fontSizePx}px;
                white-space: $whiteSpaceMode;
                word-break: $wordBreakMode;
                padding: 10px;
                padding-bottom: 90px; /* Leave space for BottomAppBar */
                user-select: text;
                -webkit-user-select: text;
            }
        </style>
    </head>
    <body>
        <div id="log-content"></div>
        <script>
            const el = document.getElementById('log-content');

            function updateContent(text, shouldScroll) {
                el.textContent = text;
                if (shouldScroll) {
                    scrollToBottom();
                } else {
                    checkScrollPosition();
                }
            }

            function scrollToBottom() {
                window.scrollTo(0, document.documentElement.scrollHeight);
                checkScrollPosition();
            }

            function setFontSize(px) {
                el.style.fontSize = px + 'px';
            }

            function setWrap(wrapMode) {
                el.style.whiteSpace = wrapMode;
                if (wrapMode === 'pre-wrap') {
                    el.style.wordBreak = 'break-all';
                    document.body.style.overflowX = 'hidden';
                } else {
                    el.style.wordBreak = 'normal';
                    document.body.style.overflowX = 'auto';
                }
            }

            function checkScrollPosition() {
                const scrollPosition = window.scrollY || window.pageYOffset;
                const viewportHeight = window.innerHeight;
                const totalHeight = Math.max(
                    document.body.scrollHeight, 
                    document.documentElement.scrollHeight
                );

                // True if user is more than 30px away from the very bottom
                const canScrollDown = (scrollPosition + viewportHeight) < (totalHeight - 30);

                if (window.Android && window.Android.onScrollStateChanged) {
                    window.Android.onScrollStateChanged(canScrollDown);
                }
            }

            let scrollTimeout;
            window.addEventListener('scroll', function() {
                if (scrollTimeout) clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(checkScrollPosition, 50);
            });
        </script>
    </body>
    </html>
""".trimIndent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                File(requireContext().cacheDir, "log_viewer.html").delete()
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onScrollStateChanged(canScrollDown: Boolean) {
            activity?.runOnUiThread {
                // Show button if user scrolled up (can scroll down further)
                scrollDownBtn?.isVisible = canScrollDown
            }
        }
    }
}