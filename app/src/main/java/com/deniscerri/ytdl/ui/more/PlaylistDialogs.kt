package com.deniscerri.ytdl.ui.more

import android.text.InputType
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.PlaylistItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ChannelsViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.PlaylistViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlaylistDialogs {
    fun showCreatePlaylistDialog(
        fragment: Fragment,
        playlistViewModel: PlaylistViewModel,
        lifecycleScope: LifecycleCoroutineScope,
        onCreated: (suspend (PlaylistItem) -> Unit)? = null
    ) {
        val input = AppCompatEditText(fragment.requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = fragment.getString(R.string.playlist_name)
        }
        var selectedType = DownloadType.audio
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setSingleChoiceItems(
                arrayOf(fragment.getString(R.string.audio), fragment.getString(R.string.video)),
                0
            ) { _, which -> selectedType = if (which == 0) DownloadType.audio else DownloadType.video }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setPositiveButton(R.string.ok) { _, _ ->
                val title = input.text.toString().trim()
                if (title.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    val playlist = PlaylistItem(title = title, type = selectedType)
                    val id = withContext(Dispatchers.IO) { playlistViewModel.insertPlaylist(playlist) }
                    onCreated?.invoke(playlist.copy(id = id))
                }
            }
            .show()
    }

    fun showAddToPlaylistDialog(
        fragment: Fragment,
        result: ResultItem,
        playlistViewModel: PlaylistViewModel,
        channelsViewModel: ChannelsViewModel,
        downloadViewModel: DownloadViewModel,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) { playlistViewModel.getAll() }
            val labels = playlists.map { it.title }.plus(fragment.getString(R.string.new_playlist)).toTypedArray()
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.add_to_playlist)
                .setItems(labels) { _, which ->
                    if (which == playlists.size) {
                        showCreatePlaylistDialog(fragment, playlistViewModel, lifecycleScope) { playlist ->
                            addResult(fragment, playlistViewModel, channelsViewModel, downloadViewModel, playlist, result)
                        }
                    } else {
                        lifecycleScope.launch {
                            addResult(fragment, playlistViewModel, channelsViewModel, downloadViewModel, playlists[which], result)
                        }
                    }
                }
                .show()
        }
    }

    private suspend fun addResult(
        fragment: Fragment,
        playlistViewModel: PlaylistViewModel,
        channelsViewModel: ChannelsViewModel,
        downloadViewModel: DownloadViewModel,
        playlist: PlaylistItem,
        result: ResultItem
    ) {
        val added = playlistViewModel.addEntry(playlist, result)
        val entries = withContext(Dispatchers.IO) { playlistViewModel.getEntries(playlist.id) }
        playlistViewModel.prefetchWindow(
            playlist,
            entries,
            playlistViewModel.getCursor(playlist.id),
            channelsViewModel,
            downloadViewModel
        )
        fragment.view?.let {
            Snackbar.make(
                it,
                fragment.getString(if (added) R.string.added_to_playlist else R.string.playlist_item_exists),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}
