package com.deniscerri.ytdl.ui.more.cookies

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.google.accompanist.web.AccompanistWebChromeClient
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.rememberWebViewState
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebViewActivity : BaseActivity() {
    private lateinit var cookiesViewModel: CookieViewModel
    private var webView: WebView? = null
    private lateinit var webViewCompose: ComposeView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var generateBtn: MaterialButton
    private lateinit var cookieManager: CookieManager
    private lateinit var url: String
    private lateinit var description: String
    private lateinit var cookies: String
    private lateinit var webViewClient: AccompanistWebViewClient
    private lateinit var preferences: SharedPreferences

    private var incognito: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_activity)
        url = intent.extras!!.getString("url")!!
        description = intent.extras!!.getString("description", "")
        incognito = intent.extras!!.getBoolean("incognito", false)

        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        lifecycleScope.launch {
            val appbar = findViewById<AppBarLayout>(R.id.webview_appbarlayout)
            toolbar = appbar.findViewById(R.id.webviewToolbar)
            generateBtn = toolbar.findViewById(R.id.generate)
            webViewCompose = findViewById(R.id.webview_compose)

            if (!url.isYoutubeURL()) {
                toolbar.menu.children.firstOrNull { it.itemId == R.id.get_data_sync_id }?.isVisible = false
            }

            toolbar.menu.children.firstOrNull { it.itemId == R.id.incognito }?.isChecked = incognito
            toolbar.menu.children.firstOrNull { it.itemId == R.id.get_data_sync_id }?.isVisible = false

            toolbar.setOnMenuItemClickListener { m : MenuItem ->
                when(m.itemId) {
                    R.id.incognito -> {
                        intent.putExtra("incognito", !incognito)
                        recreate()
                    }
                    R.id.desktop -> {
                        m.isChecked = !m.isChecked
                        webView.apply {
                            if (this == null) {
                                m.isChecked = false
                                return@apply
                            }

                            configureDesktopMode(this, m.isChecked)
                            this.reload()
                        }
                    }
                    else -> {}
                }
                true
            }

            preferences = PreferenceManager.getDefaultSharedPreferences(this@WebViewActivity)

            webViewClient = object : AccompanistWebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    webView = view
                    super.onPageFinished(view, url)
                    runCatching {
                        toolbar.title = view?.title ?: ""
                        cookies = cookieManager.getCookie(view?.url)
                    }
                }
            }

            toolbar.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            generateBtn.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        cookiesViewModel.getCookiesFromDB(url).getOrNull()?.let {
                            runCatching {
                                cookiesViewModel.insert(
                                    com.deniscerri.ytdl.database.models.CookieItem(
                                        0,
                                        url,
                                        it,
                                        description,
                                        true
                                    )
                                )
                                cookiesViewModel.updateCookiesFile()
                            }.onFailure {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@WebViewActivity,
                                        "Something went wrong",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            this@WebViewActivity.setResult(RESULT_OK)
                            this@WebViewActivity.finish()
                        }
                    }
                }
            }

            cookieManager = CookieManager.getInstance()

            if (savedInstanceState == null) {
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            }

            webViewCompose.apply {
                setContent { WebViewView() }
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

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun WebViewView() {
        val webViewChromeClient = remember {
            object : AccompanistWebChromeClient() {
            }
        }

        Scaffold(modifier = Modifier.Companion.fillMaxSize()) { paddingValues ->
            com.google.accompanist.web.WebView(
                state = rememberWebViewState(url),
                client = webViewClient,
                chromeClient = webViewChromeClient,
                modifier = Modifier.Companion
                    .padding(paddingValues)
                    .fillMaxSize(),
                captureBackPresses = false,
                factory = { context ->
                    WebView(context).apply {
                        settings.run {
                            if (!incognito) {
                                cacheMode = WebSettings.LOAD_DEFAULT
                                domStorageEnabled = true
                                setGeolocationEnabled(true)
                            } else {
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                domStorageEnabled = false
                                setGeolocationEnabled(false)
                                WebStorage.getInstance().deleteAllData()

                                this@apply.clearHistory()
                                this@apply.clearCache(true)
                                this@apply.clearFormData()
                            }

                            javaScriptEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            if (Build.VERSION.SDK_INT >= 26) {
                                safeBrowsingEnabled = true
                            }
                            preferences.edit().putString("useragent_header", userAgentString)
                                .apply()
                        }
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }
                }
            )


        }
    }

    companion object {
        const val TAG = "WebViewActivity"
    }

    data class CookieItem(
        val domain: String = "",
        val name: String = "",
        val value: String = "",
        val includeSubdomains: Boolean = true,
        val path: String = "/",
        val secure: Boolean = true,
        val expiry: Long = 0L,
    ) {
        constructor(
            url: String,
            name: String,
            value: String
        ) : this(domain = url.replace(Regex("""http(s)?://(\w*(www|m|account|sso))?|/.*"""), ""), name = name, value = value)

        fun toNetscapeFormat(): String {
            val stringList = listOf(domain,
                includeSubdomains.toString().uppercase(),
                path,
                secure.toString().uppercase(),
                expiry.toString(),
                name,
                value)

            val builder = StringBuilder(stringList.first())

            for (s in stringList.subList(1, stringList.size)) {
                if (s.isNotEmpty()) {
                    if (builder.isNotEmpty())
                        builder.append("\u0009")
                    builder.append(s)
                }
            }
            return builder.toString()
        }
    }

}