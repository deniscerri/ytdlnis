package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadAudioFragment(private var resultItem: ResultItem, private var currentDownloadItem: DownloadItem?) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel : ResultViewModel

    private lateinit var title : TextInputLayout
    private lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView
    private lateinit var infoUtil: InfoUtil

    lateinit var downloadItem : DownloadItem
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_audio, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        infoUtil = InfoUtil(requireContext())
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO) {
                if (currentDownloadItem != null){
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, Type.audio)
                }
            }
            val sharedPreferences =
                 PreferenceManager.getDefaultSharedPreferences(requireContext())

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
                saveDir.editText!!.setText(
                    FileUtil.formatPath(downloadItem.downloadPath)
                )
                saveDir.editText!!.isFocusable = false
                saveDir.editText!!.isClickable = true
                saveDir.editText!!.setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    audioPathResultLauncher.launch(intent)
                }
                freeSpace = view.findViewById(R.id.freespace)
                val free = FileUtil.convertFileSize(
                    File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
                freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
                if (free == "?") freeSpace.visibility = View.GONE

                var formats = mutableListOf<Format>()
                if (currentDownloadItem == null) {
                    formats.addAll(resultItem.formats.filter { it.format_note.contains("audio", ignoreCase = true) })
                }else{
                    //if its updating a present downloaditem and its the wrong category
                    if (currentDownloadItem!!.type != Type.audio){
                        downloadItem.type = Type.audio
                        runCatching {
                            downloadItem.format =
                                downloadItem.allFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                                    .maxByOrNull { it.filesize }!!
                        }.onFailure {
                            downloadItem.format = downloadViewModel.getGenericAudioFormats().last()
                        }
                    }
                }
                if (formats.isEmpty()) formats.addAll(downloadItem.allFormats.filter { it.format_note.contains("audio", ignoreCase = true) })

                val containers = requireContext().resources.getStringArray(R.array.audio_containers)
                var containerPreference = sharedPreferences.getString("audio_format", "Default")
                if (containerPreference == "Default") containerPreference = getString(R.string.defaultValue)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)

                if (formats.isEmpty()) formats = downloadViewModel.getGenericAudioFormats()

                val formatCard = view.findViewById<MaterialCardView>(R.id.format_card_constraintLayout)
                val chosenFormat = downloadItem.format
                UiUtil.populateFormatCard(requireContext(), formatCard, chosenFormat, null)
                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(allFormats: List<List<Format>>, item: List<FormatTuple>) {
                        downloadItem.format = item.first().format
                        UiUtil.populateFormatCard(requireContext(), formatCard, item.first().format, null)
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                resultItem.formats.removeAll(formats.toSet())
                                resultItem.formats.addAll(allFormats.first())
                                resultViewModel.update(resultItem)
                            }
                        }
                        formats = allFormats.first().toMutableList()
                    }
                }
                formatCard.setOnClickListener{
                    if (parentFragmentManager.findFragmentByTag("formatSheet") == null){
                        val bottomSheet = FormatSelectionBottomSheetDialog(listOf(downloadItem), listOf(formats), listener)
                        bottomSheet.show(parentFragmentManager, "formatSheet")
                    }
                }
                formatCard.setOnLongClickListener {
                    UiUtil.showFormatDetails(downloadItem.format, requireActivity())
                    true
                }


                container?.isEnabled = true
                containerAutoCompleteTextView?.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        containers
                    )
                )

                if (currentDownloadItem == null || !containers.contains(downloadItem.container)){
                    downloadItem.container = if (containerPreference == getString(R.string.defaultValue)) "" else containerPreference!!
                }
                containerAutoCompleteTextView.setText(downloadItem.container.ifEmpty { getString(R.string.defaultValue) }, false)

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.container = containers[index]
                        if (containers[index] == getString(R.string.defaultValue)) downloadItem.container = ""
                    }

                UiUtil.configureAudio(
                    view,
                    requireActivity(),
                    listOf(downloadItem),
                    embedThumbClicked = {
                        downloadItem.audioPreferences.embedThumb = it
                    },
                    splitByChaptersClicked = {
                        downloadItem.audioPreferences.splitByChapters = it
                    },
                    filenameTemplateSet = {
                        downloadItem.customFileNameTemplate = it
                    },
                    sponsorBlockItemsSet = { values, checkedItems ->
                        downloadItem.audioPreferences.sponsorBlockFilters.clear()
                        for (i in 0 until checkedItems.size) {
                            if (checkedItems[i]) {
                                downloadItem.audioPreferences.sponsorBlockFilters.add(values[i])
                            }
                        }
                    },
                    cutClicked = {cutVideoListener ->
                        if (parentFragmentManager.findFragmentByTag("cutVideoSheet") == null){
                            val bottomSheet = CutVideoBottomSheetDialog(downloadItem, resultItem.urls, resultItem.chapters, cutVideoListener)
                            bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                        }
                    },
                    extraCommandsClicked = {
                        val callback = object : ExtraCommandsListener {
                            override fun onChangeExtraCommand(c: String) {
                                downloadItem.extraCommands = c
                            }
                        }

                        val bottomSheetDialog = AddExtraCommandsDialog(downloadItem, callback)
                        bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                    }
                )
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
            saveDir.editText?.setText(FileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)

            val free = FileUtil.convertFileSize(
                File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
            freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
            if (free == "?") freeSpace.visibility = View.GONE
        }
    }

}