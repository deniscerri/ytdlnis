package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
import com.deniscerri.ytdlnis.util.UiUtil.enableTextHighlight
import com.deniscerri.ytdlnis.util.UiUtil.populateCommandCard
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadCommandFragment(private val resultItem: ResultItem? = null, private var currentDownloadItem: DownloadItem? = null, private var url: String = "") : Fragment() {
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var commandTemplateViewModel : CommandTemplateViewModel
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView

    lateinit var downloadItem: DownloadItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentView = inflater.inflate(R.layout.fragment_download_command, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        return fragmentView
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO){
                if (currentDownloadItem != null && currentDownloadItem!!.type == DownloadViewModel.Type.command){
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, url, DownloadViewModel.Type.command)
                }
            }

            if (!Patterns.WEB_URL.matcher(downloadItem.url).matches()){
                downloadItem.format = downloadViewModel.generateCommandFormat(CommandTemplate(0,"txt", "-a \"${downloadItem.url}\"", false))
                downloadItem.url = ""
            }

            try {
                val templates : MutableList<CommandTemplate> = withContext(Dispatchers.IO){
                    commandTemplateViewModel.getAll().toMutableList()
                }

                val chosenCommandView = view.findViewById<TextInputLayout>(R.id.content)
                chosenCommandView.editText?.setText(downloadItem.format.format_note)
                chosenCommandView.editText?.enableTextHighlight()
                chosenCommandView.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_delete_all)
                chosenCommandView.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        view.findViewById<MaterialCardView>(R.id.command_card).alpha = 0.3f
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

                val commandTemplateCard = view.findViewById<MaterialCardView>(R.id.command_card)
                runCatching {
                    populateCommandCard(commandTemplateCard, templates.first())
                    commandTemplateCard.setOnClickListener {
                        lifecycleScope.launch {
                            UiUtil.showCommandTemplates(requireActivity(), commandTemplateViewModel){
                                chosenCommandView.editText!!.text.clear()
                                it.forEach { c ->
                                    chosenCommandView.editText!!.text.insert(chosenCommandView.editText!!.selectionStart, "${c.content} ")
                                }
                                populateCommandCard(commandTemplateCard, it.first())
                                downloadItem.format = Format(
                                    it.first().title,
                                    "",
                                    "",
                                    "",
                                    "",
                                    0,
                                    it.map { c -> c.content }.joinToString { " " }
                                )
                            }
                        }


                    }
                    if (downloadItem.url.isEmpty()){
                        view.findViewById<MaterialCardView>(R.id.command_card).alpha = 0.3f
                    }
                }.onFailure {
                    commandTemplateCard.visibility = View.GONE
                    view.findViewById<Chip>(R.id.editSelected).isEnabled = false
                    view.findViewById<TextView>(R.id.command_txt).visibility = View.GONE
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
                    commandPathResultLauncher.launch(intent)
                }

                freeSpace = view.findViewById(R.id.freespace)
                freeSpace.text = String.format(getString(R.string.freespace) + ": " + FileUtil.convertFileSize(
                    File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace
                ))

                val shortcutCount = withContext(Dispatchers.IO){
                    commandTemplateViewModel.getTotalShortcutNumber()
                }

                UiUtil.configureCommand(
                    view,
                    if (downloadItem.url.isEmpty()) 0 else 1,
                    shortcutCount,
                    newTemplateClicked = {
                        UiUtil.showCommandTemplateCreationOrUpdatingSheet(null, requireActivity(), viewLifecycleOwner, commandTemplateViewModel) {
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
                            commandTemplateCard.visibility = View.VISIBLE
                            view.findViewById<TextView>(R.id.command_txt).visibility = View.VISIBLE
                            view.findViewById<Chip>(R.id.editSelected).isEnabled = true
                            populateCommandCard(commandTemplateCard, it)
                        }
                    },
                    editSelectedClicked =
                    {
                        var current = templates.find { it.id == commandTemplateCard.tag }
                        if (current == null) current =
                            CommandTemplate(
                                0,
                                "",
                                chosenCommandView.editText!!.text.toString(),
                                false
                            )
                        UiUtil.showCommandTemplateCreationOrUpdatingSheet(current, requireActivity(), viewLifecycleOwner, commandTemplateViewModel) {
                            templates.add(it)
                            chosenCommandView.editText!!.setText(it.content)
                            populateCommandCard(commandTemplateCard, it)
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
                    },
                    shortcutClicked = {
                        UiUtil.showShortcuts(requireActivity(), commandTemplateViewModel,
                            itemSelected = {
                                chosenCommandView.editText!!.setText("${chosenCommandView.editText!!.text} $it")
                                downloadItem.format.format_note = chosenCommandView.editText!!.text.toString()
                            },
                            itemRemoved = {removed ->
                                chosenCommandView.editText!!.setText(chosenCommandView.editText!!.text.replace("(${
                                    Regex.escape(
                                        removed
                                    )
                                })(?!.*\\1)".toRegex(), "").trimEnd())
                                downloadItem.format.format_note = chosenCommandView.editText!!.text.toString().trimEnd()
                            })
                    }
                )


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
            saveDir.editText?.setText(FileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)

            freeSpace.text = String.format(getString(R.string.freespace) + ": " + FileUtil.convertFileSize(
                File(FileUtil.formatPath(downloadItem.downloadPath)).freeSpace
            ))
        }
    }
}