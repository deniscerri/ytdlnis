package com.deniscerri.ytdlnis.ui.more

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.TemplatesAdapter
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.repository.CommandTemplateRepository.CommandTemplateSortType
import com.deniscerri.ytdlnis.database.DBManager.SORTING
import com.deniscerri.ytdlnis.database.models.CommandTemplateExport
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class CommandTemplatesFragment : Fragment(), TemplatesAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var templatesAdapter: TemplatesAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var templatesList: List<CommandTemplate>
    private lateinit var noResults: RelativeLayout
    private lateinit var mainActivity: MainActivity
    private lateinit var sortChip: Chip
    private var selectedObjects: ArrayList<CommandTemplate>? = null
    private var actionMode : ActionMode? = null
    private val jsonFormat = Json { prettyPrint = true }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        selectedObjects = arrayListOf()
        return inflater.inflate(R.layout.fragment_command_templates, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        templatesList = listOf()

        topAppBar = view.findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        noResults = view.findViewById(R.id.no_results)
        sortChip = view.findViewById(R.id.sortChip)

        templatesAdapter =
            TemplatesAdapter(
                this,
                mainActivity
            )
        recyclerView = view.findViewById(R.id.template_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = templatesAdapter
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("templates")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }


        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        commandTemplateViewModel.allItems.observe(viewLifecycleOwner) {
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
        }

        commandTemplateViewModel.getFilteredList().observe(viewLifecycleOwner) {
            templatesAdapter.submitList(it)
            templatesList = it
            scrollToTop()
        }

        commandTemplateViewModel.sortOrder.observe(viewLifecycleOwner){
            if (it != null){
                when(it){
                    SORTING.ASC -> sortChip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_down)
                    SORTING.DESC -> sortChip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_up)
                }
            }
        }

        commandTemplateViewModel.sortType.observe(viewLifecycleOwner){
            if(it != null){
                when(it){
                    CommandTemplateSortType.DATE -> sortChip.text = getString(R.string.date_added)
                    CommandTemplateSortType.TITLE -> sortChip.text = getString(R.string.title)
                    CommandTemplateSortType.LENGTH -> sortChip.text = getString(R.string.length)
                }
            }
        }

        initMenu()
        initChips()
    }

    private fun initMenu() {
        val onActionExpandListener: MenuItem.OnActionExpandListener =
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    return true
                }
            }
        topAppBar.menu.findItem(R.id.search_command)
            .setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar.menu.findItem(R.id.search_command).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView.queryHint = getString(R.string.search_command_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar.menu.findItem(R.id.search_command).collapseActionView()
                commandTemplateViewModel.setQueryFilter(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                commandTemplateViewModel.setQueryFilter(newText)
                return true
            }
        })
        topAppBar.setOnClickListener { scrollToTop() }

        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId){
                R.id.export_clipboard -> {
                    lifecycleScope.launch{
                        withContext(Dispatchers.IO){
                            commandTemplateViewModel.exportToClipboard()
                        }
                        Snackbar.make(recyclerView, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
                    }
                }
                R.id.import_clipboard -> {
                    lifecycleScope.launch{
                        withContext(Dispatchers.IO){
                            val count = commandTemplateViewModel.importFromClipboard()
                            mainActivity.runOnUiThread{
                                Snackbar.make(recyclerView, "${getString(R.string.items_imported)} (${count})", Snackbar.LENGTH_LONG).show()
                            }
                        }

                    }
                }
            }
            true
        }
    }

    private fun initChips() {
        val sorting = view?.findViewById<Chip>(R.id.sortChip)
        sorting?.setOnClickListener {
            val sortSheet = BottomSheetDialog(requireContext())
            sortSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            sortSheet.setContentView(R.layout.command_templates_sort_sheet)

            val date = sortSheet.findViewById<TextView>(R.id.date)
            val title = sortSheet.findViewById<TextView>(R.id.title)
            val length = sortSheet.findViewById<TextView>(R.id.length)

            val sortOptions = listOf(date!!, title!!, length!!)
            sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
            when(commandTemplateViewModel.sortType.value!!) {
                CommandTemplateSortType.DATE -> changeSortIcon(date, commandTemplateViewModel.sortOrder.value!!)
                CommandTemplateSortType.TITLE -> changeSortIcon(title, commandTemplateViewModel.sortOrder.value!!)
                CommandTemplateSortType.LENGTH -> changeSortIcon(length, commandTemplateViewModel.sortOrder.value!!)
            }

            date.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                commandTemplateViewModel.setSorting(CommandTemplateSortType.DATE)
                changeSortIcon(date, commandTemplateViewModel.sortOrder.value!!)
            }
            title.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                commandTemplateViewModel.setSorting(CommandTemplateSortType.TITLE)
                changeSortIcon(title, commandTemplateViewModel.sortOrder.value!!)
            }
            length.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                commandTemplateViewModel.setSorting(CommandTemplateSortType.LENGTH)
                changeSortIcon(length, commandTemplateViewModel.sortOrder.value!!)
            }
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            sortSheet.behavior.peekHeight = displayMetrics.heightPixels
            sortSheet.show()
        }

        val new = view?.findViewById<Chip>(R.id.newTemplate)
        new?.setOnClickListener {
            UiUtil.showCommandTemplateCreationOrUpdatingSheet(null,mainActivity, this, commandTemplateViewModel) {}
        }
        val shortcuts = view?.findViewById<Chip>(R.id.shortcuts)
        shortcuts?.setOnClickListener {
            UiUtil.showShortcutsSheet(mainActivity,this, commandTemplateViewModel)
        }
    }

    private fun changeSortIcon(item: TextView, order: SORTING){
        when(order){
            SORTING.DESC ->{
                item.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_up, 0,0,0)
            }
            SORTING.ASC ->                 {
                item.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_down, 0,0,0)
            }
        }
    }

    private fun scrollToTop() {
        recyclerView.scrollToPosition(0)
        Handler(Looper.getMainLooper()).post {
            (topAppBar.parent as AppBarLayout).setExpanded(
                true,
                true
            )
        }
    }

    override fun onItemClick(commandTemplate: CommandTemplate, index: Int) {
        UiUtil.showCommandTemplateCreationOrUpdatingSheet(commandTemplate,mainActivity, this, commandTemplateViewModel) {
            templatesAdapter.notifyItemChanged(index)
        }

    }

    override fun onSelected(commandTemplate: CommandTemplate) {
    }

    override fun onCardSelect(itemID: Long, isChecked: Boolean) {
        val item = templatesList.find { it.id == itemID }
        if (isChecked) {
            selectedObjects!!.add(item!!)
            if (actionMode == null){
                actionMode = (activity as AppCompatActivity?)!!.startSupportActionMode(contextualActionBar)

            }else{
                actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            }
        }
        else {
            selectedObjects!!.remove(item)
            actionMode?.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            if (selectedObjects!!.isEmpty()){
                actionMode?.finish()
            }
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.templates_menu_context, menu)
            mode.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
            return true
        }

        override fun onPrepareActionMode(
            mode: ActionMode?,
            menu: Menu?
        ): Boolean {
            return false
        }

        override fun onActionItemClicked(
            mode: ActionMode?,
            item: MenuItem?
        ): Boolean {
            return when (item!!.itemId) {
                R.id.delete_results -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        for (obj in selectedObjects!!){
                            commandTemplateViewModel.delete(obj)
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.select_all -> {
                    templatesAdapter.checkAll(templatesList)
                    selectedObjects?.clear()
                    templatesList.forEach { selectedObjects?.add(it) }
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    templatesAdapter.invertSelected(templatesList)
                    val invertedList = arrayListOf<CommandTemplate>()
                    templatesList.forEach {
                        if (!selectedObjects?.contains(it)!!) invertedList.add(it)
                    }
                    selectedObjects?.clear()
                    selectedObjects?.addAll(invertedList)
                    actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
                    if (invertedList.isEmpty()) actionMode?.finish()
                    true
                }
                R.id.export_clipboard -> {
                    lifecycleScope.launch{
                        val output = jsonFormat.encodeToString(
                            CommandTemplateExport(
                                templates = selectedObjects!!.toList(),
                                shortcuts = listOf()
                            )
                        )

                        val clipboard: ClipboardManager =
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setText(output)
                        Snackbar.make(recyclerView, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            clearCheckedItems()
        }
    }

    private fun clearCheckedItems(){
        templatesAdapter.clearCheckeditems()
        selectedObjects?.clear()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = templatesList[position]
                        templatesAdapter.notifyItemChanged(position)
                        UiUtil.showGenericDeleteDialog(requireContext(), deletedItem.title){
                            commandTemplateViewModel.delete(deletedItem)
                        }
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    context,
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }
}