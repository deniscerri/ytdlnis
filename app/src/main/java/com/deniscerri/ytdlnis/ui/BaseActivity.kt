package com.deniscerri.ytdlnis.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deniscerri.ytdlnis.util.ThemeUtil

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.updateTheme(this)
        super.onCreate(savedInstanceState)
    }
}