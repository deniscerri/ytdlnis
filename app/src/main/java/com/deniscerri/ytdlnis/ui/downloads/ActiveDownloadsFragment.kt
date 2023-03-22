package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yausername.youtubedl_android.YoutubeDL


class ActiveDownloadsFragment() : Fragment(), ActiveDownloadAdapter.OnItemClickListener, OnClickListener {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var activeRecyclerView : RecyclerView
    private lateinit var activeDownloads : ActiveDownloadAdapter
    lateinit var downloadItem: DownloadItem
    private lateinit var notificationUtil: NotificationUtil


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_generic_download_queue, container, false)
        activity = getActivity()
        notificationUtil = NotificationUtil(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activeDownloads =
            ActiveDownloadAdapter(
                this,
                requireActivity()
            )

        activeRecyclerView = view.findViewById(R.id.download_recyclerview)
        activeRecyclerView.adapter = activeDownloads

        val landScapeOrTablet = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || resources.getBoolean(R.bool.isTablet)
        if (landScapeOrTablet){
            activeRecyclerView.layoutManager = GridLayoutManager(context, 2)
        }else{
            activeRecyclerView.layoutManager = LinearLayoutManager(context)
        }

        downloadViewModel.activeDownloads.observe(viewLifecycleOwner) {
            activeDownloads.submitList(it)
            it.forEach{item ->
                WorkManager.getInstance(requireContext())
                    .getWorkInfosForUniqueWorkLiveData(item.id.toString())
                    .observe(viewLifecycleOwner){ list ->
                        list.forEach {work ->
                            if (work == null) return@observe
                            val id = work.progress.getLong("id", 0L)
                            if(id == 0L) return@observe

                            val progress = work.progress.getInt("progress", 0)
                            val output = work.progress.getString("output")
                            val log = work.progress.getBoolean("log", false)

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
            }
        }
    }

    override fun onCancelClick(itemID: Long) {
        cancelDownload(itemID)
    }

    private fun cancelDownload(itemID: Long){
        val id = itemID.toInt()
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        WorkManager.getInstance(requireContext()).cancelUniqueWork(id.toString())
        notificationUtil.cancelDownloadNotification(id)
    }

    override fun onClick(p0: View?) {
        TODO("Not yet implemented")
    }
}