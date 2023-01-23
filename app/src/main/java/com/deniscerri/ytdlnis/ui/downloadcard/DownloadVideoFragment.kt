package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadVideoFragment(private val downloadItem: DownloadItem) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private lateinit var resultViewModel : ResultViewModel
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var fileUtil : FileUtil

    private lateinit var title : TextInputLayout
    private lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch{
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(downloadItem.id)
            }
            if (::title.isInitialized){
                title.editText!!.setText(item.title)
                downloadItem.title = item.title
            }
            if(::author.isInitialized){
                author.editText!!.setText(item.author)
                downloadItem.author = item.author
            }
        }
        downloadViewModel.updateDownload(downloadItem)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_video, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        fileUtil = FileUtil()
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val resultItem = withContext(Dispatchers.IO){
                resultViewModel.getItemByURL(downloadItem.url)
            }
            val type = downloadItem.type

            val sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            try {
                title = view.findViewById(R.id.title_textinput)
                title.editText!!.setText(downloadItem.title)
                title.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.title = p0.toString()
                        resultItem.title = p0.toString()
                        resultViewModel.update(resultItem)
                        downloadViewModel.updateDownload(downloadItem)
                    }
                })

                author = view.findViewById(R.id.author_textinput)
                author.editText!!.setText(downloadItem.author)
                author.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.author = p0.toString()
                        resultItem.author = p0.toString()
                        resultViewModel.update(resultItem)
                        downloadViewModel.updateDownload(downloadItem)
                    }
                })

                saveDir = view.findViewById(R.id.outputPath)
                val downloadPath = sharedPreferences.getString(
                    "video_path",
                    getString(R.string.video_path)
                )
                downloadItem.downloadPath = downloadPath!!
                downloadViewModel.updateDownload(downloadItem)
                saveDir.editText!!.setText(
                    fileUtil.formatPath(downloadPath)
                )
                saveDir.editText!!.isFocusable = false;
                saveDir.editText!!.isClickable = true;
                saveDir.editText!!.setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    videoPathResultLauncher.launch(intent)
                }

                val formats =  withContext(Dispatchers.IO){
                    resultViewModel.getFormats(resultItem, type)
                }

                val formatTitles = formats.map {
                    if (it.format_note.contains("AUDIO_QUALITY_"))
                        it.format_note.replace("AUDIO_QUALITY_", "") +
                                if (it.filesize == 0L) "" else " / " + fileUtil.convertFileSize(it.filesize)
                    else it.format_note +
                            if (it.filesize == 0L) "" else " / " + fileUtil.convertFileSize(it.filesize)
                }

                val format = view.findViewById<TextInputLayout>(R.id.format)
                val autoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.format_textview)
                autoCompleteTextView?.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        formatTitles
                    )
                )
                if (formatTitles.isNotEmpty()) {
                    autoCompleteTextView!!.setText(formatTitles[formats.lastIndex], false)
                }
                (format!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.formatDesc = formats[index].format_note
                        downloadItem.videoFormatId = formats[index].format_id
                    }

                val containers = requireContext().resources.getStringArray(R.array.video_containers)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)

                container?.isEnabled = true
                containerAutoCompleteTextView?.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        containers
                    )
                )
                val selectedContainer: String =
                    formats.find { downloadItem.formatDesc == it.format_note }?.container
                        ?: sharedPreferences.getString("video_format", "DEFAULT")!!
                containerAutoCompleteTextView!!.setText(selectedContainer, false)
                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.ext = containers[index]
                    }

                val embedSubs = view.findViewById<Chip>(R.id.embed_subtitles)
                embedSubs!!.isChecked = sharedPreferences.getBoolean("embed_subtitles", false)

                val addChapters = view.findViewById<Chip>(R.id.add_chapters)
                addChapters!!.isChecked = sharedPreferences.getBoolean("add_chapters", false)

                val saveThumbnail = view.findViewById<Chip>(R.id.save_thumbnail)
                saveThumbnail!!.isChecked = sharedPreferences.getBoolean("write_thumbnail", false)


                val cancel = view.findViewById<Button>(R.id.bottomsheet_cancel_button)
                cancel!!.setOnClickListener {
                    (parentFragmentManager.findFragmentByTag("bottomSheet") as DownloadBottomSheetDialog).dismissSelf()
                }
                val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
                download!!.setOnClickListener {
//                for (i in selectedObjects!!.indices) {
//                    val vid = findVideo(
//                        selectedObjects!![i]!!.getURL()
//                    )
//                    vid!!.downloadedType = type
//                    updateDownloadingStatusOnResult(vid, type, true)
//                    homeAdapter!!.notifyItemChanged(resultsList!!.indexOf(vid))
//                    downloadQueue!!.add(vid)
//                }
//                selectedObjects = ArrayList()
//                homeAdapter!!.clearCheckedVideos()
//                downloadFabs!!.visibility = View.GONE
//                if (isStoragePermissionGranted) {
//                    mainActivity!!.startDownloadService(downloadQueue, listener)
//                    downloadQueue!!.clear()
//                }
                    (parentFragmentManager.findFragmentByTag("bottomSheet") as DownloadBottomSheetDialog).dismissSelf()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var videoPathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            downloadItem.downloadPath = result.data?.data.toString()
            downloadViewModel.updateDownload(downloadItem)
            saveDir.editText?.setText(fileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)
        }
    }
}