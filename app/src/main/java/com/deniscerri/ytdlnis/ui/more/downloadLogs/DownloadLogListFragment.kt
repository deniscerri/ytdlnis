package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.DownloadLogsAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File


class DownloadLogListFragment : Fragment(), DownloadLogsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadLogAdapter: DownloadLogsAdapter
    private lateinit var noResults: RelativeLayout
    private lateinit var fileList: MutableList<File>
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var logFolder : File
    private lateinit var mainActivity: MainActivity
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
        return inflater.inflate(R.layout.fragment_download_log_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topAppBar = view.findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }

        downloadLogAdapter =
            DownloadLogsAdapter(
                this,
                mainActivity
            )
        recyclerView = view.findViewById(R.id.logs_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = downloadLogAdapter
        noResults = view.findViewById(R.id.no_results)
        noResults.visibility = View.GONE

        logFolder = File(requireContext().filesDir.absolutePath + "/logs")
        updateList(logFolder)

        if(Build.VERSION.SDK_INT < 29){
            val observer: FileObserver = object : FileObserver(logFolder.absolutePath) {
                override fun onEvent(event: Int, path: String?) {
                    when(event) {
                        CREATE, DELETE -> updateList(logFolder)
                    }
                }
            }
            observer.startWatching()
        }else{
            val observer: FileObserver = object : FileObserver(logFolder) {
                override fun onEvent(event: Int, path: String?) {
                    when(event) {
                        CREATE, DELETE -> updateList(logFolder)
                    }
                }
            }
            observer.startWatching()
        }
        initMenu(logFolder)
    }

    private fun initMenu(logFolder: File) {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.remove_logs) {
                try{
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                    deleteDialog.setMessage(getString(R.string.confirm_delete_logs_desc))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        logFolder.listFiles()!!.forEach {
                            it.delete()
                        }.run {
                            updateList(logFolder)
                        }
                    }
                    deleteDialog.show()
                }catch (e: Exception){
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }
            }
            true
        }
    }

    private fun updateList(logFolder: File){
        fileList = mutableListOf()
        try{
            fileList.addAll(logFolder.listFiles()!!)
            fileList.sortByDescending { it.lastModified()}
        }catch (e: Exception){
            e.printStackTrace()
        }
        downloadLogAdapter.submitList(fileList.toList())
        mainActivity.runOnUiThread{
            if (fileList.isNotEmpty()) {
                noResults.visibility = View.GONE
                topAppBar.menu.findItem(R.id.remove_logs).isVisible = true
            }else{
                topAppBar.menu.findItem(R.id.remove_logs).isVisible = false
                noResults.visibility = View.VISIBLE
            }
        }
    }

    override fun onItemClick(file: File) {
        val bundle = Bundle()
        bundle.putString("logpath", file.absolutePath)
        findNavController().navigate(
            R.id.downloadLogFragment,
            bundle
        )
    }

    override fun onDeleteClick(file: File) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + file.name + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            file.delete().run {
                updateList(logFolder)
            }
        }
        deleteDialog.show()
    }
}