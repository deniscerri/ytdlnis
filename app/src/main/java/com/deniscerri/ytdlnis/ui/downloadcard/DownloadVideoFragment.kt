package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
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


class DownloadVideoFragment(private val resultItem: ResultItem, private var currentDownloadItem: DownloadItem?) : Fragment(), TitleAuthorSync {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel

    lateinit var title : TextInputLayout
    lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView
    private lateinit var infoUtil: InfoUtil

    lateinit var downloadItem: DownloadItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_video, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this@DownloadVideoFragment)[ResultViewModel::class.java]
        infoUtil = InfoUtil(requireContext())
        return fragmentView
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO){
                if (currentDownloadItem != null){
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, Type.video)
                }
            }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
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
                    videoPathResultLauncher.launch(intent)
                }

                freeSpace = view.findViewById(R.id.freespace)
                val free = FileUtil.convertFileSize(
                    File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
                freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
                if (free == "?") freeSpace.visibility = View.GONE


                var formats = mutableListOf<Format>()
                if (currentDownloadItem == null) {
                    formats.addAll(resultItem.formats)
                }else{
                    //if its updating a present downloaditem and its the wrong category
                    if (currentDownloadItem!!.type != Type.video){
                        downloadItem.type = Type.video
                        runCatching {
                            downloadItem.format =
                                downloadItem.allFormats.filter { it.vcodec.isNotEmpty() }
                                    .maxByOrNull { it.filesize }!!
                        }.onFailure {
                            downloadItem.format = downloadViewModel.getGenericVideoFormats().last()
                        }
                    }
                }
                if (formats.isEmpty()) formats.addAll(downloadItem.allFormats)

                val containers = requireContext().resources.getStringArray(R.array.video_containers)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)
                var containerPreference = sharedPreferences.getString("video_format", "Default")
                if (containerPreference == "Default") containerPreference = getString(R.string.defaultValue)

                if (formats.isEmpty()) formats = downloadViewModel.getGenericVideoFormats()

                val formatCard = view.findViewById<MaterialCardView>(R.id.format_card_constraintLayout)

                val chosenFormat = downloadItem.format
                UiUtil.populateFormatCard(requireContext(), formatCard, chosenFormat, downloadItem.allFormats.filter { downloadItem.videoPreferences.audioFormatIDs.contains(it.format_id) })
                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(allFormats: List<List<Format>>, item: List<FormatTuple>) {
                        downloadItem.format = item.first().format
                        item.first().audioFormats?.map { it.format_id }
                            ?.let { downloadItem.videoPreferences.audioFormatIDs.addAll(it) }
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                resultItem.formats.removeAll(formats.toSet())
                                resultItem.formats.addAll(allFormats.first())
                                resultViewModel.update(resultItem)
                            }
                        }
                        formats = allFormats.first().toMutableList()
                        UiUtil.populateFormatCard(requireContext(), formatCard, item.first().format, item.first().audioFormats)
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
                containerAutoCompleteTextView!!.setText(
                    downloadItem.container.ifEmpty { getString(R.string.defaultValue) },
                    false)

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.container = containers[index]
                        if (containers[index] == getString(R.string.defaultValue)) downloadItem.container = ""
                    }


                UiUtil.configureVideo(
                    view,
                    requireActivity(),
                    listOf(downloadItem),
                    embedSubsClicked = {
                        downloadItem.videoPreferences.embedSubs = it
                    },
                    addChaptersClicked = {
                        downloadItem.videoPreferences.addChapters = it
                    },
                    splitByChaptersClicked = {
                        downloadItem.videoPreferences.splitByChapters = it
                    },
                    saveThumbnailClicked = {
                        downloadItem.SaveThumb = it
                    },
                    sponsorBlockItemsSet = { values, checkedItems ->
                        downloadItem.videoPreferences.sponsorBlockFilters.clear()
                        for (i in checkedItems.indices) {
                            if (checkedItems[i]) {
                                downloadItem.videoPreferences.sponsorBlockFilters.add(values[i])
                            }
                        }
                    },
                    cutClicked = { cutVideoListener ->
                        if (parentFragmentManager.findFragmentByTag("cutVideoSheet") == null){
                            val bottomSheet = CutVideoBottomSheetDialog(downloadItem, resultItem.urls, resultItem.chapters, cutVideoListener)
                            bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                        }
                    },
                    filenameTemplateSet = {
                        downloadItem.customFileNameTemplate = it
                    },
                    saveSubtitlesClicked = {
                        downloadItem.videoPreferences.writeSubs = it
                    },
                    subtitleLanguagesClicked = {
                        UiUtil.showSubtitleLanguagesDialog(requireActivity(), downloadItem.videoPreferences.subsLanguages){
                            downloadItem.videoPreferences.subsLanguages = it
                        }
                    },
                    removeAudioClicked = {
                        downloadItem.videoPreferences.removeAudio = it
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun updateTitleAuthor(t: String, a: String){
        downloadItem.title = t
        downloadItem.author = a
        title.editText?.setText(t)
        author.editText?.setText(a)
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
            saveDir.editText?.setText(FileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)

            val free = FileUtil.convertFileSize(
                File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
            freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
            if (free == "?") freeSpace.visibility = View.GONE
        }
    }

}