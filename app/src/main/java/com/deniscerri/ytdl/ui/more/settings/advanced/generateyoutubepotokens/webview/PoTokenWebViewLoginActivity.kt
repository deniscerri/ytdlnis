package com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.PoTokenGenerator
import com.google.accompanist.web.AccompanistWebChromeClient
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PoTokenWebViewLoginActivity : BaseActivity() {
    private lateinit var webView: WebView
    private lateinit var webViewCompose: ComposeView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var generateBtn: MaterialButton
    private lateinit var cookieManager: CookieManager
    private lateinit var webViewClient: AccompanistWebViewClient

    private val sampleVideoID = "aqz-KE-bpKQ" //Big Buck Bunny

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_activity)
        lifecycleScope.launch {
            val appbar = findViewById<AppBarLayout>(R.id.webview_appbarlayout)
            toolbar = appbar.findViewById(R.id.webviewToolbar)
            toolbar.menu.forEach { it.isVisible = false }

            generateBtn = toolbar.findViewById(R.id.generate)
            webViewCompose = findViewById(R.id.webview_compose)
            cookieManager = CookieManager.getInstance()

            webViewClient = object : AccompanistWebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    kotlin.runCatching {
                        toolbar.title = view?.title ?: ""
                    }
                }
            }

            toolbar.setNavigationOnClickListener {
                finishAndRemoveTask()
            }

            generateBtn.setOnClickListener {
                generateBtn.isEnabled = false

                lifecycleScope.launch {
                    val generator = PoTokenGenerator()
                    val res = withContext(Dispatchers.IO) {
                        generator.getWebClientPoToken(sampleVideoID)
                    }

                    if (res == null) {
                        setResult(RESULT_CANCELED)
                    }else {
                        val intent = Intent()
                        intent.putExtra("visitor_data", res.visitorData)
                        intent.putExtra("player_potoken", res.playerRequestPoToken)
                        intent.putExtra("streaming_potoken", res.streamingDataPoToken)
                        setResult(RESULT_OK, intent)
                    }

                    webView.clearCache(true)
                    // ensures that the WebView isn't doing anything when destroying it
                    webView.loadUrl("about:blank")
                    webView.onPause()
                    webView.removeAllViews()
                    webView.destroy()
                    finish()
                }
            }

            webViewCompose.apply {
                setContent { WebViewView() }
            }
        }

    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    @Composable
    fun WebViewView() {
        val webViewChromeClient = remember {
            object : AccompanistWebChromeClient() {
            }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
            WebView(
                state = rememberWebViewState("https://youtube.com/account"), client = webViewClient, chromeClient = webViewChromeClient,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                captureBackPresses = false, factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.run {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            if (Build.VERSION.SDK_INT >= 26) {
                                safeBrowsingEnabled = true
                            }
                        }
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }
                }
            )
        }
    }

    companion object {
        const val TAG = "PoTokenWebViewActivity"
    }

}