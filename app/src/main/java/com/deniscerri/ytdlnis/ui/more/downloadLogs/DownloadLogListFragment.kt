package com.deniscerri.ytdlnis.ui.more.downloadLogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.DownloadLogsAdapter
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.viewmodel.LogViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File


class DownloadLogListFragment : Fragment(), DownloadLogsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadLogAdapter: DownloadLogsAdapter
    private lateinit var noResults: RelativeLayout
    private lateinit var fileList: MutableList<File>
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
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
        recyclerView.adapter = downloadLogAdapter
        noResults = view.findViewById(R.id.no_results)
        noResults.visibility = View.GONE

        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]
        logViewModel.items.observe(viewLifecycleOwner) {
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

    override fun onItemClick(item: LogItem) {
        val bundle = Bundle()
        bundle.putLong("logID", item.id)
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
}