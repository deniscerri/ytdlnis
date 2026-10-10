package com.deniscerri.ytdl.ui.welcome

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.deniscerri.ytdl.R
import com.google.android.material.button.MaterialButton

/**
 * Asks for the permissions the app uses. Only storage on Android 10 and below is required,
 * everything else can be skipped and changed later in the system settings.
 */
class WelcomePermissionsFragment : Fragment(R.layout.fragment_welcome_permissions) {

    private class Row(
        val key: String,
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val summary: Int,
        val required: Boolean,
        val isGranted: () -> Boolean,
        val request: () -> Unit
    ) {
        lateinit var action: MaterialButton
        lateinit var requiredBadge: TextView
    }

    private val rows = mutableListOf<Row>()

    // once Android stops showing the dialog, the button has to send the user to the app settings
    private val permanentlyDenied = mutableSetOf<String>()

    private val storagePermissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val storageLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onRuntimeResult("storage", storagePermissions)
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (Build.VERSION.SDK_INT >= 33) {
                onRuntimeResult("notifications", arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val list = view.findViewById<LinearLayout>(R.id.permission_list)

        rows.clear()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            rows += Row(
                "storage", R.drawable.outline_storage_24,
                R.string.welcome_perm_storage, R.string.welcome_perm_storage_summary,
                required = true,
                isGranted = { storagePermissions.all { hasPermission(it) } },
                request = { requestRuntime("storage") { storageLauncher.launch(storagePermissions) } }
            )
        }
        if (Build.VERSION.SDK_INT >= 33) {
            rows += Row(
                "notifications", R.drawable.ic_notifications,
                R.string.welcome_perm_notifications, R.string.welcome_perm_notifications_summary,
                required = false,
                isGranted = { hasPermission(Manifest.permission.POST_NOTIFICATIONS) },
                request = {
                    requestRuntime("notifications") {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
        rows += Row(
            "battery", R.drawable.ic_battery,
            R.string.welcome_perm_battery, R.string.welcome_perm_battery_summary,
            required = false,
            isGranted = {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
            },
            request = ::requestIgnoreBatteryOptimizations
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rows += Row(
                "alarms", R.drawable.baseline_access_alarm_24,
                R.string.welcome_perm_alarms, R.string.welcome_perm_alarms_summary,
                required = false,
                isGranted = {
                    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                },
                request = ::requestExactAlarms
            )
        }

        val inflater = LayoutInflater.from(context)
        rows.forEach { row ->
            val item = inflater.inflate(R.layout.item_welcome_permission, list, false)
            item.findViewById<ImageView>(R.id.permission_icon).setImageResource(row.icon)
            item.findViewById<TextView>(R.id.permission_title).setText(row.title)
            item.findViewById<TextView>(R.id.permission_summary).setText(row.summary)
            row.requiredBadge = item.findViewById(R.id.permission_required)
            row.action = item.findViewById<MaterialButton>(R.id.permission_action).apply {
                setOnClickListener { row.request() }
            }
            list.addView(item)
        }
    }

    // the user may come back from a system settings screen, so state is always read fresh
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        rows.forEach { row ->
            val granted = row.isGranted()
            row.action.isEnabled = !granted
            row.action.setText(if (granted) R.string.welcome_allowed else R.string.welcome_allow)
            row.requiredBadge.isVisible = row.required && !granted
        }
        (activity as? WelcomeActivity)?.setNextEnabled(rows.filter { it.required }.all { it.isGranted() })
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntime(key: String, launch: () -> Unit) {
        if (key in permanentlyDenied) openAppSettings() else launch()
    }

    private fun onRuntimeResult(key: String, permissions: Array<String>) {
        val denied = permissions.filter { !hasPermission(it) }
        // after a refusal, "no rationale" means Android will not ask again
        if (denied.isNotEmpty() && denied.none { shouldShowRequestPermissionRationale(it) }) {
            permanentlyDenied += key
        }
        render()
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${requireContext().packageName}"))
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${requireContext().packageName}"))
        runCatching { startActivity(intent) }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", requireContext().packageName, null))
        runCatching { startActivity(intent) }
    }
}
