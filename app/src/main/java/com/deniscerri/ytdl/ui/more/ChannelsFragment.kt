package com.deniscerri.ytdl.ui.more

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ChannelItem
import com.deniscerri.ytdl.database.viewmodel.ChannelsViewModel
import com.deniscerri.ytdl.ui.adapter.ChannelsAdapter
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsFragment : Fragment(), ChannelsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: ChannelsAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var channelsViewModel: ChannelsViewModel
    private lateinit var noResults: RelativeLayout
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_channels, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topAppBar = view.findViewById(R.id.toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        noResults = view.findViewById(R.id.no_results)

        listAdapter = ChannelsAdapter(this, mainActivity)
        val addChannel = view.findViewById<Chip>(R.id.addChannel)
        recyclerView = view.findViewById(R.id.channels_recyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        recyclerView.adapter = listAdapter

        channelsViewModel = ViewModelProvider(this)[ChannelsViewModel::class.java]
        channelsViewModel.items.observe(viewLifecycleOwner) {
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
            listAdapter.submitList(it)
        }

        addChannel.setOnClickListener {
            showAddChannelDialog()
        }

        initMenu()
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.delete_channels -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                    deleteDialog.setMessage(getString(R.string.confirm_delete_channels_desc))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        channelsViewModel.deleteAll()
                    }
                    deleteDialog.show()
                }
            }
            true
        }
    }

    private fun showAddChannelDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setTitle(getString(R.string.add_channel))

        val options = arrayOf(
            getString(R.string.search_channels),
            getString(R.string.add_by_url)
        )

        dialog.setItems(options) { _, which ->
            when (which) {
                0 -> findNavController().navigate(R.id.action_channelsFragment_to_channelSearchFragment)
                1 -> showPasteUrlDialog()
            }
        }

        dialog.show()
    }

    private fun showPasteUrlDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setTitle(getString(R.string.add_by_url))

        val input = androidx.appcompat.widget.AppCompatEditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.hint = getString(R.string.channel_url)

        dialog.setView(input)
        dialog.setNegativeButton(getString(R.string.cancel)) { d, _ -> d.cancel() }
        dialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) {
                val anchor = view ?: return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    // Normalize/validate first: rejects unsupported links and strips tab suffixes
                    // like /videos so the channel isn't saved under the name "videos".
                    val normalized = withContext(Dispatchers.IO) {
                        channelsViewModel.normalizeChannelUrl(url)
                    }
                    if (normalized == null) {
                        Snackbar.make(anchor, getString(R.string.invalid_channel_url), Snackbar.LENGTH_SHORT).show()
                        return@launch
                    }
                    val name = normalized.substringAfterLast("/").ifBlank { normalized }
                    val result = withContext(Dispatchers.IO) {
                        channelsViewModel.insert(ChannelItem(name = name, url = normalized))
                    }
                    if (result > 0) {
                        Snackbar.make(anchor, getString(R.string.channel_saved), Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(anchor, getString(R.string.channel_already_exists), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    override fun onItemClick(item: ChannelItem) {
        val bundle = Bundle()
        bundle.putString("channelUrl", item.url)
        bundle.putString("channelName", item.name)
        findNavController().navigate(R.id.action_channelsFragment_to_channelVideosFragment, bundle)
    }

    override fun onDelete(item: ChannelItem) {
        UiUtil.showGenericDeleteDialog(requireContext(), item.name) {
            channelsViewModel.delete(item)
        }
    }
}
