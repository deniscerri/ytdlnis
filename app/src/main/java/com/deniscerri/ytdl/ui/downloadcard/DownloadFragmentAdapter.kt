package com.deniscerri.ytdl.ui.downloadcard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem

class DownloadFragmentAdapter (
    fragmentManager : FragmentManager,
    lifecycle : Lifecycle,
    private var result: ResultItem?,
    private var downloadItem: DownloadItem?,
    nonSpecific: Boolean = false,
    var isIncognito: Boolean = false
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    fun setResultItem(res: ResultItem){
        result = res

        fragments.forEachIndexed { idx, it ->
            when(idx) {
                0 -> kotlin.runCatching { (it as DownloadAudioFragment).updateUI(res) }
                1 -> kotlin.runCatching { (it as DownloadVideoFragment).updateUI(res) }
                2 -> kotlin.runCatching { (it as DownloadCommandFragment).updateUI(res) }
            }
        }
    }

    private fun setTitleAuthor(t: String, a: String){
        result?.title = t
        result?.author = a
        downloadItem?.title = t
        downloadItem?.author = a
    }


    val fragments = listOf<Fragment>(
        DownloadAudioFragment(result, downloadItem,"", nonSpecific),
        DownloadVideoFragment(result, downloadItem,"", nonSpecific),
        DownloadCommandFragment(result, downloadItem)
    )

    override fun getItemCount(): Int {
        return fragments.size
    }


    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

    fun updateWhenSwitching(currentViewPagerItem : Int) {
        val prevDownloadItem = getDownloadItem(
            if (currentViewPagerItem == 1) 0 else 1
        )
        setTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)

        when(currentViewPagerItem){
            0 -> {
                kotlin.runCatching {
                    (fragments[0] as DownloadAudioFragment).apply {
                        updateTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)
                        updateSelectedAudioFormat(getDownloadItem(1).videoPreferences.audioFormatIDs.first())
                    }
                }
            }
            1 -> {
                kotlin.runCatching {
                    (fragments[1] as DownloadVideoFragment).apply {
                        updateTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)
                        updateSelectedAudioFormat(getDownloadItem(0).format)
                    }
                }
            }
            2 -> {
                kotlin.runCatching {
                    (fragments[2] as DownloadCommandFragment).apply {
                        updateTitleAuthor(prevDownloadItem.title, prevDownloadItem.author)
                    }
                }
            }
            else -> {}
        }
    }

    fun getDownloadItem(position: Int) : DownloadItem {
        return when(position) {
            0 -> (fragments[0] as DownloadAudioFragment).downloadItem
            1 -> (fragments[1] as DownloadVideoFragment).downloadItem
            else -> (fragments[2] as DownloadCommandFragment).downloadItem
        }.apply {
            incognito = isIncognito
        }
    }

}