package com.deniscerri.ytdl.ui.more

import android.content.DialogInterface
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.PlaylistItem
import com.deniscerri.ytdl.database.viewmodel.PlaylistViewModel
import com.deniscerri.ytdl.ui.adapter.PlaylistsAdapter
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaylistsFragment : Fragment(), PlaylistsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: PlaylistsAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var noResults: RelativeLayout
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        topAppBar = view.findViewById(R.id.toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        noResults = view.findViewById(R.id.no_results)

        listAdapter = PlaylistsAdapter(this, mainActivity)
        recyclerView = view.findViewById(R.id.playlists_recyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        recyclerView.adapter = listAdapter

        playlistViewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]
        playlistViewModel.items.observe(viewLifecycleOwner) {
            noResults.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            listAdapter.submitList(it)
        }

        view.findViewById<Chip>(R.id.addPlaylist).setOnClickListener {
            PlaylistDialogs.showCreatePlaylistDialog(this, playlistViewModel, lifecycleScope)
        }

        initMenu()
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            if (m.itemId == R.id.delete_playlists) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.confirm_delete_history))
                    .setMessage(getString(R.string.confirm_delete_playlists_desc))
                    .setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        lifecycleScope.launchWhenStarted { playlistViewModel.deleteAllPlaylists() }
                    }
                    .show()
            }
            true
        }
    }

    fun scrollToTop() {
        recyclerView.scrollToPosition(0)
    }

    override fun onItemClick(item: PlaylistItem) {
        findNavController().navigate(
            R.id.action_playlistsFragment_to_playlistDetailFragment,
            Bundle().apply { putLong("playlistId", item.id) }
        )
    }

    override fun onLongClick(item: PlaylistItem) {
        UiUtil.showGenericDeleteDialog(requireContext(), item.title) {
            lifecycleScope.launchWhenStarted { playlistViewModel.deletePlaylist(item) }
        }
    }
}
