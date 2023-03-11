package com.deniscerri.ytdlnis

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.deniscerri.ytdlnis.databinding.ActivityMainBinding
import com.deniscerri.ytdlnis.service.IDownloaderListener
import com.deniscerri.ytdlnis.service.IDownloaderService
import com.deniscerri.ytdlnis.ui.HomeFragment
import com.deniscerri.ytdlnis.ui.downloads.DownloadQueueActivity
import com.deniscerri.ytdlnis.ui.downloads.HistoryFragment
import com.deniscerri.ytdlnis.ui.more.MoreFragment
import com.deniscerri.ytdlnis.ui.more.settings.SettingsActivity
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var context: Context
    private lateinit var homeFragment: HomeFragment
    private lateinit var historyFragment: HistoryFragment


    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setContentView(binding.root)
        context = baseContext
        askPermissions()
        checkUpdate()
        fm = supportFragmentManager

        homeFragment = HomeFragment()
        historyFragment = HistoryFragment()
        moreFragment = MoreFragment()
        initFragments()
        binding.bottomNavigationView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.home -> {
                    if (lastFragment === homeFragment) {
                        homeFragment.scrollToTop()
                    } else {
                        this.setTitle(R.string.app_name)
                    }
                    replaceFragment(homeFragment)
                }
                R.id.history -> {
                    if (lastFragment === historyFragment) {
                        val intent = Intent(context, DownloadQueueActivity::class.java)
                        startActivity(intent)
                    } else {
                        this.title = getString(R.string.downloads)
                    }
                    replaceFragment(historyFragment)
                }
                R.id.more -> {
                    if (lastFragment === moreFragment) {
                        val intent = Intent(context, SettingsActivity::class.java)
                        startActivity(intent)
                    } else {
                        this.title = getString(R.string.more)
                    }
                    replaceFragment(moreFragment)
                }
            }
            true
        }
        window.decorView.setOnApplyWindowInsetsListener { view: View, windowInsets: WindowInsets? ->
            val windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(
                windowInsets!!, view
            )
            val isImeVisible = windowInsetsCompat.isVisible(WindowInsetsCompat.Type.ime())
            binding.bottomNavigationView.visibility =
                if (isImeVisible) View.GONE else View.VISIBLE
            view.onApplyWindowInsets(windowInsets)
        }
        val intent = intent
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
            homeFragment = HomeFragment()
            historyFragment = HistoryFragment()
            moreFragment = MoreFragment()
            if (type.equals("application/txt", ignoreCase = true)) {
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
                    val lines = LinkedList(l)
                    homeFragment.handleFileIntent(lines)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                homeFragment.handleIntent(intent)
            }
            initFragments()
        }
    }

    private fun initFragments() {
        fm.beginTransaction()
            .replace(R.id.frame_layout, homeFragment)
            .add(R.id.frame_layout, historyFragment)
            .add(R.id.frame_layout, moreFragment)
            .hide(historyFragment)
            .hide(moreFragment)
            .commit()
        lastFragment = homeFragment
        listeners = ArrayList()
    }

    private fun replaceFragment(f: Fragment) {
        fm.beginTransaction().hide(lastFragment).show(f).commit()
        lastFragment = f
    }


    private fun checkUpdate() {
        val preferences = context.getSharedPreferences("root_preferences", MODE_PRIVATE)
        if (preferences.getBoolean("update_app", false)) {
            val updateUtil = UpdateUtil(this)
            updateUtil.updateApp()
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
        private lateinit var moreFragment: MoreFragment
        private lateinit var lastFragment: Fragment
        private lateinit var fm: FragmentManager
        private var isDownloadServiceRunning = false
        private lateinit var listeners: ArrayList<IDownloaderListener>
        private var iDownloaderService: IDownloaderService? = null
    }
}