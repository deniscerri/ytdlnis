package com.deniscerri.ytdl.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.Extensions.hasPermission
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import rikka.shizuku.Shizuku
import java.io.File
import java.lang.reflect.Method

object ApkInstallUtil {

    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PREF_METHOD = "apk_install_method"
    private const val PREF_EXTERNAL = "apk_install_external_apk_id"
    const val REQUEST_CODE_SHIZUKU = 1001

    // The system install launcher is registered once per Activity/Fragment,
    // so the per-call result handler is parked here while the installer is open.
    private var pendingInstallCallback: ((resultCode: Int, data: Intent?) -> Unit)? = null

    private var pendingShizukuPermissionCallback: ((Boolean) -> Unit)? = null

    /** One row of the installer picker. [method] is what gets saved: system, shizuku or external. */
    class InstallerEntry(
        val method: String,
        val packageName: String?,
        val label: String,
        val description: String,
        val icon: Drawable?,
        val available: Boolean
    )

    /**
     * Call this once per Activity/Fragment (e.g. in onCreate) to create the launcher.
     */
    fun registerInstallLauncher(
        caller: androidx.activity.result.ActivityResultCaller
    ): ActivityResultLauncher<Intent> {
        return caller.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = pendingInstallCallback
            pendingInstallCallback = null
            callback?.invoke(result.resultCode, result.data)
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE_SHIZUKU) return@OnRequestPermissionResultListener

            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            val callback = pendingShizukuPermissionCallback
            pendingShizukuPermissionCallback = null

            callback?.let {
                Handler(Looper.getMainLooper()).post { it(granted) }
            }
        }

    fun registerShizukuPermissionListener() {
        // remove first so registering from several screens never adds it twice
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    fun unregisterShizukuPermissionListener() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    fun requestShizukuPermission(onResult: (granted: Boolean, error: String?) -> Unit) {
        if (!Shizuku.pingBinder()) {
            onResult(false, "Please start the Shizuku service first")
            return
        }
        if (Shizuku.isPreV11()) {
            onResult(false, "Shizuku version not supported")
            return
        }

        try {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    onResult(true, null)
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    onResult(false, "Shizuku permission was denied. Please enable it manually.")
                }
                else -> {
                    // registered here too, so the request also works outside SettingsActivity
                    registerShizukuPermissionListener()
                    pendingShizukuPermissionCallback = { granted ->
                        if (granted) onResult(true, null)
                        else onResult(false, "Shizuku permission denied")
                    }
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                }
            }
        } catch (e: IllegalStateException) {
            // Binder wasn't actually available despite pingBinder() check —
            // service likely died/restarted between the check and this call.
            pendingShizukuPermissionCallback = null
            onResult(false, "Shizuku service is not ready. Please try again.")
        }
    }

    // FOSS builds don't declare REQUEST_INSTALL_PACKAGES, so the system installer rejects them
    private fun canUseSystemInstaller(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                android.Manifest.permission.REQUEST_INSTALL_PACKAGES.hasPermission(context)
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess
    }

    @Suppress("DEPRECATION")
    private fun findExternalInstallers(context: Context): List<InstallerEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://dummy/dummy.apk"), APK_MIME)

        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo }
            .filter { it.exported && it.packageName != context.packageName }
            // the stock installer is already the "System" entry
            .filterNot { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && it.packageName.contains("packageinstaller") }
            // an app that can't install packages itself would just fail the same way we do
            .filter { info ->
                runCatching {
                    pm.getPackageInfo(info.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
                        ?.any { it == android.Manifest.permission.REQUEST_INSTALL_PACKAGES || it == "android.permission.INSTALL_PACKAGES" }
                }.getOrNull() == true
            }
            .distinctBy { it.packageName }
            .map { InstallerEntry("external", it.packageName, it.loadLabel(pm).toString(), it.packageName, it.loadIcon(pm), true) }
            .sortedBy { it.label.lowercase() }
    }

    fun listInstallers(context: Context): List<InstallerEntry> {
        val systemOk = canUseSystemInstaller(context)
        val genericIcon = AppCompatResources.getDrawable(context, R.drawable.baseline_install_mobile_24)
        val shizukuIcon = runCatching { context.packageManager.getApplicationIcon(SHIZUKU_PACKAGE) }.getOrNull()

        return listOf(
            InstallerEntry(
                "system", null, context.getString(R.string.system),
                context.getString(if (systemOk) R.string.installer_system_description else R.string.installer_system_unavailable),
                genericIcon, systemOk
            ),
            // not disabled when stopped: Sui has no app and the service can be started later
            InstallerEntry(
                "shizuku", null, "Shizuku",
                context.getString(if (Shizuku.pingBinder()) R.string.installer_shizuku_description else R.string.installer_shizuku_not_running),
                shizukuIcon ?: genericIcon, true
            )
        ) + findExternalInstallers(context)
    }

    fun getSelectedInstallerLabel(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return when (preferences.getString(PREF_METHOD, "system")) {
            "system" -> context.getString(R.string.system)
            "shizuku" -> "Shizuku"
            "external" -> {
                val pkg = preferences.getString(PREF_EXTERNAL, "")!!
                runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(context.getString(R.string.notset))
            }
            else -> context.getString(R.string.notset)
        }
    }

    /**
     * Single dialog listing the system installer, Shizuku and every installer app on the device.
     * [onDone] gets true when a usable installer was picked and saved, false when dismissed.
     */
    fun showInstallerPickerDialog(activity: Activity, onDone: (picked: Boolean) -> Unit) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val currentMethod = preferences.getString(PREF_METHOD, "system")
        val currentExternal = preferences.getString(PREF_EXTERNAL, "")

        var picked = false
        val list = activity.layoutInflater.inflate(R.layout.choose_installer_app_dialog, null) as LinearLayout
        val scroll = NestedScrollView(activity).apply { addView(list) }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.installation_method)
            .setIcon(R.drawable.baseline_install_mobile_24)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { onDone(picked) }
            .create()

        fun save(entry: InstallerEntry) {
            preferences.edit()
                .putString(PREF_METHOD, entry.method)
                .apply { if (entry.packageName != null) putString(PREF_EXTERNAL, entry.packageName) }
                .apply()
            picked = true
            dialog.dismiss()
        }

        val strokePx = (2 * activity.resources.displayMetrics.density).toInt()
        listInstallers(activity).forEach { entry ->
            val card = activity.layoutInflater.inflate(R.layout.installer_app_card, list, false) as MaterialCardView
            card.findViewById<TextView>(R.id.app_name).text = entry.label
            card.findViewById<TextView>(R.id.app_description).text = entry.description
            card.findViewById<ShapeableImageView>(R.id.app_icon).setImageDrawable(entry.icon)

            val selected = entry.method == currentMethod && (entry.method != "external" || entry.packageName == currentExternal)
            card.isChecked = selected
            if (selected) card.strokeWidth = strokePx

            if (!entry.available) {
                card.isEnabled = false
                card.alpha = 0.5f
            } else {
                card.setOnClickListener {
                    if (entry.method == "shizuku") {
                        requestShizukuPermission { granted, error ->
                            if (granted) save(entry)
                            else Toast.makeText(activity, error ?: "Shizuku permission not granted", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        save(entry)
                    }
                }
            }
            list.addView(card)
        }

        dialog.show()
    }

    // finishes the install that is currently waiting on an installer, if any
    private var activeInstall: ((Result<Unit>) -> Unit)? = null
    private const val ABANDON_GRACE_MS = 3000L

    /** Stop waiting for the current install (e.g. the user pressed Cancel on the "please wait" dialog). */
    fun cancelPendingInstall(context: Context) {
        activeInstall?.invoke(Result.failure(Exception(context.getString(R.string.install_cancelled))))
    }

    /**
     * When the installer runs in its own task (external installer apps, some OEM system installers),
     * the user can come back to us without finishing it and no result ever arrives.
     * So once we were left and are resumed again, give late results a short grace period, then give up.
     */
    private fun failWhenAbandoned(context: Context, owner: LifecycleOwner?, finish: (Result<Unit>) -> Unit) {
        owner ?: return
        val handler = Handler(Looper.getMainLooper())
        var left = false
        owner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> left = true
                    Lifecycle.Event.ON_RESUME -> if (left) {
                        owner.lifecycle.removeObserver(this)
                        handler.postDelayed({
                            finish(Result.failure(Exception(context.getString(R.string.install_cancelled))))
                        }, ABANDON_GRACE_MS)
                    }
                    Lifecycle.Event.ON_DESTROY -> owner.lifecycle.removeObserver(this)
                    else -> {}
                }
            }
        })
    }

    /**
     * [lifecycleOwner] is the screen showing the install progress; with it, returning to the app
     * without finishing the installer ends the install as cancelled instead of waiting forever.
     */
    fun installApk(
        context: Context,
        apkFile: File,
        installLauncher: ActivityResultLauncher<Intent>,
        lifecycleOwner: LifecycleOwner? = null,
        onResult: (Result<Unit>) -> Unit
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val installMethod = preferences.getString(PREF_METHOD, "system")
        val externalPackage = preferences.getString(PREF_EXTERNAL, "")!!

        val usable = when (installMethod) {
            "system" -> canUseSystemInstaller(context)
            "shizuku" -> true
            "external" -> externalPackage.isNotEmpty() && isInstalled(context, externalPackage)
            else -> false
        }

        // saved method can't work here (e.g. "system" on FOSS, or the installer app got uninstalled): let the user pick
        if (!usable) {
            val activity = context as? Activity
            if (activity == null) {
                onResult(Result.failure(Exception(context.getString(R.string.system_install_failed))))
                return
            }
            showInstallerPickerDialog(activity) { picked ->
                if (picked) installApk(context, apkFile, installLauncher, lifecycleOwner, onResult)
                else onResult(Result.failure(Exception(context.getString(R.string.install_cancelled))))
            }
            return
        }

        // results can arrive from several places (installer result, broadcast, timeout, cancel, abandon); only the first counts
        var done = false
        lateinit var finish: (Result<Unit>) -> Unit
        finish = { result ->
            if (!done) {
                done = true
                if (activeInstall === finish) activeInstall = null
                onResult(result)
            }
        }
        activeInstall = finish

        when (installMethod) {
            "system" -> {
                failWhenAbandoned(context, lifecycleOwner, finish)
                installApkWithSystemInstaller(context, apkFile, installLauncher, finish)
            }
            // no installer UI to abandon: Shizuku installs in the background
            "shizuku" -> {
                requestShizukuPermission { granted, error ->
                    if (!granted) {
                        finish(Result.failure(Exception(error)))
                        return@requestShizukuPermission
                    }
                    Thread {
                        val result = installApkWithShizuku(apkFile)
                        Handler(Looper.getMainLooper()).post { finish(result) }
                    }.start()
                }
            }
            "external" -> {
                failWhenAbandoned(context, lifecycleOwner, finish)
                installApkWithExternalInstaller(context, apkFile, externalPackage, finish)
            }
        }
    }

    private fun installApkWithSystemInstaller(
        context: Context,
        apkFile: File,
        installLauncher: ActivityResultLauncher<Intent>,
        onResult: (Result<Unit>) -> Unit
    ) {
        // the permission is declared but the user hasn't allowed "install unknown apps" yet
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            onResult(Result.failure(Exception(context.getString(R.string.allow_install_unknown_apps))))
            return
        }

        val contentUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apkFile)
        // ACTION_VIEW never returns a result, so failures looked like success.
        // ACTION_INSTALL_PACKAGE + EXTRA_RETURN_RESULT reports OK / cancelled / failed with a code.
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(contentUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        }

        pendingInstallCallback = { resultCode, data ->
            when (resultCode) {
                Activity.RESULT_OK -> onResult(Result.success(Unit))
                Activity.RESULT_CANCELED -> onResult(Result.failure(Exception(context.getString(R.string.install_cancelled))))
                else -> {
                    val code = data?.getIntExtra("android.intent.extra.INSTALL_RESULT", 0) ?: 0
                    onResult(Result.failure(Exception(describeInstallError(context, code))))
                }
            }
        }
        runCatching { installLauncher.launch(intent) }.onFailure {
            pendingInstallCallback = null
            onResult(Result.failure(it))
        }
    }

    // codes are PackageManager.INSTALL_FAILED_* (hidden API constants)
    private fun describeInstallError(context: Context, code: Int): String {
        return when (code) {
            -7 -> context.getString(R.string.install_failed_incompatible)
            -25 -> context.getString(R.string.install_failed_downgrade)
            -16, -113 -> context.getString(R.string.install_failed_abi)
            -4 -> context.getString(R.string.install_failed_storage)
            else -> context.getString(R.string.install_failed_code, code.toString())
        }
    }

    @Throws(Exception::class)
    private fun newShizukuProcess(cmd: Array<String>, env: Array<String>?, dir: String?): Process {
        val method: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmd, env, dir) as Process
    }

    private fun installApkWithShizuku(apkFile: File): Result<Unit> {
        return try {
            val apkSize = apkFile.length()
            val command = arrayOf("pm", "install", "-r", "-S", apkSize.toString())
            val process: Process = newShizukuProcess(command, null, null)

            process.outputStream.use { stdin ->
                apkFile.inputStream().use { input ->
                    input.copyTo(stdin)
                }
            }

            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.contains("Success")) Result.success(Unit)
            else Result.failure(Exception(errorOutput.ifBlank { output }))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun installApkWithExternalInstaller(
        context: Context,
        apkFile: File,
        targetInstallerPackage: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val apkPackageName = info?.packageName

            if (apkPackageName == null) {
                onResult(Result.failure(Exception("Could not read APK package info")))
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                `package` = targetInstallerPackage
                setDataAndType(apkUri, APK_MIME)
                // some installers only read the uri from clipData
                clipData = ClipData.newRawUri("APK", apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
            context.grantUriPermission(targetInstallerPackage, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }

            val appContext = context.applicationContext
            var isReceiverRegistered = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, i: Intent) {
                    val installedPkg = i.data?.schemeSpecificPart
                    if (installedPkg == apkPackageName && isReceiverRegistered) {
                        isReceiverRegistered = false
                        runCatching { appContext.unregisterReceiver(this) }
                        Handler(Looper.getMainLooper()).post {
                            onResult(Result.success(Unit))
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }

            context.startActivity(intent)

            // shortcut: external installers send no cancel/fail signal, so a timeout is the only end; poll the package's versionCode if this proves too coarse
            Handler(Looper.getMainLooper()).postDelayed({
                if (isReceiverRegistered) {
                    isReceiverRegistered = false
                    runCatching { appContext.unregisterReceiver(receiver) }
                    onResult(Result.failure(Exception(context.getString(R.string.installer_no_confirmation))))
                }
            }, 5 * 60_000L)

        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }
}
