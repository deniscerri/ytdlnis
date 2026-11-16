package com.aistudio.kineticmedia.feature.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class WebViewEngine : WebEngine {
    private var webViewInstance: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Render(
        modifier: Modifier,
        url: String,
        onUrlChange: (String) -> Unit,
        onTitleChange: (String) -> Unit,
        onProgressChange: (Int) -> Unit,
        onLoadFinished: () -> Unit
    ) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            url?.let { onUrlChange(it) }
                            view?.title?.let { onTitleChange(it) }
                            onLoadFinished()
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            onProgressChange(newProgress)
                            view?.url?.let { onUrlChange(it) }
                            view?.title?.let { onTitleChange(it) }
                        }
                    }

                    if (url.isNotEmpty()) {
                        loadUrl(url)
                    }
                    webViewInstance = this
                }
            },
            update = { wv ->
                if (wv.url != url && url.isNotEmpty()) {
                    wv.loadUrl(url)
                }
                webViewInstance = wv
            }
        )
    }

    override fun goBack() {
        webViewInstance?.goBack()
    }

    override fun goForward() {
        webViewInstance?.goForward()
    }

    override fun reload() {
        webViewInstance?.reload()
    }

    override fun loadUrl(url: String) {
        webViewInstance?.loadUrl(url)
    }
}
