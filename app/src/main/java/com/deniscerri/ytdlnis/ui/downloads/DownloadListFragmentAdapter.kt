package com.deniscerri.ytdlnis.ui.downloads

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadAudioFragment
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadCommandFragment
import com.deniscerri.ytdlnis.ui.downloadcard.DownloadVideoFragment

class DownloadListFragmentAdapter (
    fragmentManager : FragmentManager,
    lifecycle : Lifecycle,
    private val fragments: List<Fragment>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

}