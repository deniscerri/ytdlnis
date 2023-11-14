package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.Extensions.forceFastScrollMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class ActiveDownloadsFragment : Fragment(), ActiveDownloadAdapter.OnItemClickListener, OnClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var activeRecyclerView : RecyclerView
    private lateinit var activeDownloads : ActiveDownloadAdapter
    lateinit var downloadItem: DownloadItem
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var pauseResume: MaterialButton
    private lateinit var noResults: RelativeLayout
    private lateinit var workManager: WorkManager


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_active, container, false)
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity = getActivity()
        notificationUtil = NotificationUtil(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        workManager = WorkManager.getInstance(requireContext())

        activeDownloads =
            ActiveDownloadAdapter(
                this,
                requireActivity()
            )

        activeRecyclerView = view.findViewById(R.id.download_recyclerview)
        activeRecyclerView.forceFastScrollMode()
        activeRecyclerView.adapter = activeDownloads
        activeRecyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        pauseResume = view.findViewById(R.id.pause_resume)
        noResults = view.findViewById(R.id.no_results)

        pauseResume.setOnClickListener {
            if (pauseResume.text == requireContext().getString(R.string.pause)){
                lifecycleScope.launch {
                    workManager.cancelAllWorkByTag("download")
                    pauseResume.isEnabled = false

                    // pause queued
                    withContext(Dispatchers.IO){
                        downloadViewModel.getQueued()
                    }.forEach {
                        it.status = DownloadRepository.Status.QueuedPaused.toString()
                        downloadViewModel.updateDownload(it)
                    }

                    // pause active
                    withContext(Dispatchers.IO){
                        downloadViewModel.getActiveDownloads()
                    }.forEach {
                        cancelItem(it.id.toInt())
                        it.status = DownloadRepository.Status.ActivePaused.toString()
                        downloadViewModel.updateDownload(it)
                    }



                    activeDownloads.notifyDataSetChanged()
                    pauseResume.isEnabled = true
                }
            }else{
                lifecycleScope.launch {
                    pauseResume.isEnabled = false

                    val active = withContext(Dispatchers.IO){
                        downloadViewModel.getActiveDownloads()
                    }

                    val toQueue = active.filter { it.status == DownloadRepository.Status.ActivePaused.toString() }.toMutableList()
                    toQueue.forEach {
                        it.status = DownloadRepository.Status.Queued.toString()
                        downloadViewModel.updateDownload(it)
                    }

                    runBlocking {
                        downloadViewModel.queueDownloads(listOf())
                    }

                    val queuedItems = withContext(Dispatchers.IO){
                        downloadViewModel.getQueued()
                    }
                    queuedItems.map {
                        it.status = DownloadRepository.Status.Queued.toString()
                        downloadViewModel.updateDownload(it)
                    }

                    pauseResume.isEnabled = true
                }
            }
        }

        WorkManager.getInstance(requireContext())
            .getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.RUNNING))
            .observe(viewLifecycleOwner){ list ->
                list.forEach {work ->
                    if (work == null) return@forEach
                    val id = work.progress.getLong("id", 0L)
                    if(id == 0L) return@forEach

                    val progress = work.progress.getInt("progress", 0)
                    val output = work.progress.getString("output")

                    val progressBar = view.findViewWithTag<LinearProgressIndicator>("$id##progress")
                    val outputText = view.findViewWithTag<TextView>("$id##output")

                    requireActivity().runOnUiThread {
                        try {
                            progressBar?.setProgressCompat(progress, true)
                            outputText?.text = output
                        }catch (ignored: Exception) {}
                    }
                }
            }

        lifecycleScope.launch {
            downloadViewModel.activeDownloadsCount.collectLatest {
                delay(200)
                noResults.visibility = if (it == 0) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloads.collectLatest {
                delay(100)
                activeDownloads.submitList(it)

                if (it.size > 1){
                    pauseResume.visibility = View.VISIBLE
                    if (it.all { l -> l.status == DownloadRepository.Status.ActivePaused.toString() }){
                        pauseResume.text = requireContext().getString(R.string.resume)
                    }else{
                        pauseResume.text = requireContext().getString(R.string.pause)
                    }
                }else{
                    pauseResume.visibility = View.GONE
                }
            }

        }
    }

    override fun onCancelClick(itemID: Long) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO){
                downloadViewModel.getActiveDownloads()
            }.count()

            if (count == 1){
                val queue = withContext(Dispatchers.IO){
                    val list = downloadViewModel.getQueued().toMutableList()
                    list.map { it.status = DownloadRepository.Status.Queued.toString() }
                    list
                }

                runBlocking {
                    downloadViewModel.queueDownloads(queue)
                }
            }

        }

        cancelDownload(itemID)
    }

    override fun onPauseClick(itemID: Long, action: ActiveDownloadAdapter.ActiveDownloadAction, position: Int) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }

            when(action){
                ActiveDownloadAdapter.ActiveDownloadAction.Pause -> {
                    lifecycleScope.launch {
                        cancelItem(itemID.toInt())
                        item.status = DownloadRepository.Status.ActivePaused.toString()
                        withContext(Dispatchers.IO){
                            downloadViewModel.updateDownload(item)
                        }
                    }
                }
                ActiveDownloadAdapter.ActiveDownloadAction.Resume -> {
                    lifecycleScope.launch {
                        item.status = DownloadRepository.Status.PausedReQueued.toString()
                        withContext(Dispatchers.IO){
                            downloadViewModel.updateDownload(item)
                        }

                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(item))
                        }

                        withContext(Dispatchers.IO){
                            downloadViewModel.getQueued().filter { it.status != DownloadRepository.Status.Queued.toString() }.forEach {
                                it.status = DownloadRepository.Status.Queued.toString()
                                downloadViewModel.updateDownload(it)
                            }
                        }
                    }
                }
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

    private fun cancelItem(id: Int){
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        notificationUtil.cancelDownloadNotification(id)
    }

    private fun cancelDownload(itemID: Long){
        lifecycleScope.launch {
            cancelItem(itemID.toInt())
            withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }.let {
                it.status = DownloadRepository.Status.Cancelled.toString()
                withContext(Dispatchers.IO){
                    downloadViewModel.updateDownload(it)
                }
            }
        }
    }

    override fun onClick(p0: View?) {
    }
}