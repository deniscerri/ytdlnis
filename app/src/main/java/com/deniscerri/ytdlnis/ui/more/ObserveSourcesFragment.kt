package com.deniscerri.ytdlnis.ui.more

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ObserveSourcesViewModel
import com.deniscerri.ytdlnis.receiver.ObserveAlarmReceiver
import com.deniscerri.ytdlnis.ui.adapter.ObserveSourcesAdapter
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ObserveSourcesFragment : Fragment(), ObserveSourcesAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: ObserveSourcesAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var observeSourcesViewModel: ObserveSourcesViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var noResults : RelativeLayout
    private lateinit var mainActivity: MainActivity
    private lateinit var preferences: SharedPreferences
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_observe_sources, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topAppBar = view.findViewById(R.id.toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        noResults = view.findViewById(R.id.no_results)

        listAdapter =
            ObserveSourcesAdapter(
                this,
                mainActivity
            )
        val newSource = view.findViewById<Chip>(R.id.newSource)
        recyclerView = view.findViewById(R.id.source_recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("cookies")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        observeSourcesViewModel = ViewModelProvider(this)[ObserveSourcesViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        observeSourcesViewModel.items.observe(viewLifecycleOwner) {
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
            listAdapter.submitList(it)
        }
        initMenu()

        newSource.setOnClickListener {
            showDialog(null)
        }
    }


    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.delete_sources -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                    deleteDialog.setMessage(getString(R.string.confirm_delete_sources_desc))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        observeSourcesViewModel.deleteAll()
                    }
                    deleteDialog.show()
                }
            }
            true
        }
    }

    private fun showDialog(url: String?){
        lifecycleScope.launch {
            val bundle = Bundle()
            bundle.putSerializable("type", downloadViewModel.getDownloadType(
                DownloadViewModel.Type.valueOf(preferences.getString("download_type", "auto")!!), "")
            )

            if (url != null){
                val item = withContext(Dispatchers.IO){
                    observeSourcesViewModel.getByURL(url)
                }
                bundle.putParcelable("item", item)
            }

            findNavController().navigate(R.id.observeSourcesBottomSheetDialog, bundle)
        }

    }
    override fun onItemSearch(item: ObserveSourcesItem) {
        runCatching {
            val intent = Intent(context, ObserveAlarmReceiver::class.java)
            intent.putExtra("id", item.id)
            requireActivity().sendBroadcast(intent)
        }.onFailure {
            Snackbar.make(requireView(), it.message.toString(), Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onItemStart(item: ObserveSourcesItem, position: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                item.status = ObserveSourcesRepository.SourceStatus.ACTIVE
                item.runCount = 0
                observeSourcesViewModel.insert(item)
            }
            listAdapter.notifyItemChanged(position)
        }

    }

    override fun onItemClick(item: ObserveSourcesItem) {
        showDialog(item.url)
    }

    override fun onDelete(item: ObserveSourcesItem) {
        UiUtil.showGenericDeleteDialog(requireContext(), item.name){
            observeSourcesViewModel.delete(item)
        }
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val url = viewHolder.itemView.tag.toString()
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO){
                                observeSourcesViewModel.getByURL(url)
                            }
                            listAdapter.notifyItemChanged(position)
                            onDelete(deletedItem)
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