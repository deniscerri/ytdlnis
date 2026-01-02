package com.deniscerri.ytdl.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.deniscerri.ytdl.util.ThemeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


class ShareActivity : BaseActivity() {

    lateinit var context: Context
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController
    private var quickDownload by Delegates.notNull<Boolean>()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        if (Settings.canDrawOverlays(this)){
            val params = WindowManager.LayoutParams(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                PixelFormat.TRANSLUCENT
            )
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager

            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val myView: View = inflater.inflate(R.layout.activity_share, null)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            wm.addView(myView, params)

//            window.addFlags(
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
//                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
//                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
//            )
//
//            val params = window.attributes
//            params.alpha = 0f
//            window.attributes = params
            setContentView(R.layout.activity_share)

        }else{
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
        }

        context = baseContext
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        cookieViewModel.updateCookiesFile()
        val intent = intent
        handleIntents(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent) {
        askPermissions()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()
        navController.addOnDestinationChangedListener(object: NavController.OnDestinationChangedListener{
            @SuppressLint("RestrictedApi")
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                navController.removeOnDestinationChangedListener(this)
                CoroutineScope(SupervisorJob()).launch {
                    navController.currentBackStack.collectLatest {
                        if (it.isEmpty()){
                            this@ShareActivity.finish()
                        }
                    }
                }
            }
        })

        val action = intent.action
        Log.e("aa", intent.toString())
        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) == null && Intent.ACTION_SEND == action){
                intent.setClass(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
                return
            }

            runCatching { supportFragmentManager.popBackStack() }

            quickDownload = intent.getBooleanExtra("quick_download", sharedPreferences.getBoolean("quick_download", false) || sharedPreferences.getString("preferred_download_type", "video") == "command")
            val data = when(action){
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)!!
                else -> intent.dataString!!
            }

            val inputQuery = data.extractURL()
            val ai = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

            val type = intent.getStringExtra("TYPE")
            val background = intent.getBooleanExtra("BACKGROUND", ai.metaData?.getBoolean("quick_run_background", false) == true)

            lifecycleScope.launch {
                val result: ResultItem
                val existingResults = withContext(Dispatchers.IO){
                    resultViewModel.getAllByURL(inputQuery)
                }

                if (existingResults.isEmpty() || existingResults.size > 1) {
                    resultViewModel.deleteAll()
                    result = downloadViewModel.createEmptyResultItem(inputQuery)
                }else{
                    result = existingResults.first()
                }

                val downloadType = DownloadViewModel.Type.valueOf(type ?: downloadViewModel.getDownloadType(url = result.url).toString())
                if (sharedPreferences.getBoolean("download_card", true) && !background){
                    val bundle = Bundle()
                    bundle.putParcelable("result", result)
                    bundle.putSerializable("type", downloadType)
                    navController.setGraph(R.navigation.share_nav_graph, bundle)
                }else{
                    Toast.makeText(this@ShareActivity, "${getString(R.string.downloading)} $inputQuery", Toast.LENGTH_SHORT).show()

                    lifecycleScope.launch(Dispatchers.IO){
                        val downloadItem = downloadViewModel.createDownloadItemFromResult(
                            result = result,
                            givenType = downloadType)

                        downloadViewModel.queueDownloads(listOf(downloadItem))
                    }
                    this@ShareActivity.finish()
                }
            }
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        startActivity(Intent(this, MainActivity::class.java))
        super.onConfigurationChanged(newConfig)
    }
}