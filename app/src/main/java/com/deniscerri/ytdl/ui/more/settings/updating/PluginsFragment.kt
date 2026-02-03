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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.plugins.NodeJS
import com.deniscerri.ytdl.database.models.PluginItem
import com.deniscerri.ytdl.ui.adapter.PluginsAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.google.android.material.bottomsheet.BottomSheetDialog


class PluginsFragment : Fragment(), PluginsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: PluginsAdapter
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences

    private var tmpItem: PluginItem? = null
    private var plugins: List<PluginItem> = mutableListOf()

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

        val nodeJSInstance = NodeJS.getInstance()

        plugins = listOf(
            PluginItem("NodeJS", nodeJSInstance.currentVersion, nodeJSInstance)
        )
        listAdapter.submitList(plugins)
    }

    override fun onCardClick(item: PluginItem) {

        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.plugin_releases_bottom_sheet)

        bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)?.text = item.title
        bottomSheet.findViewById<Button>(R.id.bottomsheet_import_zip)?.setOnClickListener {
            tmpItem = item
            bottomSheet.dismiss()

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            importPluginZipLauncher.launch(intent)
        }

        //TODO SHOW RELEASES LIST

        bottomSheet.show()
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

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
                        val result = item.instance.installFromZip(requireContext(), this)
                        if (result.isSuccess) {
                            val idx = plugins.indexOfFirst { it2 -> it2.title == item.title }
                            plugins[idx].version = result.getOrNull() ?: ""
                            listAdapter.submitList(plugins)
                        }
                    }
                }
            }
        }
    }
}