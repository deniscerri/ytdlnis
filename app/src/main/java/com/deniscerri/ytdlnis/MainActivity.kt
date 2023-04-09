package com.deniscerri.ytdlnis

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.ui.HomeFragment
import com.deniscerri.ytdlnis.ui.downloads.DownloadQueueActivity
import com.deniscerri.ytdlnis.ui.more.settings.SettingsActivity
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navHostFragment : NavHostFragment


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        context = baseContext
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        preferences = context.getSharedPreferences("root_preferences", MODE_PRIVATE)

        if (preferences.getBoolean("incognito", false)){
            resultViewModel.deleteAll()
        }

        askPermissions()
        checkUpdate()

        navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        val navController = navHostFragment.findNavController()
        bottomNav = findViewById(R.id.bottomNavigationView)

        window.decorView.setOnApplyWindowInsetsListener { view: View, windowInsets: WindowInsets? ->
            val windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(
                windowInsets!!, view
            )
            val isImeVisible = windowInsetsCompat.isVisible(WindowInsetsCompat.Type.ime())
            bottomNav.visibility =
                if (isImeVisible) View.GONE else View.VISIBLE
            view.onApplyWindowInsets(windowInsets)
        }

        bottomNav.setupWithNavController(navController)
        bottomNav.setOnItemReselectedListener {
            when (it.itemId) {
                R.id.homeFragment -> {
                    (navHostFragment.childFragmentManager.primaryNavigationFragment!! as HomeFragment).scrollToTop()
                }
                R.id.historyFragment -> {
                    val intent = Intent(context, DownloadQueueActivity::class.java)
                    startActivity(intent)
                }
                R.id.moreFragment -> {
                    //navController.navigate(R.id.settingsFragment)
                    val intent = Intent(context, SettingsActivity::class.java)
                   startActivity(intent)
                }
            }
        }

        cookieViewModel.getCookiesFromDB().getOrNull()?.let{
            File(cacheDir, "cookies.txt").apply { writeText(it) }
        }

        val intent = intent
        handleIntents(intent)
    }

    fun hideNav() {
        bottomNav.visibility = View.GONE
    }

    fun showNav() {
        bottomNav.visibility = View.VISIBLE
    }

    fun disableBottomNavigation(){
        bottomNav.menu.forEach { it.isEnabled = false }
    }

    fun enableBottomNavigation(){
        bottomNav.menu.forEach { it.isEnabled = true }
    }

    override fun onResume() {
        super.onResume()
        val incognitoHeader = findViewById<TextView>(R.id.incognito_header)
        if (preferences.getBoolean("incognito", false)){
            incognitoHeader.visibility = View.VISIBLE
            window.statusBarColor = (incognitoHeader.background as ColorDrawable).color
        }else{
            window.statusBarColor = getColor(android.R.color.transparent)
            incognitoHeader.visibility = View.GONE
        }
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
            try {
                val uri = if (Build.VERSION.SDK_INT >= 33){
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                }else{
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
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
                (navHostFragment.childFragmentManager.primaryNavigationFragment!! as HomeFragment).handleFileIntent(l)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun checkUpdate() {
        if (preferences.getBoolean("update_app", false)) {
            val updateUtil = UpdateUtil(this)
            lifecycleScope.launch(Dispatchers.IO){
                updateUtil.updateApp()
            }
        }
    }

    private fun askPermissions() {
        val permissions = arrayListOf<String>()
        if (!checkFilePermission()) {
            if (Build.VERSION.SDK_INT >= 33){
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }else{
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (!checkNotificationPermission()){
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()){
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                1
            )
        }
    }


    private fun createDefaultFolders(){
        val audio = File(getString(R.string.music_path))
        val video = File(getString(R.string.video_path))
        val command = File(getString(R.string.command_path))

        audio.mkdirs()
        video.mkdirs()
        command.mkdirs()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in permissions.indices) {
            if (permissions.contains(Manifest.permission.POST_NOTIFICATIONS)) continue
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                createPermissionRequestDialog()
            }else{
                createDefaultFolders()
            }
        }
    }

    private fun exit() {
        finishAffinity()
        exitProcess(0)
    }

    private fun checkFilePermission(): Boolean {
        return if(Build.VERSION.SDK_INT >= 33){
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED)
        }else{
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun createPermissionRequestDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
        dialog.setTitle(getString(R.string.warning))
        dialog.setMessage(getString(R.string.request_permission_desc))
        dialog.setOnCancelListener { exit() }
        dialog.setNegativeButton(getString(R.string.exit_app)) { _: DialogInterface?, _: Int -> exit() }
        dialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
            exitProcess(0)
        }
        dialog.show()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        startActivity(Intent(this, MainActivity::class.java))
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}