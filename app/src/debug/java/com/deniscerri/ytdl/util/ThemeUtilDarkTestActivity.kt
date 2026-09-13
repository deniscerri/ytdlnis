package com.deniscerri.ytdl.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

class ThemeUtilDarkTestActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        val darkConfiguration = Configuration(newBase.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    Configuration.UI_MODE_NIGHT_YES
        }
        super.attachBaseContext(newBase.createConfigurationContext(darkConfiguration))
    }
}
