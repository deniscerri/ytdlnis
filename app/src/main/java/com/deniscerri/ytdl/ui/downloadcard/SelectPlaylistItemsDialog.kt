package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.navigation.fragment.findNavController
import androidx.paging.filter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.PlaylistAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.setTextAndRecalculateWidth
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

class SelectPlaylistItemsDialog : BottomSheetDialogFragment(), PlaylistAdapter.OnItemClickListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var listAdapter : PlaylistAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var ok: MaterialButton
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fromTextInput: TextInputLayout
    private lateinit var toTextInput: TextInputLayout
    private lateinit var count: TextView
    private lateinit var selectBetween: MenuItem
    private lateinit var bottomAppBar: BottomAppBar
    private var totalCount: Int = 0
    private var isProgrammaticChange : Boolean = false

    private lateinit var resultItemIDs: List<Long>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]

        arguments?.getLongArray("resultIDs").apply {
            if (this == null){
                dismiss()
                return
            }else{
                resultItemIDs = this.toList()
            }
        }
    }

    @SuppressLint("RestrictedApi", "NotifyDataSetChanged")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.select_playlist_items, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        val progress = view.findViewById<LinearProgressIndicator>(R.id.loadingItemsProgress)

        listAdapter =
            PlaylistAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()

        count = view.findViewById(R.id.count)
        count.text = "0 ${resources.getString(R.string.selected)}"

        fromTextInput = view.findViewById(R.id.from_textinput)
        fromTextInput.editText!!.keyListener = DigitsKeyListener.getInstance("0123456789")
        toTextInput = view.findViewById(R.id.to_textinput)
        toTextInput.editText!!.keyListener = DigitsKeyListener.getInstance("0123456789")


        fromTextInput.editText!!.doAfterTextChanged { _text ->
            if (isProgrammaticChange) return@doAfterTextChanged

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
                    if (endNr > totalCount) endNr = totalCount - 1

                    lifecycleScope.launch {
                        val resultsInMiddle = withContext(Dispatchers.IO){
                            resultViewModel.getResultsBetweenTwoItems(resultItemIDs[startNr], resultItemIDs[endNr])
                        }.toMutableList()

                        if (resultsInMiddle.isNotEmpty()) {
                            listAdapter.checkMultipleItems(resultsInMiddle.map { it.id })
                        }
                        ok.isEnabled = true
                        val selectedIds = getSelectedIDs()
                        count.text = "${selectedIds.size} ${resources.getString(R.string.selected)}"
                    }
                }
            }
        }

        toTextInput.editText!!.doAfterTextChanged { _text  ->
            if (isProgrammaticChange) return@doAfterTextChanged

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
                    if (endNr > totalCount) endNr = totalCount -1

                    lifecycleScope.launch {
                        val resultsInMiddle = withContext(Dispatchers.IO){
                            resultViewModel.getResultsBetweenTwoItems(resultItemIDs[startNr], resultItemIDs[endNr])
                        }.toMutableList()

                        if (resultsInMiddle.isNotEmpty()) {
                            listAdapter.checkMultipleItems(resultsInMiddle.map { it.id })
                        }

                        val selectedIDs = getSelectedIDs().toMutableList()
                        ok.isEnabled = true
                        count.text = "${selectedIDs.size} ${resources.getString(R.string.selected)}"
                    }
                }
            }
        }

        val checkAll = view.findViewById<FloatingActionButton>(R.id.check_all)
        checkAll!!.setOnClickListener {
            lifecycleScope.launch {
                isProgrammaticChange = true

                val selectedIds = getSelectedIDs()
                if (selectedIds.size != totalCount){
                    fromTextInput.editText!!.setTextAndRecalculateWidth("1")
                    toTextInput.editText!!.setTextAndRecalculateWidth(totalCount.toString())
                    listAdapter.checkAll()
                    fromTextInput.isEnabled = true
                    toTextInput.isEnabled = true
                    ok.isEnabled = true
                    val selectedIds = getSelectedIDs()
                    count.text = "(${selectedIds.size}) ${resources.getString(R.string.all_items_selected)} "
                }else{
                    reset()
                    fromTextInput.isEnabled = true
                    toTextInput.isEnabled = true
                    ok.isEnabled = false
                    fromTextInput.editText!!.setTextAndRecalculateWidth("")
                    toTextInput.editText!!.setTextAndRecalculateWidth("")
                }

                isProgrammaticChange = false
            }
        }


        ok = view.findViewById(R.id.bottomsheet_ok_button)
        ok.isEnabled = false
        ok.setOnClickListener {
            ok.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val selectedIds = getSelectedIDs()
                if (selectedIds.size == 1){
                    val resultItem = resultViewModel.getByID(selectedIds.first())!!
                    downloadCardViewModel.setResultItem(resultItem)
                    downloadCardViewModel.setDownloadItem(null)
                    withContext(Dispatchers.Main){
                        findNavController().navigate(R.id.action_selectPlaylistItemsDialog_to_downloadBottomSheetDialog, bundleOf(
                            Pair("result", resultItem),
                            Pair("type", downloadViewModel.getDownloadType(url = resultItem.url)),
                        ))
                    }
                }else{
                    downloadViewModel.turnResultItemsToProcessingDownloads(selectedIds)
                    withContext(Dispatchers.Main){
                        findNavController().navigate(R.id.action_selectPlaylistItemsDialog_to_downloadMultipleBottomSheetDialog)
                    }
                }

                dismiss()

            }
            true
        }

        bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)
        bottomAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId) {
                R.id.invert_selected -> {
                    listAdapter.invertSelected()

                    lifecycleScope.launch {
                        val selectedIds = getSelectedIDs()
                        if (selectedIds.size == totalCount){
                            count.text = "(${selectedIds.size}) ${resources.getString(R.string.all_items_selected)} "
                        }else{
                            count.text = "${selectedIds.size} ${resources.getString(R.string.selected)}"
                        }
                        if(selectedIds.isNotEmpty() && selectedIds.size < totalCount){
                            fromTextInput.isEnabled = false
                            toTextInput.isEnabled = false
                        }
                        ok.isEnabled = selectedIds.isNotEmpty()
                    }
                }
                R.id.select_between -> {
                    lifecycleScope.launch {
                        val selectedIDs = getSelectedIDs().toMutableList()
                        if(selectedIDs.size != 2){
                            m.isVisible = false
                        }else{
                            val resultsInMiddle = withContext(Dispatchers.IO){
                                resultViewModel.getResultsBetweenTwoItems(selectedIDs.first(), selectedIDs.last())
                            }.toMutableList()
                            if (resultsInMiddle.isNotEmpty()){
                                selectedIDs.addAll(resultsInMiddle.map { it.id })
                                listAdapter.checkMultipleItems(selectedIDs)
                                val selectedIDs = getSelectedIDs().toMutableList()
                                count.text = "${selectedIDs.size} ${resources.getString(R.string.selected)}"
                            }
                        }
                    }


                }
                R.id.reverse -> {
                    resultItemIDs = resultViewModel.reverseResults(resultItemIDs)
                    reset()
                }
            }
            true
        }

        selectBetween = bottomAppBar.menu.findItem(R.id.select_between)

        lifecycleScope.launch {
            resultViewModel.totalCount.collectLatest {
                totalCount = it

                val isLoading = it != resultItemIDs.size
                progress.isVisible = isLoading
                recyclerView.suppressLayout(isLoading)
                bottomAppBar.menu.children.forEach { c -> c.isEnabled = !isLoading }
                ok.isEnabled = !isLoading
                checkAll.isEnabled = !isLoading
                fromTextInput.isEnabled = !isLoading
                toTextInput.isEnabled = !isLoading
            }
        }

        lifecycleScope.launch {
            resultViewModel.paginatedItems.map {
                it.filter { it2 ->
                    resultItemIDs.contains(it2.id)
                }
            }.collectLatest {
                listAdapter.submitData(it)
            }
        }

    }

    suspend fun getSelectedIDs() : List<Long> {
        return if (listAdapter.inverted || (listAdapter.checkedItems.isEmpty() && listAdapter.inverted)){
            withContext(Dispatchers.IO){
                resultViewModel.getItemIDsNotPresentIn(listAdapter.checkedItems.toList())
            }
        }else{
            listAdapter.checkedItems.toList()
        }
    }

    override fun onResume() {
        super.onResume()
        ViewCompat.setOnApplyWindowInsetsListener(bottomAppBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Prevent extra bottom padding
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
            WindowInsetsCompat.CONSUMED
        }
    }


    private fun checkRanges(start: String, end: String) : Boolean {
        fromTextInput.error = ""
        toTextInput.error = ""

        if (start.isBlank() || end.isBlank()) return false

        val startValid = start.toInt() >= 0
        val endValid = end.toInt() <= resultItemIDs.size

        if (!startValid) {
            fromTextInput.editText?.setText("")
            fromTextInput.error = "Invalid Number"
        }
        if (!endValid) {
            toTextInput.editText?.setText("")
            toTextInput.error = "Invalid Number"
        }

        return startValid && endValid
    }

    private fun reset(){
        ok.isEnabled = false
        listAdapter.clearCheckeditems()
        count.text = "0 ${resources.getString(R.string.selected)}"
    }


    @SuppressLint("SetTextI18n")
    override fun onCardSelect(itemID: Long, isChecked: Boolean) {
        lifecycleScope.launch {
            val realSelectedIDs = getSelectedIDs()
            val selectedSize = realSelectedIDs.size

            if (selectedSize == totalCount){
                count.text = "(${selectedSize}) ${resources.getString(R.string.all_items_selected)} "
            }else{
                count.text = "$selectedSize ${resources.getString(R.string.selected)}"
            }
            if (selectedSize == 0){
                ok.isEnabled = false
                fromTextInput.isEnabled = true
                toTextInput.isEnabled = true
            }else{
                ok.isEnabled = true
                fromTextInput.isEnabled = false
                toTextInput.isEnabled = false
                val canSelectBetween = run {
                    if(selectedSize != 2) false
                    else {
                        val item1 = resultItemIDs.indexOf(realSelectedIDs.first())
                        val item2 = resultItemIDs.indexOf(realSelectedIDs.last())

                        (item1-item2).absoluteValue > 1
                    }
                }
                selectBetween.isVisible = canSelectBetween
            }
        }


    }

}

