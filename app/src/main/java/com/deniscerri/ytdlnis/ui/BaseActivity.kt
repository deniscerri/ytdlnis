package com.deniscerri.ytdlnis.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.google.android.material.elevation.SurfaceColors

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.updateTheme(this)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        super.onCreate(savedInstanceState)
    }
}