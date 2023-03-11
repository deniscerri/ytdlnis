package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.ClipboardManager
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadCommandFragment(private val resultItem: ResultItem, private var currentDownloadItem: DownloadItem?) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var commandTemplateViewModel : CommandTemplateViewModel
    private lateinit var fileUtil : FileUtil
    private lateinit var uiUtil : UiUtil
    private lateinit var saveDir : TextInputLayout

    lateinit var downloadItem: DownloadItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_command, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]

        downloadItem = if (currentDownloadItem != null && currentDownloadItem!!.type == DownloadViewModel.Type.command){
            val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
            Gson().fromJson(string, DownloadItem::class.java)
        }else{
            downloadViewModel.createDownloadItemFromResult(resultItem, DownloadViewModel.Type.command)
        }

        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            val sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            try {
                val templates : MutableList<CommandTemplate> = withContext(Dispatchers.IO){
                    commandTemplateViewModel.getAll().toMutableList()
                }
                val id = sharedPreferences.getLong("commandTemplate", templates[0].id)
                val chosenCommand = templates.find { it.id == id }
                downloadItem.format = Format(
                    chosenCommand!!.title,
                    "",
                    "",
                    "",
                    "",
                    0,
                    chosenCommand.content
                )

                val chosenCommandView = view.findViewById<TextInputLayout>(R.id.content)
                chosenCommandView.editText!!.setText(chosenCommand.content)
                chosenCommandView.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_delete_all)
                chosenCommandView.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        if (p0!!.isNotEmpty()){
                            chosenCommandView.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_delete_all)
                        }else{
                            chosenCommandView.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_clipboard)
                        }
                        downloadItem.format = Format(
                            "Custom",
                            "",
                            "",
                            "",
                            "",
                            0,
                            p0.toString()
                        )
                    }
                })

                chosenCommandView.setEndIconOnClickListener {
                    if(chosenCommandView.editText!!.text.isEmpty()){
                        val clipboard: ClipboardManager =
                            requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                        chosenCommandView.editText!!.setText(clipboard.primaryClip?.getItemAt(0)?.text)
                    }else{
                        chosenCommandView.editText!!.setText("")
                    }
                }

                val commandTemplates = view.findViewById<TextInputLayout>(R.id.template)
                val autoCompleteTextView =
                    requireView().findViewById<AutoCompleteTextView>(R.id.template_textview)
                populateCommandTemplates(templates, autoCompleteTextView)

                (commandTemplates!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        chosenCommandView.editText!!.setText(templates[index].content)
                        downloadItem.format = Format(
                            templates[index].title,
                            "",
                            "",
                            "",
                            "",
                            0,
                            templates[index].content
                        )
                    }

                saveDir = view.findViewById(R.id.outputPath)
                saveDir.editText!!.setText(
                    fileUtil.formatPath(downloadItem.downloadPath)
                )
                saveDir.editText!!.isFocusable = false;
                saveDir.editText!!.isClickable = true;
                saveDir.editText!!.setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    commandPathResultLauncher.launch(intent)
                }

                val newTemplate : Chip = view.findViewById(R.id.newTemplate)
                newTemplate.setOnClickListener {
                    uiUtil.showCommandTemplateCreationOrUpdatingSheet(null, requireActivity(), viewLifecycleOwner, commandTemplateViewModel) {
                        templates.add(it)
                        chosenCommandView.editText!!.setText(it.content)
                        downloadItem.format = Format(
                            it.title,
                            "",
                            "",
                            "",
                            "",
                            0,
                            it.content
                        )
                        populateCommandTemplates(templates, autoCompleteTextView)
                    }
                }

                val editSelected : Chip = view.findViewById(R.id.editSelected)
                editSelected.setOnClickListener {
                    var current = templates.find { it.title == autoCompleteTextView.text.toString() }
                    if (current == null) current = CommandTemplate(0, "", chosenCommandView.editText!!.text.toString())
                    uiUtil.showCommandTemplateCreationOrUpdatingSheet(current, requireActivity(), viewLifecycleOwner, commandTemplateViewModel) {
                        templates.add(it)
                        chosenCommandView.editText!!.setText(it.content)
                        downloadItem.format = Format(
                            it.title,
                            "",
                            "",
                            "",
                            "",
                            0,
                            it.content
                        )
                    }
                }

                val copyURL = view.findViewById<Chip>(R.id.copy_url)
                copyURL.setOnClickListener {
                    val clipboard: ClipboardManager =
                        requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setText(downloadItem.url)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun populateCommandTemplates(templates: List<CommandTemplate>, autoCompleteTextView: AutoCompleteTextView?){
        val templateTitles = templates.map {it.title}


        autoCompleteTextView?.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                templateTitles
            )
        )
        if (templateTitles.isNotEmpty()) {
            autoCompleteTextView!!.setText(downloadItem.format.format_id, false)
        }
    }

    private var commandPathResultLauncher = registerForActivityResult(
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