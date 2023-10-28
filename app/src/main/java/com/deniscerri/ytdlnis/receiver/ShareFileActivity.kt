package com.deniscerri.ytdlnis.receiver

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.BaseActivity
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.deniscerri.ytdlnis.util.UiUtil

class ShareFileActivity : BaseActivity() {

    lateinit var context: Context
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
        }

        setContentView(R.layout.activity_share)

        val paths = intent.getStringArrayExtra("path")
        val notificationId = intent.getIntExtra("notificationID", 0)
        NotificationUtil(this).cancelDownloadNotification(notificationId)
        Log.e("SHAREE", paths.toString())
        UiUtil.shareFileIntent(this, paths!!.toList().filter { it.isNotBlank() }.distinct())
        finishAffinity()
    }
}