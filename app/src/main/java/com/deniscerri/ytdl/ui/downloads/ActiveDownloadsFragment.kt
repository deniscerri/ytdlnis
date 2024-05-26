package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdl.util.Extensions.forceFastScrollMode
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class ActiveDownloadsFragment : Fragment(), ActiveDownloadAdapter.OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel

    private lateinit var activeRecyclerView : RecyclerView
    private lateinit var activeDownloads : ActiveDownloadAdapter

    private lateinit var notificationUtil: NotificationUtil
    private lateinit var pause: ExtendedFloatingActionButton
    private lateinit var resume: ExtendedFloatingActionButton
    private lateinit var noResults: RelativeLayout
    private lateinit var workManager: WorkManager
    private lateinit var preferences: SharedPreferences


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_active, container, false)
        return fragmentView
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity = getActivity()
        notificationUtil = NotificationUtil(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        workManager = WorkManager.getInstance(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        activeDownloads = ActiveDownloadAdapter(this,requireActivity())
        activeRecyclerView = view.findViewById(R.id.download_recyclerview)
        activeRecyclerView.forceFastScrollMode()
        activeRecyclerView.adapter = activeDownloads
        activeRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        pause = view.findViewById(R.id.pause)
        pause.isVisible = false
        resume = view.findViewById(R.id.resume)
        noResults = view.findViewById(R.id.no_results)

        pause.setOnClickListener {
            lifecycleScope.launch {
                workManager.cancelAllWorkByTag("download")
                withContext(Dispatchers.IO){
                    downloadViewModel.getActiveDownloads()
                }.forEach {
                    YoutubeDL.getInstance().destroyProcessById(it.id.toString())
                }
                preferences.edit().putBoolean("paused_downloads", true).apply()
                activeDownloads.notifyDataSetChanged()
                resume.isVisible = true
                pause.isVisible = false
            }
        }

        resume.setOnClickListener {
            lifecycleScope.launch {
                preferences.edit().putBoolean("paused_downloads", false).apply()
                resume.isVisible = false
                pause.isVisible = true
                withContext(Dispatchers.IO) {
                    downloadViewModel.resetActivePaused()
                    downloadViewModel.startDownloadWorker(listOf())
                    withContext(Dispatchers.Main){
                        activeDownloads.notifyDataSetChanged()
                    }
                }
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloadsCount.collectLatest {
                noResults.isVisible = it == 0
                val pausedDownloads = preferences.getBoolean("paused_downloads", false)
                pause.isVisible = it > 0 && !pausedDownloads
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeQueuedDownloadsCount.collectLatest {
                val pausedDownloads = preferences.getBoolean("paused_downloads", false)
                resume.isVisible = it > 0 && pausedDownloads
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloads.collectLatest {
                delay(100)
                activeDownloads.submitList(it)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadProgressEvent(event: DownloadWorker.WorkerProgress) {
        val progressBar = requireView().findViewWithTag<LinearProgressIndicator>("${event.downloadItemID}##progress")
        val outputText = requireView().findViewWithTag<TextView>("${event.downloadItemID}##output")

        requireActivity().runOnUiThread {
            try {
                progressBar?.setProgressCompat(event.progress, true)
                outputText?.text = event.output
            }catch (ignored: Exception) {}
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onCancelClick(itemID: Long) {
        lifecycleScope.launch {
            YoutubeDL.getInstance().destroyProcessById(itemID.toString())
            notificationUtil.cancelDownloadNotification(itemID.toInt())

            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }
            item.status = DownloadRepository.Status.Cancelled.toString()
            withContext(Dispatchers.IO){
                downloadViewModel.updateDownload(item)
            }

            val activeCount = withContext(Dispatchers.IO){
                downloadViewModel.getActiveDownloadsCount()
            }

            if (activeCount == 0){
                val queuedCount = withContext(Dispatchers.IO){
                    downloadViewModel.getQueuedDownloadsCount()
                }
                if (queuedCount == 0) {
                    preferences.edit().putBoolean("paused_downloads", false).apply()
                }
            }

            if (activeCount == 1){
                val queue = withContext(Dispatchers.IO){
                    downloadViewModel.getQueued()
                }

                if (!preferences.getBoolean("paused_downloads", false)){
                    runBlocking {
                        downloadViewModel.queueDownloads(queue)
                    }
                }
            }

        }
    }

    override fun onResumeClick(itemID: Long) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }
            withContext(Dispatchers.IO){
                downloadViewModel.queueDownloads(listOf(item))
            }
        }
    }

    override fun onOutputClick(item: DownloadItem) {
        if (item.logID != null && item.logID != 0L) {
            val bundle = Bundle()
            bundle.putLong("logID", item.logID!!)
            findNavController().navigate(
                R.id.downloadLogFragment,
                bundle
            )
        }
    }

}