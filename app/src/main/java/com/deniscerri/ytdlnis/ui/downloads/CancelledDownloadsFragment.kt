package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class CancelledDownloadsFragment : Fragment(), GenericDownloadAdapter.OnItemClickListener {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var cancelledRecyclerView : RecyclerView
    private lateinit var cancelledDownloads : GenericDownloadAdapter
    private lateinit var items : List<DownloadItem>
    private lateinit var fileUtil: FileUtil
    private lateinit var uiUtil : UiUtil

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_generic_download_queue, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        items = listOf()
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cancelledDownloads =
            GenericDownloadAdapter(
                this,
                requireActivity()
            )

        cancelledRecyclerView = view.findViewById(R.id.download_recyclerview)
        cancelledRecyclerView.adapter = cancelledDownloads

        val landScapeOrTablet = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || resources.getBoolean(R.bool.isTablet)
        if (landScapeOrTablet){
            cancelledRecyclerView.layoutManager = GridLayoutManager(context, 2)
        }else{
            cancelledRecyclerView.layoutManager = LinearLayoutManager(context)
        }

        downloadViewModel.cancelledDownloads.observe(viewLifecycleOwner) {
            items = it
            cancelledDownloads.submitList(it)
        }
    }

    override fun onActionButtonClick(itemID: Long) {
        val item = items.find { it.id == itemID }
        if (item != null){
            downloadViewModel.queueDownloads(listOf(item))
        }else{
            Toast.makeText(requireContext(), getString(R.string.error_restarting_download), Toast.LENGTH_LONG).show()
        }
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

        val time = bottomSheet.findViewById<Chip>(R.id.time)
        val formatNote = bottomSheet.findViewById<Chip>(R.id.format_note)
        val codec = bottomSheet.findViewById<Chip>(R.id.codec)
        val fileSize = bottomSheet.findViewById<Chip>(R.id.file_size)

        time!!.visibility = View.GONE

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
            removeItem(item, bottomSheet)
        }
        val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.visibility = View.GONE

        val redownload = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
        redownload!!.tag = itemID
        redownload.setOnClickListener{
            downloadViewModel.queueDownloads(listOf(item))
            bottomSheet.cancel()
        }

        openFile.visibility = View.GONE

        bottomSheet.show()
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }


    private fun removeItem(item: DownloadItem, bottomSheet: BottomSheetDialog?){
        bottomSheet?.hide()
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            downloadViewModel.deleteDownload(item)
        }
        deleteDialog.show()
    }
}