package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch


class DownloadAudioFragment(private var resultItem: ResultItem) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var fileUtil : FileUtil

    private lateinit var title : TextInputLayout
    private lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout

    lateinit var downloadItem: DownloadItem

    override fun onResume() {
        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_audio, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        downloadItem = downloadViewModel.createDownloadItemFromResult(resultItem, Type.audio)
        fileUtil = FileUtil()
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            val sharedPreferences =
                requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)

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
                    "music_path",
                    getString(R.string.music_path)
                )
                downloadItem.downloadPath = downloadPath!!
                //downloadViewModel.updateDownload(downloadItem)
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
                    audioPathResultLauncher.launch(intent)
                }

                val format = view.findViewById<TextInputLayout>(R.id.format)
                val formats = resultItem.formats.filter { it.format_note.contains("audio", ignoreCase = true) }

                val containers = requireContext().resources.getStringArray(R.array.audio_containers)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)



                if (formats.isEmpty()) {
                    format.visibility = View.GONE
                } else {
                    val formatTitles = formats.map {
                        if (it.format_note.contains("AUDIO_QUALITY_")) {
                            it.format_note.replace(
                                "AUDIO_QUALITY_",
                                ""
                            ) + if (it.filesize == 0L) "" else " / " + fileUtil.convertFileSize(it.filesize)
                        } else {
                            it.format_note + if (it.filesize == 0L) "" else " / " + fileUtil.convertFileSize(
                                it.filesize
                            )
                        }
                    }

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
                            downloadItem.format.format_note = formats[index].format_note
                            downloadItem.format.format_id = formats[index].format_id
                            downloadItem.format.filesize = formats[index].filesize

                            if (containers.contains(formats[index].container)){
                                downloadItem.format.container = formats[index].container
                                containerAutoCompleteTextView.setText(formats[index].container, false)
                            }else{
                                downloadItem.format.container = containers[0]
                                containerAutoCompleteTextView.setText(containers[0], false)
                            }
                        }
                }


                container?.isEnabled = true
                containerAutoCompleteTextView?.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        containers
                    )
                )

                val selectedContainer: String = try {
                    if (containers.contains(formats[formats.lastIndex].container)){
                        formats[formats.lastIndex].container
                    }else{
                        containers[0]
                    }
                }catch (e: Exception){
                    containers[0]
                }

                downloadItem.format.container = selectedContainer
                Log.e("TAG", selectedContainer)
                containerAutoCompleteTextView!!.setText(selectedContainer, false)
                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.format.container = containers[index]
                    }

            }catch (e : Exception){
                e.printStackTrace()
            }
        }
    }

    private var audioPathResultLauncher = registerForActivityResult(
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
            //downloadViewModel.updateDownload(downloadItem)
            saveDir.editText?.setText(fileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)
        }
    }

}