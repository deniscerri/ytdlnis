package com.deniscerri.ytdl.ui.more.settings.updating

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.packages.Aria2c
import com.deniscerri.ytdl.core.packages.FFmpeg
import com.deniscerri.ytdl.core.packages.NodeJS
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.core.packages.Python
import com.deniscerri.ytdl.database.models.PackageItem
import com.deniscerri.ytdl.ui.adapter.PackageReleaseAdapter
import com.deniscerri.ytdl.ui.adapter.PackagesAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri


class PackagesFragment : Fragment(), PackagesAdapter.OnItemClickListener, PackageReleaseAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: PackagesAdapter
    private lateinit var releaseAdapter: PackageReleaseAdapter
    private var bottomSheet: BottomSheetDialog? = null
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences

    private var tmpItem: PackageItem? = null
    private var tmpInstance: PackageBase? = null
    private var tmpDownloadJob: Job? = null
    private var packages: List<PackageItem> = mutableListOf()
    private var packageReleases: List<PackageBase.PackageRelease> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.packages))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_packages, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listAdapter = PackagesAdapter(this, settingsActivity)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()

        packages = listOf(
            PackageItem("Python", Python),
            PackageItem("FFmpeg", FFmpeg),
            PackageItem("Aria2c", Aria2c),
            PackageItem("NodeJS", NodeJS)
        )
        listAdapter.submitList(packages)
    }

    override fun onCardClick(item: PackageItem, location: PackageBase.PackageLocation) {
        tmpItem = item

        val sheet = BottomSheetDialog(requireContext())
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        sheet.setContentView(R.layout.plugin_releases_bottom_sheet)

        sheet.findViewById<TextView>(R.id.bottom_sheet_subtitle)?.text = item.title

        val loader = sheet.findViewById<CircularProgressIndicator>(R.id.loader)
        val noResults = sheet.findViewById<View>(R.id.no_results)
        packageReleases = mutableListOf()
        releaseAdapter = PackageReleaseAdapter(this@PackagesFragment, requireActivity())
        releaseAdapter.setPackageLocation(location)

        val releaseRecyclerView = sheet.findViewById<RecyclerView>(R.id.recyclerView)!!
        releaseRecyclerView.adapter = releaseAdapter

        lifecycleScope.launch {
            val instance = tmpItem!!.getInstance()
            instance.getReleases().apply {
                this.onFailure {
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), it.message ?: getString(R.string.errored), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                packageReleases = this.getOrElse { listOf() }
                releaseAdapter.submitList(packageReleases)
                releaseRecyclerView.isVisible = packageReleases.isNotEmpty()
                loader?.isVisible = false
                noResults?.isVisible = packageReleases.isEmpty()
            }
        }

        sheet.setOnDismissListener {
            bottomSheet = null
        }

        sheet.show()
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        sheet.behavior.peekHeight = displayMetrics.heightPixels
        sheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        bottomSheet = sheet
    }

    private var uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (tmpInstance?.isPackageAppInstalled(requireContext()) == false) {
            val resp = tmpInstance?.uninstallDownloadedRuntimeDir(requireContext())
            resp?.onFailure {
                Snackbar.make(requireView(), it.message ?: getString(R.string.errored), Snackbar.LENGTH_LONG).show()
            }
            resp?.onSuccess {
                bottomSheet?.dismiss()
                listAdapter.notifyDataSetChanged()
                RuntimeManager.reInit(requireContext())
            }
        }
    }

    override fun onDeleteDownloadedPackageClick(item: PackageBase.PackageRelease) {
        deleteDownloadedVersion(tmpItem!!, item.version)
    }

    override fun onDeleteDownloadedVersion(item: PackageItem, currentVersion: String?) {
        deleteDownloadedVersion(item, currentVersion)
    }

    private fun deleteDownloadedVersion(item: PackageItem, version: String?) {
        UiUtil.showGenericDeleteDialog(
            requireContext(),
            "${item.title} (${version})"
        ) {
            val instance = item.getInstance()
            tmpInstance = instance
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${instance.apkPackage}")
            }
            uninstallLauncher.launch(intent)
        }
    }

    override fun onDownloadReleaseClick(item: PackageBase.PackageRelease) {
        var positiveButton: Button? = null
        val updateDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${item.tag_name} (${FileUtil.convertFileSize(item.downloadSize)})")
            .setMessage(item.body)
            .setIcon(R.drawable.ic_update_app)
            .setNegativeButton(requireContext().getString(R.string.cancel)) { _: DialogInterface?, _: Int ->
                tmpDownloadJob?.cancel()
            }
            .setPositiveButton(requireContext().getString(R.string.download), null)
        val view = updateDialog.show()
        val textView = view.findViewById<TextView>(android.R.id.message)
        textView!!.movementMethod = LinkMovementMethod.getInstance()
        val mw = Markwon.builder(requireContext()).usePlugin(object: AbstractMarkwonPlugin() {

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    requireContext().startActivity(browserIntent)
                }
            }
        }).build()
        mw.setMarkdown(textView, item.body)

        positiveButton = view.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton?.setOnClickListener {
            positiveButton.isEnabled = false
            positiveButton.text = "0%"

            tmpDownloadJob = lifecycleScope.launch {
                val instance = tmpItem!!.getInstance()
                val fileResp = instance.downloadReleaseApk(requireContext(), item) { progress ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            positiveButton.text = "$progress%"
                        }
                    }
                }

                fileResp.onFailure {
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            view.dismiss()
                            Snackbar.make(requireView(), it.message ?: getString(R.string.errored), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                fileResp.onSuccess { file ->
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(file.uri, "application/vnd.android.package-archive");
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
            }
        }
    }
}