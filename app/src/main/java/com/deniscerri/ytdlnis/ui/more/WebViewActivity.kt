package com.deniscerri.ytdlnis.ui.more

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CookieItem
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class WebViewActivity : AppCompatActivity() {
    private lateinit var cookiesViewModel: CookieViewModel
    private lateinit var webView: WebView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var generateBtn: MaterialButton
    private lateinit var cookieManager: CookieManager
    private lateinit var url: String
    private lateinit var cookies: String

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
        cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true

        cookieManager = CookieManager.getInstance()

        toolbar.setNavigationOnClickListener {
            cookieManager.flush()
            onBackPressedDispatcher.onBackPressed()
        }

        val webViewClient: WebViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                kotlin.runCatching {
                    toolbar.title = view.title
                    cookies = CookieManager.getInstance().getCookie(view.url)
                }
            }
        }
        webView.webViewClient = webViewClient

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
            cookieManager.removeAllCookies(null)
            onBackPressedDispatcher.onBackPressed()
        }

        webView.loadUrl(url)

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