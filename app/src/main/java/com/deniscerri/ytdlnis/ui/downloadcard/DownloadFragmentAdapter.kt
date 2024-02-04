package com.deniscerri.ytdlnis.ui.downloadcard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem

class DownloadFragmentAdapter (
    fragmentManager : FragmentManager,
    lifecycle : Lifecycle,
    private var result: ResultItem,
    private var downloadItem: DownloadItem?,
    private var nonSpecific: Boolean = false
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    fun setResultItem(res: ResultItem){
        result = res
    }

    fun setDownloadItem(dd: DownloadItem){
        downloadItem = dd
    }

    fun setTitleAuthor(t: String, a: String){
        result.title = t
        result.author = a
        downloadItem?.title = t
        downloadItem?.author = a
    }

    override fun getItemCount(): Int {
        return 3
    }

    override fun createFragment(position: Int): Fragment {
        return when(position){
            0 -> DownloadAudioFragment(result, downloadItem,"", nonSpecific)
            1 -> DownloadVideoFragment(result, downloadItem,"",  nonSpecific)
            else -> DownloadCommandFragment(result, downloadItem)
        }
    }

}