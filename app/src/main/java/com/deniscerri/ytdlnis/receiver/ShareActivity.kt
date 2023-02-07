package com.deniscerri.ytdlnis.receiver

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.ActivityMainBinding
import com.deniscerri.ytdlnis.ui.MoreFragment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.system.exitProcess
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

class ShareActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var context: Context
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadViewModel: DownloadViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        window.setBackgroundDrawable(ColorDrawable(0))
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        handleIntents(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent) {
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            Log.e(TAG, action)
            moreFragment = MoreFragment()
            if (type.equals("application/txt", ignoreCase = true)) {
                try {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    val `is` = contentResolver.openInputStream(uri!!)
                    val textBuilder = StringBuilder()
                    val reader: Reader = BufferedReader(
                        InputStreamReader(
                            `is`, Charset.forName(
                                StandardCharsets.UTF_8.name()
                            )
                        )
                    )
                    var c: Int
                    while (reader.read().also { c = it } != -1) {
                        textBuilder.append(c.toChar())
                    }
                    val l = listOf(*textBuilder.toString().split("\n").toTypedArray())
                    val lines = LinkedList(l)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                //TODO
            }
        }
    }



    private fun exit() {
        finishAffinity()
        exitProcess(0)
    }


    companion object {
        private const val TAG = "ShareActivity"
        private lateinit var moreFragment: MoreFragment
        private lateinit var fm: FragmentManager
    }
}