package com.deniscerri.ytdlnis.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdlnis.adapter.QueuedDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadQueueActivity : AppCompatActivity(), ActiveDownloadAdapter.OnItemClickListener, QueuedDownloadAdapter.OnItemClickListener {
    private lateinit var activeRecyclerView: RecyclerView
    private lateinit var activeDownloads: ActiveDownloadAdapter
    private lateinit var queuedRecyclerView: RecyclerView
    private lateinit var queuedDownloads: QueuedDownloadAdapter
    private lateinit var noResults: RelativeLayout
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var notificationUtil: NotificationUtil

    private lateinit var runningLayout: LinearLayout
    private lateinit var queuedLayout: LinearLayout
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_queue)
        context = baseContext
        val view : View = window.decorView.findViewById(android.R.id.content)


        topAppBar = findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        activeDownloads =
            ActiveDownloadAdapter(
                this,
                this@DownloadQueueActivity
            )

        queuedDownloads =
            QueuedDownloadAdapter(
                this,
                this@DownloadQueueActivity
            )


        activeRecyclerView = findViewById(R.id.active_recyclerview)
        activeRecyclerView.layoutManager = LinearLayoutManager(context)
        activeRecyclerView.adapter = activeDownloads

        queuedRecyclerView = findViewById(R.id.queued_recyclerview)
        queuedRecyclerView.layoutManager = LinearLayoutManager(context)
        queuedRecyclerView.adapter = queuedDownloads

        noResults = findViewById(R.id.no_results)
        noResults.visibility = View.GONE

        runningLayout = findViewById(R.id.running_layout)
        queuedLayout = findViewById(R.id.queued_layout)

        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        notificationUtil = NotificationUtil(this)


        //active downloads livedata
        downloadViewModel.activeDownloads.observe(this) {
            activeDownloads.submitList(it)
            if (it.isEmpty()){
                if (queuedLayout.visibility == View.GONE) noResults.visibility = View.VISIBLE
                runningLayout.visibility = View.GONE
            }else{
                noResults.visibility = View.GONE
                runningLayout.visibility = View.VISIBLE

                it.forEach{item ->
                    WorkManager.getInstance(this)
                        .getWorkInfosForUniqueWorkLiveData(item.id.toString())
                        .observe(this){ list ->
                            list.forEach {work ->
                                if (work == null) return@observe
                                val id = work.progress.getLong("id", 0L)
                                if(id == 0L) return@observe

                                val progress = work.progress.getInt("progress", 0)
                                val output = work.progress.getString("output")

                                val progressBar = view.findViewWithTag<LinearProgressIndicator>("$id##progress")
                                val outputText = view.findViewWithTag<TextView>("$id##output")

                                runOnUiThread {
                                    progressBar.setProgressCompat(progress, true)
                                    outputText.text = output
                                }
                            }
                        }
                }
            }
        }

        //queued downloads livedata
        downloadViewModel.queuedDownloads.observe(this) {
            queuedDownloads.submitList(it)
            if (it.isEmpty()){
                if (runningLayout.visibility == View.GONE) noResults.visibility = View.VISIBLE
                queuedLayout.visibility = View.GONE
            }else{
                noResults.visibility = View.GONE
                queuedLayout.visibility = View.VISIBLE
            }
        }
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


    companion object {
        private const val TAG = "DownloadQueueActivity"
    }

    override fun onCancelClick(itemID: Long) {
        cancelDownload(itemID)
    }

    override fun onQueuedCancelClick(itemID: Long) {
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                downloadViewModel.deleteDownload(downloadViewModel.getItemByID(itemID))
            }
        }
        cancelDownload(itemID)
    }

    private fun cancelDownload(itemID: Long){
        val id = itemID.toInt()
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        WorkManager.getInstance(this).cancelUniqueWork(id.toString())
        notificationUtil.cancelDownloadNotification(id)
    }
}