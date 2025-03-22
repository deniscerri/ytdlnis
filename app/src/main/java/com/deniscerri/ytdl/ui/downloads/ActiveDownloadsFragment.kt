package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdl.util.Extensions.forceFastScrollMode
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class ActiveDownloadsFragment : Fragment(), ActiveDownloadAdapter.OnItemClickListener {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel

    //private lateinit var activeDownloadsLinear: LinearLayout
    private lateinit var activeRecyclerView : RecyclerView
    private lateinit var activeDownloads : ActiveDownloadAdapter

    private lateinit var notificationUtil: NotificationUtil
    private lateinit var pauseBtn: ExtendedFloatingActionButton
    private lateinit var resumeBtn: ExtendedFloatingActionButton
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

    @OptIn(ExperimentalBadgeUtils::class)
    @SuppressLint("NotifyDataSetChanged", "RestrictedApi")
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

        pauseBtn = view.findViewById(R.id.pause)
        pauseBtn.isVisible = false
        resumeBtn = view.findViewById(R.id.resume)
        noResults = view.findViewById(R.id.no_results)

        downloadViewModel.pausedAllDownloads.observe(viewLifecycleOwner) { value ->
            lifecycleScope.launch {
                when(value) {
                    DownloadViewModel.PausedAllDownloadsState.PAUSE -> {
                        resumeBtn.isVisible = false
                        pauseBtn.isClickable = true
                        val typedValue = TypedValue()
                        requireContext().theme.resolveAttribute(R.attr.colorPrimaryContainer, typedValue, true)
                        pauseBtn.backgroundTintList = ColorStateList.valueOf(typedValue.data)
                        pauseBtn.isVisible = true
                        activeDownloads.notifyDataSetChanged()
                    }
                    DownloadViewModel.PausedAllDownloadsState.RESUME -> {
                        pauseBtn.isVisible = false
                        resumeBtn.isClickable = true
                        val typedValue = TypedValue()
                        requireContext().theme.resolveAttribute(R.attr.colorPrimaryContainer, typedValue, true)
                        resumeBtn.backgroundTintList = ColorStateList.valueOf(typedValue.data)
                        resumeBtn.isVisible = true
                        activeDownloads.notifyDataSetChanged()
                    }
                    DownloadViewModel.PausedAllDownloadsState.PROCESSING -> {
                        pauseBtn.isClickable = false
                        pauseBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.grey))

                        resumeBtn.isClickable = false
                        resumeBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.grey))
                    }
                    else -> {
                        pauseBtn.isVisible = false
                        resumeBtn.isVisible = false
                    }
                }
            }
        }

        pauseBtn.setOnClickListener {
            lifecycleScope.launch {
                downloadViewModel.pauseAllDownloads()
            }
        }

        resumeBtn.setOnClickListener {
            lifecycleScope.launch {
                downloadViewModel.resumeAllDownloads()
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activePausedDownloads.collectLatest {
                noResults.isVisible = it.isEmpty()
                activeDownloads.submitList(it)
                activeRecyclerView.scrollTo(0,0)
            }
        }

    }

    //dont remove
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

    override fun onPauseClick(itemID: Long) {
        lifecycleScope.launch {
            YoutubeDL.getInstance().destroyProcessById(itemID.toString())
            notificationUtil.cancelDownloadNotification(itemID.toInt())
            withContext(Dispatchers.IO){
                downloadViewModel.updateToStatus(itemID, DownloadRepository.Status.Paused)
            }
        }
    }

    override fun onResumeClick(itemID: Long) {
        downloadViewModel.resumeDownload(itemID)
    }

}