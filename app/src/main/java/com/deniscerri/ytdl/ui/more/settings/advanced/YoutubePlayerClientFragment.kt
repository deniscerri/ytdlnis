package com.deniscerri.ytdl.ui.more.settings.advanced

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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.ui.adapter.YoutubePlayerClientAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.gson.Gson


class YoutubePlayerClientFragment : Fragment(), YoutubePlayerClientAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: YoutubePlayerClientAdapter
    private lateinit var noResults : RelativeLayout
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences
    private lateinit var currentList: MutableList<YoutubePlayerClientItem>
    private lateinit var currentListRaw: String
    private lateinit var dragHandle: View
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.player_client))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_youtube_player_clients, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        noResults = view.findViewById(R.id.no_results)
        dragHandle = view.findViewById(R.id.drag)

        val itemTouchHelper = ItemTouchHelper(dragDropHelper)
        listAdapter = YoutubePlayerClientAdapter(this,settingsActivity, itemTouchHelper)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        itemTouchHelper.attachToRecyclerView(recyclerView)
        currentListRaw = preferences.getString("youtube_player_clients", "[]")!!
        currentList = Gson().fromJson(currentListRaw, Array<YoutubePlayerClientItem>::class.java).toMutableList()
        listAdapter.submitList(currentList.toList())

        val newClient = view.findViewById<Chip>(R.id.newClient)
        newClient.setOnClickListener {
            UiUtil.showYoutubePlayerClientSheet(
                settingsActivity, preferences, null,
                newValue = { newItem ->
                    currentList.add(newItem)
                    currentListRaw = Gson().toJson(currentList).toString()
                    updateRecords()
                    listAdapter.submitList(currentList.toList())
                    checkNoResults()
                },
                deleted = {
                }
            )
        }

        checkNoResults()

        dragHandle.setOnClickListener {
            listAdapter.toggleShowDragHandle()
        }
    }

    private fun updateRecords() {
        preferences.edit().putString("youtube_player_clients", currentListRaw).apply()
    }

    private fun checkNoResults() {
        val hasNoResults = currentList.isEmpty()
        noResults.isVisible = hasNoResults
        recyclerView.isVisible = !hasNoResults
        dragHandle.isVisible = currentList.size > 1
    }


    override fun onItemClick(item: YoutubePlayerClientItem, index: Int) {
        UiUtil.showYoutubePlayerClientSheet(
            settingsActivity, preferences, item,
            newValue = { newItem ->
                currentList.remove(item)
                currentList.add(newItem)
                currentListRaw = Gson().toJson(currentList).toString()
                updateRecords()
                listAdapter.submitList(currentList.toList())
            },
            deleted = {
                onDeleteClick(item, index)
            }
        )
    }

    override fun onStatusToggle(item: YoutubePlayerClientItem, enabled: Boolean, index: Int) {
        currentList[currentList.indexOf(item)].enabled = enabled
        currentListRaw = Gson().toJson(currentList).toString()
        updateRecords()
        listAdapter.submitList(currentList.toList())
    }

    override fun onDeleteClick(item: YoutubePlayerClientItem, index: Int) {
        UiUtil.showGenericDeleteDialog(requireContext(), item.playerClient) {
            currentList.remove(item)
            currentListRaw = Gson().toJson(currentList).toString()
            updateRecords()
            listAdapter.submitList(currentList.toList())
            checkNoResults()
        }
    }

    var movedToNewPositionTag = ""
    private val dragDropHelper: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                movedToNewPositionTag = target.itemView.tag.toString()
                listAdapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)
                if (ItemTouchHelper.ACTION_STATE_DRAG == actionState) {
                    /**
                     * Change alpha, scale and elevation on drag.
                     */
                    (viewHolder?.itemView as? MaterialCardView)?.also {
                        AnimatorSet().apply {
                            this.duration = 100L
                            this.interpolator = AccelerateDecelerateInterpolator()

                            playTogether(
                                UiUtil.getAlphaAnimator(it, 0.7f),
                                UiUtil.getScaleXAnimator(it, 1.02f),
                                UiUtil.getScaleYAnimator(it, 1.02f),
                                UiUtil.getElevationAnimator(it, R.dimen.elevation_6dp)
                            )
                        }.start()
                    }
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                /**
                 * Clear alpha, scale and elevation after drag/swipe
                 */
                (viewHolder.itemView as? MaterialCardView)?.also {
                    AnimatorSet().apply {
                        this.duration = 100L
                        this.interpolator = AccelerateDecelerateInterpolator()

                        playTogether(
                            UiUtil.getAlphaAnimator(it, 1f),
                            UiUtil.getScaleXAnimator(it, 1f),
                            UiUtil.getScaleYAnimator(it, 1f),
                            UiUtil.getElevationAnimator(it, R.dimen.elevation_2dp)
                        )
                    }.start()
                }

                val currentIndex = currentList.indexOfFirst { it.playerClient == viewHolder.itemView.tag.toString() }
                val newIndex = currentList.indexOfFirst { it.playerClient == movedToNewPositionTag }

                val newList = mutableListOf<YoutubePlayerClientItem>()

                if (currentIndex != newIndex && currentIndex >= 0 && newIndex >= 0) {
                    Log.e("AAAAAAAAAAAAA", "${currentIndex} ${newIndex}")
                    if(currentIndex > newIndex) {
                        newList.addAll(currentList.subList(0, newIndex))
                        newList.add(currentList[currentIndex])
                        currentList.remove(currentList[currentIndex])
                        newList.addAll(currentList.subList(newIndex, currentList.size))
                    }else{
                        val toMove = currentList[currentIndex]
                        newList.addAll(currentList.subList(0, currentIndex))
                        newList.addAll(currentList.subList(currentIndex + 1, newIndex + 1))
                        newList.add(toMove)
                        newList.addAll(currentList.subList(newIndex + 1, currentList.size))
                    }

                    currentList.clear()
                    currentList.addAll(newList)
                    currentListRaw = Gson().toJson(currentList).toString()
                    updateRecords()
                    listAdapter.submitList(currentList.toList())
                    listAdapter.notifyDataSetChanged()
                }

            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
        }
}