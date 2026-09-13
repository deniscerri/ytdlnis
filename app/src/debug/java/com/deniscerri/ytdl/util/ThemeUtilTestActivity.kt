package com.deniscerri.ytdl.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

class ThemeUtilTestActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        val lightConfiguration = Configuration(newBase.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(lightConfiguration))
    }
}
