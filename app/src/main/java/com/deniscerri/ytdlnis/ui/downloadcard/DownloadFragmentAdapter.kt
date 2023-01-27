package com.deniscerri.ytdlnis.ui.downloadcard

import android.util.Log
import androidx.annotation.NonNull
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.google.gson.Gson

class DownloadFragmentAdapter (item : DownloadItem, fragmentManager : FragmentManager, lifecycle : Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {
    private val downloadItem = item

    override fun getItemCount(): Int {
        return 3
    }

    override fun createFragment(position: Int): Fragment {
        when (position) {
            0 -> {
                downloadItem.type = "audio"
                return DownloadAudioFragment(clone(downloadItem))
            }
            1 -> {
                downloadItem.type = "video"
                return DownloadVideoFragment(clone(downloadItem))
            }
        }
        downloadItem.type = "command"
        return DownloadCommandFragment(clone(downloadItem))
    }


    private fun clone(item: DownloadItem) : DownloadItem {
        val stringItem = Gson().toJson(item, DownloadItem::class.java)
        return Gson().fromJson(stringItem, DownloadItem::class.java)
    }
}