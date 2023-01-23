package com.deniscerri.ytdlnis.ui.downloadcard

import androidx.annotation.NonNull
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem

class DownloadFragmentAdapter (item : DownloadItem, fragmentManager : FragmentManager, lifecycle : Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {
    private val downloadItem = item

    override fun getItemCount(): Int {
        return 3
    }

    override fun createFragment(position: Int): Fragment {
        when (position) {
            0 -> {
                downloadItem.type = "audio"
                return DownloadAudioFragment(downloadItem)
            }
            1 -> {
                downloadItem.type = "video"
                return DownloadVideoFragment(downloadItem)
            }
        }
        downloadItem.type = "command"
        return DownloadCommandFragment(downloadItem)
    }
}