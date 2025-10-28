package com.deniscerri.ytdl.ui.more.settings.updating

import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.ui.adapter.ChangelogAdapter
import com.deniscerri.ytdl.ui.adapter.YoutubePlayerClientAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ChangeLogFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: ChangelogAdapter
    private lateinit var noResults : RelativeLayout
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.changelog))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_changelog, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        noResults = view.findViewById(R.id.no_results)

        listAdapter = ChangelogAdapter(settingsActivity)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()

        val updateUtil = UpdateUtil(requireContext())
        lifecycleScope.launch {
            val releases = withContext(Dispatchers.IO) {
                updateUtil.getGithubReleases()
            }
            listAdapter.submitList(releases)
            noResults.isVisible = releases.isEmpty()
            recyclerView.isVisible = releases.isNotEmpty()
        }

    }
}