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
import java.io.File
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.WelcomeViewModel
import com.deniscerri.ytdl.util.FileUtil
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
        val request: () -> Unit,
        val summaryText: (() -> String)? = null
    ) {
        lateinit var action: MaterialButton
        lateinit var requiredBadge: TextView
        lateinit var summaryView: TextView
    }

    /** Same as the plain tree picker, but the grant can be kept across restarts. */
    private class PersistableTree : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
    }

    // folder preferences a restored backup may point to, with the title shown for each
    private val folderPrefs = listOf(
        Triple("music_path", R.drawable.ic_music_downloaded, R.string.music_directory),
        Triple("video_path", R.drawable.ic_video_downloaded, R.string.video_directory),
        Triple("command_path", R.drawable.ic_terminal, R.string.command_directory),
        Triple("cache_path", R.drawable.baseline_folder_24, R.string.cache_directory),
        Triple("download_archive_path", R.drawable.baseline_archive_24, R.string.download_archive_folder),
        Triple("backup_path", R.drawable.baseline_folder_24, R.string.backup_path)
    )

    private var pendingFolderKey: String? = null

    private val folderLauncher = registerForActivityResult(PersistableTree()) { uri ->
        val key = pendingFolderKey
        pendingFolderKey = null
        if (uri != null && key != null) {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            // a different folder than the restored one replaces it
            preferences().edit(commit = true) { putString(key, uri.toString()) }
        }
        render()
    }

    private fun preferences() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    private fun hasFolderAccess(path: String): Boolean {
        if (path.startsWith("content://")) {
            val uri = path.toUri()
            val granted = requireContext().contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (granted) return true
        }
        val dir = File(FileUtil.formatPath(path))
        if (!dir.exists()) {
            // on Android 11+ the app's own Android/data folder is only created by the system when
            // asked for it, a plain mkdirs() below it fails on a fresh install
            runCatching { requireContext().getExternalFilesDirs(null) }
            runCatching { dir.mkdirs() }
        }
        return dir.canWrite()
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

        // only a restored backup can bring folders the app has no access to
        val viewModel = ViewModelProvider(requireActivity())[WelcomeViewModel::class.java]
        val folderRows = mutableListOf<Row>()
        if (viewModel.restoredSettings) {
            folderPrefs.forEach { (key, icon, title) ->
                val path = preferences().getString(key, "").orEmpty()
                val formattedPath = FileUtil.formatPath(path)
                runCatching { File(formattedPath).mkdirs() }
                val row = Row(
                    "folder_$key", icon, title, 0,
                    required = true,
                    isGranted = { hasFolderAccess(preferences().getString(key, "").orEmpty()) },
                    request = {
                        pendingFolderKey = key
                        folderLauncher.launch(preferences().getString(key, "").orEmpty().toUri())
                    },
                    summaryText = { FileUtil.formatPath(preferences().getString(key, "").orEmpty()) }
                )
                if (row.isGranted()) return@forEach

                folderRows += row
            }
        }

        val inflater = LayoutInflater.from(context)
        fun addRow(row: Row) {
            val item = inflater.inflate(R.layout.item_welcome_permission, list, false)
            item.findViewById<ImageView>(R.id.permission_icon).setImageResource(row.icon)
            item.findViewById<TextView>(R.id.permission_title).setText(row.title)
            row.summaryView = item.findViewById(R.id.permission_summary)
            if (row.summary != 0) row.summaryView.setText(row.summary)
            row.requiredBadge = item.findViewById(R.id.permission_required)
            row.action = item.findViewById<MaterialButton>(R.id.permission_action).apply {
                setOnClickListener { row.request() }
            }
            list.addView(item)
        }
        rows.forEach(::addRow)

        if (folderRows.isNotEmpty()) {
            list.addView(TextView(context).apply {
                setText(R.string.welcome_perm_folders_header)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            list.addView(TextView(context).apply {
                setText(R.string.welcome_perm_folders_summary)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            folderRows.forEach(::addRow)
            rows += folderRows
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
            row.summaryText?.let { row.summaryView.text = it() }
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
