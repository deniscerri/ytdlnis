package com.deniscerri.ytdl.ui.more.settings.updating

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
import com.deniscerri.ytdl.core.plugins.Aria2c
import com.deniscerri.ytdl.core.plugins.FFmpeg
import com.deniscerri.ytdl.core.plugins.NodeJS
import com.deniscerri.ytdl.core.plugins.PluginBase
import com.deniscerri.ytdl.core.plugins.Python
import com.deniscerri.ytdl.database.models.PluginItem
import com.deniscerri.ytdl.ui.adapter.PluginReleaseAdapter
import com.deniscerri.ytdl.ui.adapter.PluginsAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import junit.runner.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PluginsFragment : Fragment(), PluginsAdapter.OnItemClickListener, PluginReleaseAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: PluginsAdapter
    private lateinit var releaseAdapter: PluginReleaseAdapter
    private var bottomSheet: BottomSheetDialog? = null
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences

    private var tmpItem: PluginItem? = null
    private var plugins: List<PluginItem> = mutableListOf()
    private var pluginReleases: List<PluginBase.PluginRelease> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.plugins))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_plugins, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listAdapter = PluginsAdapter(this, settingsActivity)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()

        plugins = listOf(
            PluginItem("Python", Python),
            PluginItem("FFmpeg", FFmpeg),
            PluginItem("Aria2c", Aria2c),
            PluginItem("NodeJS", NodeJS)
        )
        listAdapter.submitList(plugins)
    }

    override fun onCardClick(item: PluginItem) {
        tmpItem = item

        val sheet = BottomSheetDialog(requireContext())
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        sheet.setContentView(R.layout.plugin_releases_bottom_sheet)

        sheet.findViewById<TextView>(R.id.bottom_sheet_subtitle)?.text = item.title
        sheet.findViewById<Button>(R.id.bottomsheet_import_zip)?.setOnClickListener {
            sheet.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            importPluginZipLauncher.launch(intent)
        }

        val loader = sheet.findViewById<CircularProgressIndicator>(R.id.loader)
        val noResults = sheet.findViewById<View>(R.id.no_results)
        pluginReleases = mutableListOf()
        releaseAdapter = PluginReleaseAdapter(this@PluginsFragment, requireActivity())

        val releaseRecyclerView = sheet.findViewById<RecyclerView>(R.id.recyclerView)!!
        releaseRecyclerView.adapter = releaseAdapter

        lifecycleScope.launch {
            val instance = tmpItem!!.getInstance()
            instance.getReleases().apply {
                pluginReleases = this.toMutableList()
                releaseAdapter.submitList(pluginReleases)
                releaseRecyclerView.isVisible = pluginReleases.isNotEmpty()
                loader?.isVisible = false
                noResults?.isVisible = pluginReleases.isEmpty()
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

    private var importPluginZipLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                tmpItem?.let { item ->
                    DocumentFile.fromSingleUri(requireContext(), it)?.apply {
                        val instance = item.getInstance()
                        val result = instance.installFromZip(requireContext(), this)
                        if (result.isSuccess) {
                            bottomSheet?.dismiss()
                            listAdapter.notifyDataSetChanged()
                            RuntimeManager.reInit(requireContext())
                        }
                    }
                }
            }
        }
    }

    private fun deleteDownloadedVersion(item: PluginItem, version: String?) {
        UiUtil.showGenericDeleteDialog(
            requireContext(),
            "${item.title} (${version})"
        ) {
            val instance = item.getInstance()
            val resp = instance.uninstall(requireContext())
            resp.onFailure {
                Snackbar.make(requireView(), it.message ?: getString(R.string.errored), Snackbar.LENGTH_LONG).show()
            }
            resp.onSuccess {
                bottomSheet?.dismiss()
                listAdapter.notifyDataSetChanged()
                RuntimeManager.reInit(requireContext())
            }
        }
    }

    override fun onDeleteReleaseClick(item: PluginBase.PluginRelease) {
        deleteDownloadedVersion(tmpItem!!, item.version)
    }

    override fun onDeleteDownloadedVersion(item: PluginItem, currentVersion: String?) {
        deleteDownloadedVersion(item, currentVersion)
    }

    override fun onDownloadReleaseClick(item: PluginBase.PluginRelease) {
        lifecycleScope.launch {
            val instance = tmpItem!!.getInstance()

            val file = instance.downloadRelease(requireContext(), item) {
            }

            val resp = instance.installFromZip(requireContext(), file!!, item.version)
            resp.onFailure {
                Snackbar.make(requireView(), it.message ?: getString(R.string.errored), Snackbar.LENGTH_LONG).show()
            }
            resp.onSuccess {
                bottomSheet?.dismiss()
                listAdapter.notifyDataSetChanged()
                RuntimeManager.reInit(requireContext())
            }
        }
    }
}