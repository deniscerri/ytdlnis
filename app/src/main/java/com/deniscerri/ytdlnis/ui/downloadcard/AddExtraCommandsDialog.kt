package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.Extensions.enableTextHighlight
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class AddExtraCommandsDialog(private val item: DownloadItem?, private val callback: ExtraCommandsListener) : BottomSheetDialogFragment() {
    private lateinit var infoUtil: InfoUtil
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout to use as dialog or embedded fragment
        return inflater.inflate(R.layout.extra_commands_bottom_sheet, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }


    @SuppressLint("RestrictedApi", "SetTextI18n", "UseGetLayoutInflater")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val view = LayoutInflater.from(context).inflate(R.layout.result_card_details, null)
        dialog.setContentView(view)


    }

    @SuppressLint("SetTextI18n")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val text = view.findViewById<EditText>(R.id.command)
        val templates = view.findViewById<Button>(R.id.commands)
        val shortcuts = view.findViewById<Button>(R.id.shortcuts)
        val currentText =  view.findViewById<TextView>(R.id.currentText)

        if (item != null){
            val currentCommand = infoUtil.parseYTDLRequestString(infoUtil.buildYoutubeDLRequest(item))
            currentText?.text = currentCommand
        }else{
            view.findViewById<View>(R.id.current).visibility = View.GONE
        }

        text.enableTextHighlight()
        currentText.enableTextHighlight()

        text?.setText(item?.extraCommands)
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        text!!.postDelayed({
            text.setSelection(text.length())
            text.requestFocus()
            imm.showSoftInput(text, 0)
        }, 300)

        var templateCount = 0
        var shortcutCount = 0
        lifecycleScope.launch {
            templateCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalNumber()
            }
            templates.isEnabled = templateCount != 0

            shortcutCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalShortcutNumber()
            }
            shortcuts.isEnabled = shortcutCount != 0

        }

        templates.setOnClickListener {
            if (templateCount == 0){
                Toast.makeText(context, getString(R.string.add_template_first), Toast.LENGTH_SHORT).show()
            }else{
                lifecycleScope.launch {
                    UiUtil.showCommandTemplates(requireActivity(), commandTemplateViewModel){ templates ->
                        templates.forEach {
                            text.text.insert(text.selectionStart, "${it.content} ")
                        }
                        text.postDelayed({
                            text.requestFocus()
                            imm.showSoftInput(text, 0)
                        }, 200)
                    }
                }
            }
        }

        shortcuts.setOnClickListener {
            lifecycleScope.launch {
                if (shortcutCount > 0){
                    UiUtil.showShortcuts(requireActivity(), commandTemplateViewModel,
                        itemSelected = {sh ->
                            text.setText("${text.text} $sh")
                        },
                        itemRemoved = {removed ->
                            text.setText(text.text.replace("(${Regex.escape(removed)})(?!.*\\1)".toRegex(), ""))
                            text.setSelection(text.text.length)
                        })
                }
            }
        }

        val ok = view.findViewById<Button>(R.id.okButton)
        ok?.setOnClickListener {
            callback.onChangeExtraCommand(text.text.toString())
            this.dismiss()
        }

    }


    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }


    private fun cleanUp(){
        kotlin.runCatching {
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("extraCommands")!!).commit()
        }
    }


}


interface ExtraCommandsListener {
    fun onChangeExtraCommand(c: String)
}