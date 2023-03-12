package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class QueuedDownloadsFragment : Fragment(), GenericDownloadAdapter.OnItemClickListener {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var queuedRecyclerView : RecyclerView
    private lateinit var queuedDownloads : GenericDownloadAdapter
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var items : List<DownloadItem>
    private lateinit var fileUtil: FileUtil
    private lateinit var uiUtil: UiUtil

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
        items = listOf()
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queuedDownloads =
            GenericDownloadAdapter(
                this,
                requireActivity()
            )

        queuedRecyclerView = view.findViewById(R.id.download_recyclerview)
        queuedRecyclerView.adapter = queuedDownloads

        val landScapeOrTablet = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || resources.getBoolean(R.bool.isTablet)
        if (landScapeOrTablet){
            queuedRecyclerView.layoutManager = GridLayoutManager(context, 2)
        }else{
            queuedRecyclerView.layoutManager = LinearLayoutManager(context)
        }

        downloadViewModel.queuedDownloads.observe(viewLifecycleOwner) {
            items = it
            queuedDownloads.submitList(it)
        }
    }

    override fun onActionButtonClick(itemID: Long) {
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                downloadViewModel.deleteDownload(downloadViewModel.getItemByID(itemID))
            }
        }
        cancelDownload(itemID)
    }

    override fun onCardClick(itemID: Long) {
        val item = items.find { it.id == itemID } ?: return

        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)
        val title = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = item.title
        val author = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = item.author

        // BUTTON ----------------------------------
        val buttonLayout = bottomSheet.findViewById<LinearLayout>(R.id.downloads_download_button_layout)
        val btn = buttonLayout!!.findViewById<MaterialButton>(R.id.downloads_download_button_type)

        if (item.type == DownloadViewModel.Type.audio) {
            btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_music)
        } else if (item.type == DownloadViewModel.Type.video) {
            btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_video)
        }else{
            btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_terminal)
        }

        val formatNote = bottomSheet.findViewById<TextView>(R.id.format_note)
        val codec = bottomSheet.findViewById<TextView>(R.id.codec)
        val fileSize = bottomSheet.findViewById<TextView>(R.id.file_size)

        if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
            View.GONE
        else formatNote!!.text = item.format.format_note

        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none"){
            codec!!.visibility = View.GONE
        }else{
            codec!!.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSizeReadable = fileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize!!.visibility = View.GONE
        else fileSize!!.text = fileSizeReadable

        val link = bottomSheet.findViewById<Button>(R.id.bottom_sheet_link)
        val url = item.url
        link!!.text = url
        link.tag = itemID
        link.setOnClickListener{
            uiUtil.openLinkIntent(requireContext(), item.url, bottomSheet)
        }
        link.setOnLongClickListener{
            uiUtil.copyLinkToClipBoard(requireContext(), item.url, bottomSheet)
            true
        }
        val remove = bottomSheet.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = itemID
        remove.setOnClickListener{
            cancelDownload(itemID)
        }
        val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.visibility = View.GONE

        val redownload = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
        redownload!!.visibility = View.GONE

        bottomSheet.show()
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun cancelDownload(itemID: Long){
        val id = itemID.toInt()
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        WorkManager.getInstance(requireContext()).cancelUniqueWork(id.toString())
        notificationUtil.cancelDownloadNotification(id)
    }

}