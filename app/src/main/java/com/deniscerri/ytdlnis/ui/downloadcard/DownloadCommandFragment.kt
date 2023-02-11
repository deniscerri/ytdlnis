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
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class DownloadCommandFragment(private val resultItem: ResultItem) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var fileUtil : FileUtil

    private lateinit var saveDir : TextInputLayout
    private lateinit var commandTemplateDao : CommandTemplateDao

    lateinit var downloadItem: DownloadItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_command, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        downloadItem = downloadViewModel.createDownloadItemFromResult(resultItem, DownloadViewModel.Type.command)
        fileUtil = FileUtil()
        commandTemplateDao = DBManager.getInstance(requireContext()).commandTemplateDao
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {

            val sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            try {
                val commands = commandTemplateDao.getAllTemplates()
                val id = sharedPreferences.getLong("commandTemplate", commands[0].id)
                val chosenCommand = commands.find { it.id == id }
                downloadItem.format = Format(
                    chosenCommand!!.title,
                    "",
                    0,
                    chosenCommand.content
                )

                val templates = commandTemplateDao.getAllTemplates()
                val templateTitles = templates.map {it.title}

                val commandTemplates = view.findViewById<TextInputLayout>(R.id.template)
                val autoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.template_textview)
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

                (commandTemplates!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
//                       TODO
                    }

                val templateContent = view.findViewById<TextView>(R.id.template_content_textview)
                templateContent.text = downloadItem.format.format_note

                saveDir = view.findViewById(R.id.outputPath)
                val downloadPath = sharedPreferences.getString(
                    "command_path",
                    getString(R.string.command_path)
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
                    commandPathResultLauncher.launch(intent)
                }


                val embedSubs = view.findViewById<Chip>(R.id.embed_subtitles)
                embedSubs!!.isChecked = embedSubs.isChecked
                embedSubs.setOnClickListener {
                    downloadItem.embedSubs = embedSubs.isChecked
                }

                val addChapters = view.findViewById<Chip>(R.id.add_chapters)
                addChapters!!.isChecked = addChapters.isChecked
                addChapters.setOnClickListener{
                    downloadItem.addChapters = addChapters.isChecked
                }

                val saveThumbnail = view.findViewById<Chip>(R.id.save_thumbnail)
                saveThumbnail!!.isChecked = saveThumbnail.isChecked
                saveThumbnail.setOnClickListener {
                    downloadItem.SaveThumb = saveThumbnail.isChecked
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
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