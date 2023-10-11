package com.deniscerri.ytdlnis.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors

open class ErrorDialogActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val title = intent.extras?.getString("title") ?: ""
        val message = intent.extras?.getString("message") ?: ""

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

        val errDialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(R.drawable.ic_cancel)
            .setPositiveButton(getString(R.string.copy_log)) { _: DialogInterface?, _: Int ->
                val clipboard: ClipboardManager =
                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setText(message)
                this.finish()
            }.setOnDismissListener {
                this.finish()
            }
        errDialog.show()
        super.onCreate(savedInstanceState)
    }
}