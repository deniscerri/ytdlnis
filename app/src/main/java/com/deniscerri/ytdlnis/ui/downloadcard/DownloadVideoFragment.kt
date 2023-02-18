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
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type


class DownloadVideoFragment(private val resultItem: ResultItem) : Fragment() {
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

    lateinit var downloadItem: DownloadItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_video, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        downloadItem = downloadViewModel.createDownloadItemFromResult(resultItem, Type.video)
        fileUtil = FileUtil()
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {

            val sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            try {
                title = view.findViewById(R.id.title_textinput)
                title.editText!!.setText(downloadItem.title)
                title.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.title = p0.toString()
                    }
                })

                author = view.findViewById(R.id.author_textinput)
                author.editText!!.setText(downloadItem.author)
                author.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.author = p0.toString()
                    }
                })

                saveDir = view.findViewById(R.id.outputPath)
                val downloadPath = sharedPreferences.getString(
                    "video_path",
                    getString(R.string.video_path)
                )
                downloadItem.downloadPath = downloadPath!!
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

                val formats = mutableListOf<Format>()
                formats.addAll(resultItem.formats.filter { !it.format_note.contains("audio", ignoreCase = true) })
                if (formats.isEmpty()) {
                    val videoFormats = resources.getStringArray(R.array.video_formats)
                    val container = sharedPreferences.getString("video_format", "Default")
                    videoFormats.forEach { formats.add(Format(it, container!!, 0, it)) }
                }

                val formatTitles = formats.map {
                    it.format_note + if (it.filesize == 0L) "" else " / " + fileUtil.convertFileSize(it.filesize)
                }


                downloadItem.format = formats[formats.lastIndex]
                val containers = requireContext().resources.getStringArray(R.array.video_containers)

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
                val selectedContainer: String = downloadItem.format.container
                containerAutoCompleteTextView!!.setText(selectedContainer, false)

                (format!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.format.format_note = formats[index].format_note
                        downloadItem.format.format_id = formats[index].format_id
                        downloadItem.format.filesize = formats[index].filesize

                        if (containers.contains(formats[index].container)){
                            downloadItem.format.container = formats[index].container
                            containerAutoCompleteTextView.setText(formats[index].container, false)
                        }

                    }

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.format.container = containers[index]
                    }





                val embedSubs = view.findViewById<Chip>(R.id.embed_subtitles)
                embedSubs!!.isChecked = downloadItem.videoPreferences.embedSubs
                embedSubs.setOnClickListener {
                    downloadItem.videoPreferences.embedSubs = embedSubs.isChecked
                }

                val addChapters = view.findViewById<Chip>(R.id.add_chapters)
                addChapters!!.isChecked = downloadItem.videoPreferences.addChapters
                addChapters.setOnClickListener{
                    downloadItem.videoPreferences.addChapters = addChapters.isChecked
                }

                val saveThumbnail = view.findViewById<Chip>(R.id.save_thumbnail)
                saveThumbnail!!.isChecked = downloadItem.SaveThumb
                saveThumbnail.setOnClickListener {
                    downloadItem.SaveThumb = saveThumbnail.isChecked
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
            //downloadviewmodel.updateDownload(downloadItem)
            saveDir.editText?.setText(fileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)
        }
    }

}