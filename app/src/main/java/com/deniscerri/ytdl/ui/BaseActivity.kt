package com.deniscerri.ytdl.ui

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.ThemeUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import kotlin.system.exitProcess

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.updateTheme(this)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        super.onCreate(savedInstanceState)
    }

    fun askPermissions() {
        val permissions = arrayListOf<String>()
        if (!checkFilePermission()) {
            if (Build.VERSION.SDK_INT >= 33){
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                if (Build.VERSION.SDK_INT >= 34){
                    permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
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

    private fun exit() {
        finishAffinity()
        exitProcess(0)
    }

}