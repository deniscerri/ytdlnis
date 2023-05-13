package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doOnTextChanged
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
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadVideoFragment(private val resultItem: ResultItem, private var currentDownloadItem: DownloadItem?) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var fileUtil : FileUtil
    private lateinit var uiUtil : UiUtil

    private lateinit var title : TextInputLayout
    private lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView

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

        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO){
                if (currentDownloadItem != null && currentDownloadItem!!.type == Type.video){
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
                    fileUtil.formatPath(downloadItem.downloadPath)
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
                freeSpace.text = String.format(getString(R.string.freespace) + ": " + fileUtil.convertFileSize(
                    File(fileUtil.formatPath(downloadItem.downloadPath)).freeSpace
                ))

                var formats = mutableListOf<Format>()
                formats.addAll(resultItem.formats.filter { !it.format_note.contains("audio", ignoreCase = true) })
                if (formats.isEmpty()) formats.addAll(downloadItem.allFormats.filter { !it.format_note.contains("audio", ignoreCase = true) })

                val containers = requireContext().resources.getStringArray(R.array.video_containers)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)
                var containerPreference = sharedPreferences.getString("video_format", "Default")
                if (containerPreference == "Default") containerPreference = getString(R.string.defaultValue)

                if (formats.isEmpty()) formats = downloadViewModel.getGenericVideoFormats()

                val formatCard = view.findViewById<ConstraintLayout>(R.id.format_card_constraintLayout)

                val chosenFormat = downloadItem.format
                uiUtil.populateFormatCard(formatCard, chosenFormat)
                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(allFormats: List<List<Format>>, item: List<Format>) {
                        downloadItem.format = item.first()
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                resultItem.formats.removeAll(formats.toSet())
                                resultItem.formats.addAll(allFormats.first())
                                resultViewModel.update(resultItem)
                            }
                        }
                        formats = allFormats.first().toMutableList()
                        uiUtil.populateFormatCard(formatCard, item.first())
                    }
                }
                formatCard.setOnClickListener{
                    if (parentFragmentManager.findFragmentByTag("formatSheet") == null){
                        val bottomSheet = FormatSelectionBottomSheetDialog(listOf(downloadItem), listOf(formats), listener)
                        bottomSheet.show(parentFragmentManager, "formatSheet")
                    }
                }

                formatCard.setOnLongClickListener {
                    uiUtil.showFormatDetails(downloadItem.format, requireActivity())
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
                downloadItem.format.container = if (containerPreference == getString(R.string.defaultValue)) "" else containerPreference!!
                containerAutoCompleteTextView!!.setText(
                    downloadItem.format.container.ifEmpty { getString(R.string.defaultValue) },
                    false)

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.format.container = containers[index]
                        if (containers[index] == getString(R.string.defaultValue)) downloadItem.format.container = ""
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


                val splitByChapters = view.findViewById<Chip>(R.id.split_by_chapters)
                if(downloadItem.downloadSections.isNotBlank()){
                    splitByChapters.isEnabled = false
                    splitByChapters.isChecked = false
                }else{
                    splitByChapters!!.isChecked = downloadItem.audioPreferences.splitByChapters
                }
                splitByChapters.setOnClickListener {
                    if (splitByChapters.isChecked){
                        addChapters.isEnabled = false
                        addChapters.isChecked = false
                        downloadItem.videoPreferences.addChapters = false
                    }else{
                        addChapters.isEnabled = true
                    }
                    downloadItem.videoPreferences.splitByChapters = splitByChapters.isChecked
                }

                val saveThumbnail = view.findViewById<Chip>(R.id.save_thumbnail)
                saveThumbnail!!.isChecked = downloadItem.SaveThumb
                saveThumbnail.setOnClickListener {
                    downloadItem.SaveThumb = saveThumbnail.isChecked
                }

                val sponsorBlock = view.findViewById<Chip>(R.id.sponsorblock_filters)
                sponsorBlock!!.setOnClickListener {
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle(getString(R.string.select_sponsorblock_filtering))
                    val values = resources.getStringArray(R.array.sponsorblock_settings_values)
                    val entries = resources.getStringArray(R.array.sponsorblock_settings_entries)
                    val checkedItems : ArrayList<Boolean> = arrayListOf()
                    values.forEach {
                        if (downloadItem.videoPreferences.sponsorBlockFilters.contains(it)) {
                            checkedItems.add(true)
                        }else{
                            checkedItems.add(false)
                        }
                    }

                    builder.setMultiChoiceItems(
                        entries,
                        checkedItems.toBooleanArray()
                    ) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }

                    builder.setPositiveButton(
                        getString(R.string.ok)
                    ) { _: DialogInterface?, _: Int ->
                        downloadItem.videoPreferences.sponsorBlockFilters.clear()
                        for (i in 0 until checkedItems.size) {
                            if (checkedItems[i]) {
                                downloadItem.videoPreferences.sponsorBlockFilters.add(values[i])
                            }
                        }
                    }

                    // handle the negative button of the alert dialog
                    builder.setNegativeButton(
                        getString(R.string.cancel)
                    ) { _: DialogInterface?, _: Int -> }

                    val dialog = builder.create()
                    dialog.show()
                }

                val cut = view.findViewById<Chip>(R.id.cut)
                if(downloadItem.duration.isNotEmpty()){
                    cut.isEnabled = true
                    if (downloadItem.downloadSections.isNotBlank()) cut.text = downloadItem.downloadSections
                    val cutVideoListener = object : VideoCutListener {

                        override fun onChangeCut(list: List<String>) {
                            if (list.isEmpty()){
                                downloadItem.downloadSections = ""
                                cut.text = getString(R.string.cut)

                                splitByChapters.isEnabled = true
                                splitByChapters.isChecked = downloadItem.videoPreferences.splitByChapters
                                if (splitByChapters.isChecked){
                                    addChapters.isEnabled = false
                                    addChapters.isChecked = false
                                }else{
                                    addChapters.isEnabled = true
                                }
                            }else{
                                var value = ""
                                list.forEach {
                                    value += "$it;"
                                }
                                downloadItem.downloadSections = value
                                cut.text = value.dropLast(1)

                                splitByChapters.isEnabled = false
                                splitByChapters.isChecked = false
                                addChapters.isEnabled = true
                            }

                        }
                    }
                    cut.setOnClickListener {
                        if (parentFragmentManager.findFragmentByTag("cutVideoSheet") == null){
                            val bottomSheet = CutVideoBottomSheetDialog(downloadItem, cutVideoListener)
                            bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                        }
                    }
                }else{
                    cut.isEnabled = false
                }

                val filenameTemplate = view.findViewById<Chip>(R.id.filename_template)
                filenameTemplate.setOnClickListener {
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle(getString(R.string.file_name_template))
                    val inputLayout = layoutInflater.inflate(R.layout.textinput, null)
                    val editText = inputLayout.findViewById<EditText>(R.id.url_edittext)
                    inputLayout.findViewById<TextInputLayout>(R.id.url_textinput).hint = getString(R.string.file_name_template)
                    editText.setText(downloadItem.customFileNameTemplate)
                    editText.setSelection(editText.text.length)
                    builder.setView(inputLayout)
                    builder.setPositiveButton(
                        getString(R.string.ok)
                    ) { dialog: DialogInterface?, which: Int ->
                        downloadItem.customFileNameTemplate = editText.text.toString()
                    }

                    // handle the negative button of the alert dialog
                    builder.setNegativeButton(
                        getString(R.string.cancel)
                    ) { dialog: DialogInterface?, which: Int -> }

                    val dialog = builder.create()
                    editText.doOnTextChanged { text, start, before, count ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
                    }
                    dialog.show()
                    val imm = context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                    editText!!.postDelayed({
                        editText.requestFocus()
                        imm.showSoftInput(editText, 0)
                    }, 300)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
                }

                val saveSubtitles = view.findViewById<Chip>(R.id.save_subtitles)
                val subtitleLanguages = view.findViewById<Chip>(R.id.subtitle_languages)
                if (downloadItem.videoPreferences.writeSubs) {
                    saveSubtitles.isChecked = true
                    subtitleLanguages.visibility = View.VISIBLE
                }

                saveSubtitles.setOnCheckedChangeListener { compoundButton, b ->
                    if (saveSubtitles.isChecked) subtitleLanguages.visibility = View.VISIBLE
                    else subtitleLanguages.visibility = View.GONE
                    downloadItem.videoPreferences.writeSubs = saveSubtitles.isChecked
                }

                subtitleLanguages.setOnClickListener {
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle(getString(R.string.subtitle_languages))
                    val inputLayout = layoutInflater.inflate(R.layout.textinput, null)
                    val editText = inputLayout.findViewById<EditText>(R.id.url_edittext)
                    editText.setHint(R.string.subtitle_languages)
                    editText.setText(downloadItem.videoPreferences.subsLanguages)
                    editText.setSelection(editText.text.length)
                    builder.setView(inputLayout)
                    builder.setPositiveButton(
                        getString(R.string.ok)
                    ) { dialog: DialogInterface?, which: Int ->
                        downloadItem.videoPreferences.subsLanguages = editText.text.toString()
                    }

                    // handle the negative button of the alert dialog
                    builder.setNegativeButton(
                        getString(R.string.cancel)
                    ) { dialog: DialogInterface?, which: Int -> }

                    builder.setNeutralButton("?")  { dialog: DialogInterface?, which: Int ->
                        val browserIntent =
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yt-dlp/yt-dlp#subtitle-options"))
                        startActivity(browserIntent)
                    }


                    val dialog = builder.create()
                    editText.doOnTextChanged { text, start, before, count ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
                    }
                    dialog.show()
                    val imm = context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                    editText!!.postDelayed({
                        editText.requestFocus()
                        imm.showSoftInput(editText, 0)
                    }, 300)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
                }

                val removeAudio = view.findViewById<Chip>(R.id.remove_audio)
                removeAudio.setOnCheckedChangeListener { compoundButton, b ->
                    downloadItem.videoPreferences.removeAudio = removeAudio.isChecked
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

            freeSpace.text = String.format(getString(R.string.freespace) + ": " + fileUtil.convertFileSize(
                File(fileUtil.formatPath(downloadItem.downloadPath)).freeSpace
            ))
        }
    }

}