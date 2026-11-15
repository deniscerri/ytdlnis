package com.aistudio.kineticmedia.feature.browser

import android.content.Context
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface WebEngine {
    @Composable
    fun Render(
        modifier: Modifier,
        url: String,
        onUrlChange: (String) -> Unit,
        onTitleChange: (String) -> Unit,
        onProgressChange: (Int) -> Unit,
        onLoadFinished: () -> Unit
    )

    fun goBack()
    fun goForward()
    fun reload()
    fun loadUrl(url: String)
}
