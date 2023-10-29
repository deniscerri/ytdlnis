package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.ActionBar.LayoutParams
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.PlaylistAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.receiver.ShareActivity
import com.deniscerri.ytdlnis.util.UiUtil.enableFastScroll
import com.deniscerri.ytdlnis.util.UiUtil.setTextAndRecalculateWidth
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SelectPlaylistItemsDialog(private val items: List<ResultItem?>, private val type: DownloadViewModel.Type) : DialogFragment(), PlaylistAdapter.OnItemClickListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var listAdapter : PlaylistAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var ok: MenuItem
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fromTextInput: TextInputLayout
    private lateinit var toTextInput: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_playlist_items, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        dialog?.window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)

        listAdapter =
            PlaylistAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()
        listAdapter.submitList(items)

        toolbar.title = "0 ${resources.getString(R.string.selected)}"

        fromTextInput = view.findViewById(R.id.from_textinput)
        toTextInput = view.findViewById(R.id.to_textinput)


        fromTextInput.editText!!.doAfterTextChanged { _text ->
            reset()
            val start = _text.toString()
            val end = toTextInput.editText!!.text.toString()

            if (checkRanges(start, end)) {
                if (start.toInt() < end.toInt()){
                    var startNr = Integer.parseInt(start)
                    startNr--
                    var endNr = Integer.parseInt(end)
                    endNr--
                    if (startNr <= 0) startNr = 0
                    if (endNr > items.size) endNr = items.size - 1
                    listAdapter.checkRange(startNr, endNr)
                    ok.isEnabled = true
                    toolbar.title = "${listAdapter.getCheckedItems().size} ${resources.getString(R.string.selected)}"
                }
            }
        }

        toTextInput.editText!!.doAfterTextChanged { _text  ->
            reset()
            val start = fromTextInput.editText!!.text.toString()
            val end = _text.toString()

            if (checkRanges(start, end)) {
                if (start.toInt() < end.toInt()){
                    var startNr = Integer.parseInt(start)
                    startNr--
                    var endNr = Integer.parseInt(end)
                    endNr--
                    if (startNr <= 0) startNr = 0
                    if (endNr > items.size) endNr = items.size -1
                    listAdapter.checkRange(startNr, endNr)
                    ok.isEnabled = true
                    toolbar.title = "${listAdapter.getCheckedItems().size} ${resources.getString(R.string.selected)}"
                }
            }
        }

        val checkAll = view.findViewById<ExtendedFloatingActionButton>(R.id.check_all)
        checkAll!!.setOnClickListener {
            if (listAdapter.getCheckedItems().size != items.size){
                fromTextInput.editText!!.setTextAndRecalculateWidth("1")
                toTextInput.editText!!.setTextAndRecalculateWidth(items.size.toString())
                listAdapter.checkAll()
                fromTextInput.isEnabled = true
                toTextInput.isEnabled = true
                ok.isEnabled = true
                toolbar.title = resources.getString(R.string.all_items_selected)
            }else{
                reset()
                fromTextInput.isEnabled = true
                toTextInput.isEnabled = true
                ok.isEnabled = false
                fromTextInput.editText!!.setTextAndRecalculateWidth("")
                toTextInput.editText!!.setTextAndRecalculateWidth("")
            }
        }


        ok = toolbar.menu.getItem(0)
        ok.isEnabled = false
        ok.setOnMenuItemClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val checkedItems = listAdapter.getCheckedItems()
                val checkedResultItems = items.filter { item -> checkedItems.contains(item!!.url) }
                if (checkedResultItems.size == 1){
                    val resultItem = resultViewModel.getItemByURL(checkedResultItems[0]!!.url)
                    val bottomSheet = DownloadBottomSheetDialog(resultItem, type)
                    parentFragmentManager.addFragmentOnAttachListener { fragmentManager, fragment ->
                        dismiss()
                    }
                    bottomSheet.show(parentFragmentManager, "downloadSingleSheet")
                }else{
                    val downloadItems = mutableListOf<DownloadItem>()
                    checkedResultItems.forEach { c ->
                        c!!.id = 0
                        val i = downloadViewModel.createDownloadItemFromResult(
                            result = c, givenType = type)
                        if (type == DownloadViewModel.Type.command){
                            i.format = downloadViewModel.getLatestCommandTemplateAsFormat()
                        }
                        downloadItems.add(i)
                    }

                    val bottomSheet = DownloadMultipleBottomSheetDialog(downloadItems)
                    parentFragmentManager.addFragmentOnAttachListener { fragmentManager, fragment ->
                        dismiss()
                    }
                    bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                }

                dismiss()

            }
            true
        }

        view.findViewById<BottomAppBar>(R.id.bottomAppBar).setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.invert_selected) {
                listAdapter.invertSelected(items)
                val checkedItems = listAdapter.getCheckedItems()
                if (checkedItems.size == items.size){
                    toolbar.title= resources.getString(R.string.all_items_selected)
                }else{
                    toolbar.title = "${checkedItems.size} ${resources.getString(R.string.selected)}"
                }
                if(checkedItems.isNotEmpty() && checkedItems.size < items.size){
                    fromTextInput.isEnabled = false
                    toTextInput.isEnabled = false
                }
                ok.isEnabled = checkedItems.isNotEmpty()
            }
            true
        }

        toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanup()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanup()
    }

    private fun cleanup(){
        kotlin.runCatching {
            val movedToMultipleOrSingleSheet = parentFragmentManager.fragments.map { it.tag }.contains("downloadMultipleSheet") || parentFragmentManager.fragments.map { it.tag }.contains("downloadSingleSheet")
            if (activity is ShareActivity && !movedToMultipleOrSingleSheet){
                (activity as ShareActivity).finish()
            }
        }
    }

    private fun checkRanges(start: String, end: String) : Boolean {
        return start.isNotBlank() && end.isNotBlank()
    }

    private fun reset(){
        ok.isEnabled = false
        listAdapter.clearCheckeditems()
        toolbar.title = "0 ${resources.getString(R.string.selected)}"
    }


    override fun onCardSelect(itemURL: String, isChecked: Boolean, checkedItems: List<String>) {
        if (checkedItems.size == items.size){
            toolbar.title = resources.getString(R.string.all_items_selected)
        }else{
            toolbar.title = "${checkedItems.size} ${resources.getString(R.string.selected)}"
        }
        if (checkedItems.isEmpty()){
            ok.isEnabled = false
            fromTextInput.isEnabled = true
            toTextInput.isEnabled = true
        }else{
            ok.isEnabled = true
            fromTextInput.isEnabled = false
            toTextInput.isEnabled = false
        }
    }

}

