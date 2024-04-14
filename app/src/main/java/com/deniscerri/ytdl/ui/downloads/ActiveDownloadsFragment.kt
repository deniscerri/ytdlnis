package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdl.ui.adapter.QueuedDownloadAdapter
import com.deniscerri.ytdl.util.Extensions.forceFastScrollMode
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


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
                pause.isEnabled = false

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
                pause.isEnabled = true
            }
        }

        resume.setOnClickListener {
            lifecycleScope.launch {
                resume.isEnabled = false

                val active = withContext(Dispatchers.IO){
                    downloadViewModel.getActiveDownloads()
                }

                val toQueue = active.filter { it.status == DownloadRepository.Status.ActivePaused.toString() }.toMutableList()
                runBlocking {
                    toQueue.forEach {
                        it.status = DownloadRepository.Status.Queued.toString()
                        downloadViewModel.updateDownload(it)
                    }
                }

                runBlocking {
                    downloadViewModel.startDownloadWorker(listOf())
                }

                val queuedItems = withContext(Dispatchers.IO){
                    downloadViewModel.getQueued()
                }
                queuedItems.map {
                    it.status = DownloadRepository.Status.Queued.toString()
                    downloadViewModel.updateDownload(it)
                }

                resume.isEnabled = true
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
            downloadViewModel.activeAndActivePausedDownloadsCount.collectLatest {
                noResults.isVisible = it == 0
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloadsCount.collectLatest {
                pause.isVisible = it > 0
            }
        }


        lifecycleScope.launch {
            downloadViewModel.pausedDownloadsCount.collectLatest {
                resume.isVisible = it > 0
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloads.collectLatest {
                delay(100)
                activeDownloads.submitList(it)
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
}