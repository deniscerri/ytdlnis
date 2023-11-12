package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.DownloadLogsAdapter
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.viewmodel.LogViewModel
import com.deniscerri.ytdlnis.util.Extensions.enableFastScroll
import com.deniscerri.ytdlnis.util.Extensions.forceFastScrollMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DownloadLogListFragment : Fragment(), DownloadLogsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadLogAdapter: DownloadLogsAdapter
    private lateinit var noResults: RelativeLayout
    private lateinit var items: MutableList<LogItem>
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel
    private var selectedObjects: ArrayList<LogItem>? = null
    private var actionMode : ActionMode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
        items = mutableListOf()
        selectedObjects = arrayListOf()
        return inflater.inflate(R.layout.fragment_download_log_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topAppBar = view.findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }

        downloadLogAdapter =
            DownloadLogsAdapter(
                this,
                mainActivity
            )
        recyclerView = view.findViewById(R.id.logs_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.enableFastScroll()
        recyclerView.forceFastScrollMode()
        recyclerView.adapter = downloadLogAdapter
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getBoolean("swipe_gestures", true)){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        noResults = view.findViewById(R.id.no_results)
        noResults.visibility = View.GONE

        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]
        logViewModel.items.observe(viewLifecycleOwner) {
            items = it.toMutableList()
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
            downloadLogAdapter.submitList(it)
        }

        initMenu()
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.remove_logs) {
                try{
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                    deleteDialog.setMessage(getString(R.string.confirm_delete_logs_desc))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        logViewModel.deleteAll()
                    }
                    deleteDialog.show()
                }catch (e: Exception){
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }
            }
            true
        }
    }

    override fun onItemClick(itemID: Long) {
        val bundle = Bundle()
        bundle.putLong("logID", itemID)
        findNavController().navigate(
            R.id.downloadLogFragment,
            bundle
        )
    }

    override fun onDeleteClick(item: LogItem) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            logViewModel.delete(item)

        }
        deleteDialog.show()
    }

    override fun onCardSelect(itemID: Long, isChecked: Boolean) {
        val item = items.find { it.id == itemID }
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
            mode!!.menuInflater.inflate(R.menu.logs_menu_context, menu)
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
                            logViewModel.delete(obj)
                        }
                        clearCheckedItems()
                        actionMode?.finish()
                    }
                    deleteDialog.show()
                    true
                }
                R.id.select_all -> {
                    downloadLogAdapter.checkAll(items)
                    selectedObjects?.clear()
                    items.forEach { selectedObjects?.add(it) }
                    mode?.title = getString(R.string.all_items_selected)
                    true
                }
                R.id.invert_selected -> {
                    downloadLogAdapter.invertSelected(items)
                    val invertedList = arrayListOf<LogItem>()
                    items.forEach {
                        if (!selectedObjects?.contains(it)!!) invertedList.add(it)
                    }
                    selectedObjects?.clear()
                    selectedObjects?.addAll(invertedList)
                    actionMode!!.title = "${selectedObjects!!.size} ${getString(R.string.selected)}"
                    if (invertedList.isEmpty()) actionMode?.finish()
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
        downloadLogAdapter.clearCheckeditems()
        selectedObjects?.clear()
    }


    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO){
                                logViewModel.getItemById(items[position].id)
                            }
                            logViewModel.delete(deletedItem)
                            Snackbar.make(recyclerView, getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title, Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.undo)) {
                                    lifecycleScope.launch(Dispatchers.IO){
                                        logViewModel.insert(deletedItem)
                                    }
                                }.show()
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
                    requireContext(),
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