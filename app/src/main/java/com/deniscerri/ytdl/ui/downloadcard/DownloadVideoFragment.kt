package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdl.database.viewmodel.FormatViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.applyFilenameTemplateForCuts
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
import com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadVideoFragment(private var resultItem: ResultItem? = null, private var currentDownloadItem: DownloadItem? = null, private var url: String = "", private var nonSpecific: Boolean = false) : Fragment(), GUISync {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var formatViewModel : FormatViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var preferences: SharedPreferences
    private lateinit var shownFields: List<String>
    lateinit var title : TextInputLayout
    lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView

    private lateinit var genericVideoFormats: MutableList<Format>
    private lateinit var genericAudioFormats: MutableList<Format>

    lateinit var downloadItem: DownloadItem


    @SuppressLint("RestrictedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_download_video, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        formatViewModel = ViewModelProvider(requireActivity())[FormatViewModel::class.java]
        val formatUtil = FormatUtil(requireContext())
        genericVideoFormats = formatUtil.getGenericVideoFormats(requireContext().resources)
        genericAudioFormats = formatUtil.getGenericAudioFormats(requireContext().resources)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        shownFields = preferences.getStringSet("modify_download_card", requireContext().getStringArray(R.array.modify_download_card_values).toSet())!!.toList()
        return fragmentView
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO){
                if (savedInstanceState?.containsKey("updated") == true) {
                    downloadItem.apply {
                        title = resultItem!!.title
                        author = resultItem!!.author
                        allFormats = resultItem!!.formats
                        format = downloadViewModel.getFormat(allFormats, Type.video)
                        duration = resultItem!!.duration
                        playlistIndex = resultItem!!.playlistIndex
                        playlistURL = resultItem!!.playlistURL
                        playlistTitle = resultItem!!.playlistTitle
                        thumb = resultItem!!.thumb
                        website = resultItem!!.website
                        url = resultItem!!.url
                        videoPreferences.apply {
                            audioFormatIDs = downloadViewModel.getPreferredAudioFormats(allFormats)
                        }
                    }
                }else if (currentDownloadItem != null){
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, url, Type.video)
                }
            }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            try {
                title = view.findViewById(R.id.title_textinput)
                title.visibility = if (shownFields.contains("title") && !nonSpecific) View.VISIBLE else View.GONE
                if (title.editText?.text?.isEmpty() == true){
                    title.editText!!.setText(downloadItem.title)
                    title.endIconMode = END_ICON_NONE
                }
                title.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.title = p0.toString()
                    }
                })

                author = view.findViewById(R.id.author_textinput)
                author.visibility = if (shownFields.contains("author") && !nonSpecific) View.VISIBLE else View.GONE
                if (author.editText?.text?.isEmpty() == true){
                    author.editText!!.setText(downloadItem.author)
                    author.endIconMode = END_ICON_NONE
                }
                author.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.author = p0.toString()
                    }
                })

                if (savedInstanceState?.containsKey("updated") == true){
                    if (!listOf(resultItem?.title, downloadItem.title).contains(title.editText?.text.toString())){
                        title.endIconMode = END_ICON_CUSTOM
                        title.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_refresh)
                        downloadItem.title = title.editText?.text.toString()
                    }

                    if (!listOf(resultItem?.author, downloadItem.author).contains(author.editText?.text.toString())){
                        author.endIconMode = END_ICON_CUSTOM
                        author.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_refresh)
                        downloadItem.author = author.editText?.text.toString()
                    }
                }

                title.setEndIconOnClickListener {
                    if (resultItem != null){
                        title.editText?.setText(resultItem?.title)
                    }
                    title.endIconMode = END_ICON_NONE
                }

                author.setEndIconOnClickListener {
                    if (resultItem != null){
                        author.editText?.setText(resultItem?.author)
                    }
                    author.endIconMode = END_ICON_NONE
                }

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
                    pathResultLauncher.launch(intent)
                }

                freeSpace = view.findViewById(R.id.freespace)
                val free = FileUtil.convertFileSize(
                    File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace)
                freeSpace.text = String.format( getString(R.string.freespace) + ": " + free)
                if (free == "?") freeSpace.visibility = View.GONE


                var formats = mutableListOf<Format>()
                if (currentDownloadItem == null) {
                    formats.addAll(resultItem?.formats ?: listOf())
                }else{
                    //if its updating a present downloaditem and its the wrong category
                    if (currentDownloadItem!!.type != Type.video){
                        downloadItem.type = Type.video
                        runCatching {
                            downloadItem.format = downloadViewModel.getFormat(downloadItem.allFormats, Type.video)
                            if (downloadItem.videoPreferences.audioFormatIDs.isEmpty()){
                                downloadItem.videoPreferences.audioFormatIDs.add(
                                    downloadViewModel.getFormat(downloadItem.allFormats, Type.audio).format_id
                                )
                            }
                        }.onFailure {
                            downloadItem.format = genericVideoFormats.last()
                        }
                    }
                }
                if (formats.isEmpty()) formats.addAll(downloadItem.allFormats)

                val containers = requireContext().resources.getStringArray(R.array.video_containers)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                container.visibility = if (shownFields.contains("container")) View.VISIBLE else View.GONE
                if (nonSpecific){
                    val param = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.0f
                    )
                    container.layoutParams = param
                    container.setPadding(0)
                }
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)
                var containerPreference = sharedPreferences.getString("video_format", "Default")
                if (containerPreference == "Default") containerPreference = getString(R.string.defaultValue)

                if (formats.isEmpty()) formats = genericVideoFormats

                val formatCard = view.findViewById<MaterialCardView>(R.id.format_card_constraintLayout)

                val chosenFormat = downloadItem.format

                UiUtil.populateFormatCard(
                    requireContext(),
                    formatCard,
                    chosenFormat,
                    downloadItem.allFormats.filter { downloadItem.videoPreferences.audioFormatIDs.contains(it.format_id) },
                    showSize = downloadItem.downloadSections.isEmpty()
                )

                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(formatTuple: FormatTuple) {
                        formatTuple.format?.apply {
                            downloadItem.format = this
                        }
                        downloadItem.videoPreferences.audioFormatIDs.clear()
                        formatTuple.audioFormats?.map { it.format_id }?.let {
                            downloadItem.videoPreferences.audioFormatIDs.addAll(it)
                        }
                        UiUtil.populateFormatCard(requireContext(), formatCard, downloadItem.format,
                            if(downloadItem.videoPreferences.removeAudio) listOf() else formatTuple.audioFormats,
                            showSize = downloadItem.downloadSections.isEmpty()
                        )
                    }

                    override fun onFormatsUpdated(allFormats: List<Format>) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                resultItem?.apply {
                                    this.formats.removeAll(formats)
                                    this.formats.addAll(allFormats.filter { !genericVideoFormats.contains(it) })
                                    resultViewModel.update(this)
                                }

                                currentDownloadItem?.apply {
                                    downloadViewModel.updateDownloadItemFormats(this.id, allFormats.filter { !genericVideoFormats.contains(it) })
                                }
                            }
                        }
                        formats = allFormats.filter { !genericVideoFormats.contains(it) }.toMutableList()
                        val preferredFormat = downloadViewModel.getFormat(formats, Type.video)
                        val preferredAudioFormats = downloadViewModel.getPreferredAudioFormats(formats)
                        downloadItem.format = preferredFormat
                        downloadItem.allFormats = formats
                        UiUtil.populateFormatCard(requireContext(), formatCard, preferredFormat,
                            if(downloadItem.videoPreferences.removeAudio) listOf() else formats.filter { preferredAudioFormats.contains(it.format_id) },
                            showSize = downloadItem.downloadSections.isEmpty()
                        )
                    }

                }
                formatCard.setOnClickListener{
                    if (parentFragmentManager.findFragmentByTag("formatSheet") == null){
                        formatViewModel.setItem(downloadItem, !nonSpecific)
                        val bottomSheet = FormatSelectionBottomSheetDialog(listener)
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
                if (currentDownloadItem == null || !containers.contains(downloadItem.container.ifEmpty { getString(R.string.defaultValue) })){
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


                view.findViewById<LinearLayout>(R.id.adjust).apply {
                    visibility = if (shownFields.contains("adjust_video")) View.VISIBLE else View.GONE
                    if (isVisible){
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
                                    val bottomSheet = CutVideoBottomSheetDialog(downloadItem, resultItem?.urls ?: "", resultItem?.chapters ?: listOf(), cutVideoListener)
                                    bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                                }
                            },
                            cutDisabledClicked = {
                                val isUpdatingData = ViewModelProvider(requireActivity())[ResultViewModel::class.java].updatingData.value
                                if(isUpdatingData){
                                    val snack = Snackbar.make(view, context.getString(R.string.please_wait), Snackbar.LENGTH_SHORT)
                                    snack.show()
                                }else if (!nonSpecific){
                                    val snack = Snackbar.make(view, context.getString(R.string.cut_unavailable), Snackbar.LENGTH_SHORT)
                                    snack.setAction(R.string.update){
                                        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
                                            resultItem?.apply {
                                                val rsVM = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
                                                rsVM.updateItemData(this)
                                            }
                                        }
                                    }
                                    snack.show()
                                }
                            },
                            cutValueChanged = {
                                downloadItem.downloadSections = it
                                UiUtil.populateFormatCard(
                                    requireContext(),
                                    formatCard,
                                    downloadItem.format,
                                    if(downloadItem.videoPreferences.removeAudio) listOf()
                                    else downloadItem.allFormats.filter { f -> downloadItem.videoPreferences.audioFormatIDs.contains(f.format_id) },
                                    showSize = downloadItem.downloadSections.isEmpty()
                                )

                                if (it.isNotBlank()){
                                    downloadItem.customFileNameTemplate = downloadItem.customFileNameTemplate.applyFilenameTemplateForCuts()
                                }else{
                                    downloadItem.customFileNameTemplate = sharedPreferences.getString("file_name_template", "%(uploader).30B - %(title).170B")!!
                                }
                            },
                            filenameTemplateSet = {
                                downloadItem.customFileNameTemplate = it
                            },
                            saveSubtitlesClicked = {
                                downloadItem.videoPreferences.writeSubs = it
                            },
                            saveAutoSubtitlesClicked = {
                                downloadItem.videoPreferences.writeAutoSubs = it
                            },
                            subtitleLanguagesSet = {
                                downloadItem.videoPreferences.subsLanguages = it
                            },
                            removeAudioClicked = {
                                downloadItem.videoPreferences.removeAudio = it
                                UiUtil.populateFormatCard(
                                    requireContext(),
                                    formatCard,
                                    downloadItem.format,
                                    if (it) listOf() else downloadItem.allFormats.filter { downloadItem.videoPreferences.audioFormatIDs.contains(it.format_id) },
                                    showSize = downloadItem.downloadSections.isEmpty()
                                )
                            },
                            recodeVideoClicked = {
                                downloadItem.videoPreferences.recodeVideo = it
                            },
                            alsoDownloadAsAudioClicked = {
                                downloadItem.videoPreferences.alsoDownloadAsAudio = it
                            },
                            extraCommandsClicked = {
                                val callback = object : ExtraCommandsListener {
                                    override fun onChangeExtraCommand(c: String) {
                                        downloadItem.extraCommands = c
                                    }
                                }

                                val bottomSheetDialog = AddExtraCommandsDialog(downloadItem, callback)
                                bottomSheetDialog.show(parentFragmentManager, "extraCommands")
                            },
                            liveFromStart = {
                                downloadItem.videoPreferences.liveFromStart = it
                            },
                            waitForVideo = { wait, value ->
                                downloadItem.videoPreferences.waitForVideoMinutes = if (wait) value else 0
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun updateTitleAuthor(t: String, a: String){
        downloadItem.title = t
        downloadItem.author = a
        title.editText?.setText(t)
        title.endIconMode = END_ICON_NONE
        author.editText?.setText(a)
        title.endIconMode = END_ICON_NONE
    }

    override fun updateUI(res: ResultItem?) {
        resultItem = res
        val state = Bundle()
        state.putBoolean("updated", true)
        onViewCreated(requireView(),savedInstanceState = state)
    }

    @SuppressLint("RestrictedApi")
    fun updateSelectedAudioFormat(format: Format){
        if (downloadItem.videoPreferences.audioFormatIDs.contains(format.format_id)) {
            return
        }

        downloadItem.videoPreferences.audioFormatIDs.clear()
        downloadItem.videoPreferences.audioFormatIDs.addAll(arrayListOf(format.format_id))
        val formatCard = requireView().findViewById<MaterialCardView>(R.id.format_card_constraintLayout)
        UiUtil.populateFormatCard(requireContext(), formatCard, downloadItem.format,
            if(downloadItem.videoPreferences.removeAudio) listOf() else listOf(format),
            showSize = downloadItem.downloadSections.isEmpty()
        )
    }

    private var pathResultLauncher = registerForActivityResult(
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