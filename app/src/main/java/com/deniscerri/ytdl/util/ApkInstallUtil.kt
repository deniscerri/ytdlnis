package com.deniscerri.ytdl.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.Extensions.hasPermission
import rikka.shizuku.Shizuku
import java.io.File
import java.lang.reflect.Method

object ApkInstallUtil {

    // Holds the callback for the currently in-flight "system" install,
    // since ActivityResultLauncher's own callback is registered once and
    // can't take a per-call lambda directly.
    private var pendingInstallCallback: ((Result<Unit>) -> Unit)? = null

    private var pendingShizukuPermissionCallback: ((Boolean) -> Unit)? = null

    /**
     * Call this once per Activity/Fragment (e.g. in onCreate) to create the launcher.
     * Wires the launcher's result back into whatever callback was passed to installApk().
     */
    fun registerInstallLauncher(
        caller: androidx.activity.result.ActivityResultCaller
    ): ActivityResultLauncher<Intent> {
        return caller.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            pendingInstallCallback?.invoke(
                if (success) Result.success(Unit)
                else Result.failure(Exception("Install cancelled or failed"))
            )
            pendingInstallCallback = null
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE_SHIZUKU) return@OnRequestPermissionResultListener

            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            val callback = pendingShizukuPermissionCallback
            pendingShizukuPermissionCallback = null

            callback?.let {
                Handler(Looper.getMainLooper()).post { it(granted) }
            }
        }

    fun registerShizukuPermissionListener() {
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
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    onResult(true, null)
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    onResult(false, "Shizuku permission was denied. Please enable it manually.")
                }
                else -> {
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


    fun installApk(
        context: Context,
        apkFile: File,
        installLauncher: ActivityResultLauncher<Intent>,
        onResult: (Result<Unit>) -> Unit
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val installMethod = preferences.getString("apk_install_method", "system")

        when (installMethod) {
            "system" -> {
                val canRequestPackageInstalls = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.Manifest.permission.REQUEST_INSTALL_PACKAGES.hasPermission(context)
                } else {
                    true
                }

                if (canRequestPackageInstalls) {
                    val contentUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apkFile)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    pendingInstallCallback = onResult // set BEFORE launch
                    installLauncher.launch(intent)
                } else {
                    onResult(Result.failure(Exception(context.getString(R.string.system_install_failed))))
                }
            }
            "shizuku" -> {
                requestShizukuPermission { granted, error ->
                    if (!granted) {
                        onResult(Result.failure(Exception(error)))
                        return@requestShizukuPermission
                    }
                    Thread {
                        val result = installApkWithShizuku(apkFile)
                        Handler(Looper.getMainLooper()).post { onResult(result) }
                    }.start()
                }
            }
            "external" -> {
                val packageName = preferences.getString("apk_install_external_apk_id", "")!!
                if (packageName.isEmpty()) {
                    onResult(Result.failure(Exception("External Installer not configured!")))
                    return
                }
                installApkWithExternalInstaller(context, apkFile, packageName, onResult)
            }
            else -> onResult(Result.success(Unit))
        }
    }

    const val REQUEST_CODE_SHIZUKU = 1001

    fun checkShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            return false // Shizuku service is not running
        }

        return if (Shizuku.isPreV11()) {
            false
        } else {
            when {
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> true
                Shizuku.shouldShowRequestPermissionRationale() -> false
                else -> {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                    false
                }
            }
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
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }

            var isReceiverRegistered = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, i: Intent) {
                    val installedPkg = i.data?.schemeSpecificPart
                    if (installedPkg == apkPackageName) {
                        if (isReceiverRegistered) {
                            isReceiverRegistered = false
                            try {
                                context.unregisterReceiver(this)
                            } catch (e: IllegalArgumentException) {
                                // already unregistered elsewhere — safe to ignore
                            }
                        }
                        Handler(Looper.getMainLooper()).post {
                            onResult(Result.success(Unit))
                        }
                    }
                }
            }

            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }

            context.startActivity(intent)

            // No cancellation broadcast exists, so time out after a while and
            // just stop listening; caller won't get an explicit failure signal here.
            Handler(Looper.getMainLooper()).postDelayed({
                if (isReceiverRegistered) {
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                    isReceiverRegistered = false
                }
            }, 5 * 60_000L)

        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }
}