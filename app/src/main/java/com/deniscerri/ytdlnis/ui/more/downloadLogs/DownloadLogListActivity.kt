package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.Context
import android.content.Intent
import android.os.*
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.DownloadLogsAdapter
import com.google.android.material.appbar.MaterialToolbar
import java.io.File


class DownloadLogListActivity : AppCompatActivity(), DownloadLogsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadLogAdapter: DownloadLogsAdapter
    private lateinit var noResults: RelativeLayout
    private lateinit var fileList: MutableList<File>
    private lateinit var topAppBar: MaterialToolbar
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

        val logFolder = File(filesDir.absolutePath + "/logs")
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
                    logFolder.listFiles()!!.forEach {
                        it.delete()
                    }
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
            fileList.reverse()
        }catch (ignored: Exception){}
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
        intent.putExtra("path", file.absolutePath)
        startActivity(intent)
    }

    override fun onDeleteClick(file: File) {
        file.delete()
    }
}