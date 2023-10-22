package com.deniscerri.ytdlnis.ui.more

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CookieItem
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.deniscerri.ytdlnis.ui.BaseActivity
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


class WebViewActivity : BaseActivity() {
    private lateinit var cookiesViewModel: CookieViewModel
    private lateinit var webView: WebView
    private lateinit var webViewCompose: ComposeView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var generateBtn: MaterialButton
    private lateinit var cookieManager: CookieManager
    private lateinit var url: String
    private lateinit var cookies: String
    private lateinit var webViewClient: AccompanistWebViewClient
    private lateinit var preferences: SharedPreferences
    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_activity)
        url = intent.extras!!.getString("url")!!
        cookies = ""
        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        val appbar = findViewById<AppBarLayout>(R.id.webview_appbarlayout)
        toolbar = appbar.findViewById(R.id.webviewToolbar)
        generateBtn = toolbar.findViewById(R.id.generate)
        webViewCompose = findViewById(R.id.webview_compose)
        cookieManager = CookieManager.getInstance()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        webViewClient = object : AccompanistWebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                kotlin.runCatching {
                    toolbar.title = view?.title ?: ""
                    cookies = CookieManager.getInstance().getCookie(view?.url)
                }
            }
        }

        cookieManager = CookieManager.getInstance()

        toolbar.setNavigationOnClickListener {
            cookieManager.flush()
            onBackPressedDispatcher.onBackPressed()
        }

        generateBtn.setOnClickListener {
            cookiesViewModel.getCookiesFromDB().getOrNull()?.let {
                kotlin.runCatching {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO){
                            cookiesViewModel.insert(
                                CookieItem(
                                    0,
                                    url,
                                    it
                                )
                            )
                        }
                        cookiesViewModel.updateCookiesFile()
                    }
                }.onFailure {
                    Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
                }
            }
            onBackPressedDispatcher.onBackPressed()
        }

        webViewCompose.apply {
            setContent { WebViewView() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun WebViewView() {
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
                    WebView(context).apply {
                        settings.run {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            preferences.edit().putString("useragent_header", userAgentString).apply()
                        }
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