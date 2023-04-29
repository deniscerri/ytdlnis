package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.DownloadLogsAdapter
import com.deniscerri.ytdlnis.ui.BaseActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File


class DownloadLogListActivity : BaseActivity(), DownloadLogsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadLogAdapter: DownloadLogsAdapter
    private lateinit var noResults: RelativeLayout
    private lateinit var fileList: MutableList<File>
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var logFolder : File
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_log_list)
        context = baseContext

        topAppBar = findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        downloadLogAdapter =
            DownloadLogsAdapter(
                this,
                this@DownloadLogListActivity
            )
        recyclerView = findViewById(R.id.logs_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = downloadLogAdapter
        noResults = findViewById(R.id.no_results)
        noResults.visibility = View.GONE

        logFolder = File(filesDir.absolutePath + "/logs")
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
                    val deleteDialog = MaterialAlertDialogBuilder(this)
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
        runOnUiThread{
            if (fileList.isNotEmpty()) {
                noResults.visibility = View.GONE
                topAppBar.menu.findItem(R.id.remove_logs).isVisible = true
            }else{
                topAppBar.menu.findItem(R.id.remove_logs).isVisible = false
                noResults.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }

    override fun onItemClick(file: File) {
        val intent = Intent(this, DownloadLogActivity::class.java)
        intent.putExtra("logpath", file.absolutePath)
        startActivity(intent)
    }

    override fun onDeleteClick(file: File) {
        val deleteDialog = MaterialAlertDialogBuilder(this)
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