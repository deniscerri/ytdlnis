package com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.NewPipePoTokenGenerator
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
    private lateinit var cookiesViewModel: CookieViewModel
    private lateinit var preferences: SharedPreferences

    private val sampleVideoID = "aqz-KE-bpKQ" //Big Buck Bunny

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_activity)

        val url = intent.getStringExtra("url")!!
        var redirectUrl = intent.getStringExtra("redirect_url")

        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        lifecycleScope.launch {
            val appbar = findViewById<AppBarLayout>(R.id.webview_appbarlayout)
            toolbar = appbar.findViewById(R.id.webviewToolbar)
            //hide incognito
            toolbar.menu.children.firstOrNull { it.itemId == R.id.incognito }?.isVisible = false
            toolbar.setOnMenuItemClickListener { m : MenuItem ->
                when(m.itemId) {
                    R.id.get_data_sync_id -> {
                        webView.evaluateJavascript("ytcfg.get('DATASYNC_ID')") { id ->
                            UiUtil.copyToClipboard(id.replace("\"", ""), this@PoTokenWebViewLoginActivity)
                        }
                    }
                    R.id.desktop -> {
                        m.isChecked = !m.isChecked
                        webView.apply {
                            configureDesktopMode(this, m.isChecked)
                            this.reload()
                        }
                    }
                    else -> {}
                }
                true
            }

            generateBtn = toolbar.findViewById(R.id.generate)
            webViewCompose = findViewById(R.id.webview_compose)
            cookieManager = CookieManager.getInstance()

            preferences = PreferenceManager.getDefaultSharedPreferences(this@PoTokenWebViewLoginActivity)
            preferences.edit().putString("genenerate_youtube_po_token_preferred_url", url).apply()

            webViewClient = object : AccompanistWebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    kotlin.runCatching {
                        toolbar.title = view?.title ?: ""
                    }

                    //finish generation
                    if (url?.contains("robots.txt") == true) {
                        lifecycleScope.launch {
                            val generator = NewPipePoTokenGenerator()
                            val res = withContext(Dispatchers.IO) {
                                generator.getWebClientPoToken(sampleVideoID)
                            }

                            if (res == null) {
                                setResult(RESULT_CANCELED)
                            }else {
                                val intent = Intent()
                                intent.putExtra("visitor_data", res.visitorData)
                                intent.putExtra("player_potoken", res.playerRequestPoToken)
                                intent.putExtra("subs_potoken", res.playerRequestPoToken)
                                intent.putExtra("streaming_potoken", res.streamingDataPoToken)
                                setResult(RESULT_OK, intent)
                            }


                            //update cookies
                            withContext(Dispatchers.IO) {
                                val cookieURL = "Po Token Generated Cookies"
                                cookiesViewModel.getCookiesFromDB(cookieURL).getOrNull()?.let {
                                    kotlin.runCatching {
                                        cookiesViewModel.insert(
                                            CookieItem(
                                                0,
                                                cookieURL,
                                                it,
                                                "",
                                                true
                                            )
                                        )
                                        cookiesViewModel.updateCookiesFile()
                                        preferences.edit().putBoolean("use_cookies", true).apply()
                                    }.onFailure {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@PoTokenWebViewLoginActivity,
                                                "Tokens were generated but cookies were not updated",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
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

                    webView.evaluateJavascript("(function(){return document.readyState;})()") { value ->
                        if (value?.trim('"') == "complete") {
                            Handler(Looper.getMainLooper()).postDelayed({
                                redirectUrl?.apply {
                                    webView.loadUrl(this)
                                }
                                redirectUrl = null
                            }, 2500)
                        }
                    }

                }
            }

            toolbar.setNavigationOnClickListener {
                finishAndRemoveTask()
            }

            generateBtn.setOnClickListener {
                generateBtn.isEnabled = false
                webView.loadUrl("https://www.youtube.com/robots.txt")
            }

            webViewCompose.apply {
                setContent { WebViewView(url) }
            }
        }

    }

    private fun configureDesktopMode(webView: WebView, desktop: Boolean) {
        webView.settings.apply {
            if (desktop) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                useWideViewPort = true
                loadWithOverviewMode = true
            } else {
                userAgentString = WebSettings.getDefaultUserAgent(webView.context)
                useWideViewPort = false
                loadWithOverviewMode = false
            }
        }
    }


    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    @Composable
    fun WebViewView(url: String) {
        val webViewChromeClient = remember {
            object : AccompanistWebChromeClient() {
            }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
            WebView(
                state = rememberWebViewState(url), client = webViewClient, chromeClient = webViewChromeClient,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                captureBackPresses = false, factory = { context ->
                    WebStorage.getInstance().deleteAllData()
                    WebView(context).apply {
                        webView = this
                        clearHistory()
                        clearCache(true)
                        clearFormData()
                        settings.run {
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            domStorageEnabled = false
                            setGeolocationEnabled(false)
                            javaScriptEnabled = true
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