package com.deniscerri.ytdlnis

import android.Manifest
import android.app.ActionBar.LayoutParams
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.ui.BaseActivity
import com.deniscerri.ytdlnis.ui.HomeFragment
import com.deniscerri.ytdlnis.ui.more.settings.SettingsActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess


class MainActivity : BaseActivity() {

    lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var navigationView: View
    private lateinit var navHostFragment : NavHostFragment
    private lateinit var navController : NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        setContentView(R.layout.activity_main)
        context = baseContext
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        preferences = PreferenceManager.getDefaultSharedPreferences(context)

        if (preferences.getBoolean("incognito", false)) resultViewModel.deleteAll()

        askPermissions()
        checkUpdate()

        navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()
        navigationView = try {
            findViewById(R.id.bottomNavigationView)
        }catch (e: Exception){
            findViewById<NavigationView>(R.id.navigationView)
        }

        if (navigationView is NavigationBarView){
            window.decorView.setOnApplyWindowInsetsListener { view: View, windowInsets: WindowInsets? ->
                val windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(
                    windowInsets!!, view
                )
                val isImeVisible = windowInsetsCompat.isVisible(WindowInsetsCompat.Type.ime())
                navigationView.visibility =
                    if (isImeVisible) View.GONE else View.VISIBLE
                view.onApplyWindowInsets(windowInsets)
            }
        }

        if (savedInstanceState == null){
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            graph.setStartDestination(R.id.homeFragment)
            when(preferences.getString("start_destination", "")) {
                "History" -> graph.setStartDestination(R.id.historyFragment)
                "More" -> if (navigationView is NavigationBarView) graph.setStartDestination(R.id.moreFragment)
            }

            navController.graph = graph
        }

        if (navigationView is NavigationBarView){
            (navigationView as NavigationBarView).setupWithNavController(navController)
            (navigationView as NavigationBarView).setOnItemReselectedListener {
                when (it.itemId) {
                    R.id.homeFragment -> {
                        (navHostFragment.childFragmentManager.primaryNavigationFragment!! as HomeFragment).scrollToTop()
                    }
                    R.id.historyFragment -> {
                        navController.navigate(R.id.downloadQueueMainFragment)
                    }
                    R.id.moreFragment -> {
                        val intent = Intent(context, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                }
            }

            val activeDownloadsBadge = (navigationView as NavigationBarView).getOrCreateBadge(R.id.historyFragment)
            lifecycleScope.launch {
                downloadViewModel.activeDownloadsCount.collectLatest {
                    if (it == 0) {
                        activeDownloadsBadge.isVisible = false
                        activeDownloadsBadge.clearNumber()
                    }
                    else {
                        activeDownloadsBadge.isVisible = true
                        activeDownloadsBadge.number = it
                    }
                }
            }
        }
        if (navigationView is NavigationView){
            (navigationView as NavigationView).setupWithNavController(navController)
            //terminate button
            (navigationView as NavigationView).menu.getItem(8).setOnMenuItemClickListener {
                if (preferences.getBoolean("ask_terminate_app", true)){
                    var doNotShowAgain = false
                    val terminateDialog = MaterialAlertDialogBuilder(this)
                    terminateDialog.setTitle(getString(R.string.confirm_delete_history))
                    val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
                    val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
                    terminateDialog.setView(dialogView)
                    checkbox.setOnCheckedChangeListener { compoundButton, _ ->
                        doNotShowAgain = compoundButton.isChecked
                    }

                    terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    terminateDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        runBlocking {
                            val job : Job = lifecycleScope.launch(Dispatchers.IO) {
                                val activeDownloads = downloadViewModel.getActiveDownloads().toMutableList()
                                activeDownloads.map { it.status = DownloadRepository.Status.Queued.toString() }
                                activeDownloads.forEach {
                                    downloadViewModel.updateDownload(it)
                                }
                            }
                            runBlocking {
                                job.join()
                                if (doNotShowAgain){
                                    preferences.edit().putBoolean("ask_terminate_app", false).apply()
                                }
                                finishAndRemoveTask()
                                finishAffinity()
                                exitProcess(0)
                            }
                        }
                    }
                    terminateDialog.show()
                }else{
                    finishAndRemoveTask()
                    exitProcess(0)
                }
                true
            }
            //settings button
            (navigationView as NavigationView).menu.getItem(9).setOnMenuItemClickListener {
                val intent = Intent(context, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            (navigationView as NavigationView).getHeaderView(0).findViewById<TextView>(R.id.title).text = ThemeUtil.getStyledAppName(this)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (navigationView is NavigationBarView){
                when(destination.id){
                    R.id.homeFragment, R.id.historyFragment, R.id.moreFragment -> showBottomNavigation()
                    else -> hideBottomNavigation()
                }

            }
        }

        navigationView.visibilityChanged {
            if (it.isVisible){
                val curr = navController.currentDestination?.id
                if (curr != R.id.homeFragment && curr != R.id.historyFragment && curr != R.id.moreFragment) hideBottomNavigation()
            }
        }

        when(preferences.getString("start_destination", "")) {
            "Queue" -> if (savedInstanceState == null) navController.navigate(R.id.downloadQueueMainFragment)
        }

        cookieViewModel.updateCookiesFile()
        val intent = intent
        handleIntents(intent)

        if (preferences.getBoolean("auto_update_ytdlp", false)){
            CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
                kotlin.runCatching {
                    if(DBManager.getInstance(this@MainActivity).downloadDao.getDownloadsCountByStatus(listOf("Active", "Queued")) == 0){
                        if (UpdateUtil(this@MainActivity).updateYoutubeDL() == YoutubeDL.UpdateStatus.DONE) {
                            val version = YoutubeDL.getInstance().version(context)
                            Snackbar.make(findViewById(R.id.frame_layout),
                                this@MainActivity.getString(R.string.ytld_update_success) + " [${version}]",
                                Snackbar.LENGTH_LONG).show()
                        }
                    }
                }

            }
        }



    }
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBundle("nav_state", navController.saveState())
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        navController.restoreState(savedInstanceState.getBundle("nav_state"))
    }

    private fun View.visibilityChanged(action: (View) -> Unit) {
        this.viewTreeObserver.addOnGlobalLayoutListener {
            val newVis: Int = this.visibility
            if (this.tag as Int? != newVis) {
                this.tag = this.visibility
                // visibility has changed
                action(this)
            }
        }
    }


    fun hideBottomNavigation(){
        if (navigationView is BottomNavigationView){
            findViewById<FragmentContainerView>(R.id.frame_layout).updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
            navigationView.animate()?.translationY(navigationView.height.toFloat())?.setDuration(300)?.withEndAction {
                navigationView.visibility = View.GONE
            }?.start()
        }else if (navigationView is NavigationRailView){
            findViewById<FragmentContainerView>(R.id.frame_layout).updateLayoutParams {
                this.width = LayoutParams.MATCH_PARENT
            }
            navigationView.animate()?.translationX(-navigationView.width.toFloat())?.setDuration(300)?.withEndAction {
                navigationView.visibility = View.GONE
            }?.start()
        }
    }

    fun showBottomNavigation(){
        if (navigationView is BottomNavigationView){
            findViewById<FragmentContainerView>(R.id.frame_layout).updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomToTop = R.id.bottomNavigationView
                bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            }
            navigationView.animate()?.translationY(0F)?.setDuration(300)?.withEndAction {
                navigationView.visibility = View.VISIBLE
            }?.start()
        }else if (navigationView is NavigationRailView){
            findViewById<FragmentContainerView>(R.id.frame_layout).updateLayoutParams {
                this.width = 0
            }
            navigationView.animate()?.translationX(0F)?.setDuration(300)?.withEndAction {
                navigationView.visibility = View.VISIBLE
            }?.start()
        }
    }

    fun disableBottomNavigation(){
        if (navigationView is NavigationBarView){
            (navigationView as NavigationBarView).menu.forEach { it.isEnabled = false }
        }else{
            (navigationView as NavigationView).menu.forEach { it.isEnabled = false }
        }
    }

    fun enableBottomNavigation(){
        if (navigationView is NavigationBarView){
            (navigationView as NavigationBarView).menu.forEach { it.isEnabled = true }
        }else{
            (navigationView as NavigationView).menu.forEach { it.isEnabled = true }
        }
    }

    override fun onResume() {
        super.onResume()
        //incognito header
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

                if (preferences.getString("preferred_download_type", "video") == "command"){
                    val f = File(FileUtil.formatPath(uri?.path ?: ""))
                    if (!f.exists()){
                        Toast.makeText(context, "Couldn't read file", Toast.LENGTH_LONG).show()
                        return
                    }
                    val bundle = Bundle()
                    bundle.putParcelable("result", downloadViewModel.createEmptyResultItem(f.absolutePath))
                    bundle.putSerializable("type", DownloadViewModel.Type.command)
                    navController.navigate(R.id.downloadBottomSheetDialog, bundle)
                }else{
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
                    val bundle = Bundle()
                    bundle.putString("url", textBuilder.toString())
                    navController.popBackStack(R.id.homeFragment, true)
                    navController.navigate(
                        R.id.homeFragment,
                        bundle
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun checkUpdate() {
        if (preferences.getBoolean("update_app", false)) {
            val updateUtil = UpdateUtil(this)
            CoroutineScope(Dispatchers.IO).launch{
                updateUtil.updateApp{}
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
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkNotificationPermission()){
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()){
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                1
            )
        }
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
                    == PackageManager.PERMISSION_GRANTED) &&
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

    companion object {
        private const val TAG = "MainActivity"
    }
}