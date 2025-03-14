package com.deniscerri.ytdl.ui.more.settings.advanced

import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.ui.adapter.YoutubePlayerClientAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
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
            showYoutubePlayerClientSheet(
                null,
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


    private fun showYoutubePlayerClientSheet(currentValue: YoutubePlayerClientItem?, newValue: (item: YoutubePlayerClientItem) -> Unit, deleted: () -> Unit){
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.youtube_player_client_create_bottom_sheet)

        val title : TextInputLayout = bottomSheet.findViewById(R.id.title)!!
        val chipGroup : ChipGroup = bottomSheet.findViewById(R.id.chipGroup)!!
        val suggestedLabel : View = bottomSheet.findViewById(R.id.suggestedLabel)!!
        val okBtn : Button = bottomSheet.findViewById(R.id.client_create)!!
        val deleteBtn : Button = bottomSheet.findViewById(R.id.client_delete)!!
        deleteBtn.isVisible = currentValue != null

        val useOnlyPOToken : MaterialSwitch = bottomSheet.findViewById(R.id.use_only_po_token)!!
        useOnlyPOToken.isChecked = currentValue?.useOnlyPoToken ?: false

        val contentLinear : LinearLayout = bottomSheet.findViewById(R.id.contentLinear)!!

        val defaultChips = requireContext().getStringArray(R.array.youtube_player_clients).toMutableSet()

        title.isEndIconVisible = false
        title.editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                chipGroup.children.forEach { (it as Chip).isChecked = false }
                chipGroup.children.firstOrNull { (it as Chip).text == p0.toString() }?.apply {
                    (this as Chip).isChecked = true
                }
            }
        })

        val existingConfigsRaw = preferences.getString("youtube_player_clients", "[]")
        val existingConfigs = Gson().fromJson(existingConfigsRaw, Array<YoutubePlayerClientItem>::class.java).toMutableList()

        defaultChips.filter { it.isNotBlank() }.forEach {
            if (!existingConfigs.any { it2 -> it2.playerClient == it }) {
                val tmp = requireActivity().layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
                tmp.text = it
                tmp.setOnClickListener {
                    title.editText!!.setText(tmp.text.toString())
                }
                chipGroup.addView(tmp)
            }
        }

        if (chipGroup.children.count() == 0) {
            suggestedLabel.isVisible = false
        }

        val poTokenInputs = contentLinear.children.filter { it is TextInputLayout }.map { it as TextInputLayout }.toList()
        for(p in poTokenInputs) {
            p.setEndIconOnClickListener {
                val clipboard: ClipboardManager = requireActivity().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                p.editText!!.setText(clipboard.primaryClip?.getItemAt(0)?.text)
            }
        }

        currentValue?.apply {
            title.editText!!.setText(this.playerClient)
            for (t in this.poTokens) {
                poTokenInputs.firstOrNull { it.tag == "potoken_${t.context}" }?.editText?.setText(t.token)
            }
            okBtn.text = requireContext().getString(R.string.edit)
        }

        val urlRegexInput = contentLinear.findViewById<TextInputLayout>(R.id.url_regex)
        urlRegexInput.isEndIconVisible = false
        urlRegexInput.editText!!.doOnTextChanged { text, start, before, count ->
            urlRegexInput.isEndIconVisible = urlRegexInput.editText!!.text.isNotBlank()
        }

        val urlRegexChips = contentLinear.findViewById<ChipGroup>(R.id.urlRegexChipGroup)
        currentValue?.apply {
            for(chip in this.urlRegex) {
                val tmp = requireActivity().layoutInflater.inflate(R.layout.input_chip, urlRegexChips, false) as Chip
                tmp.text = chip
                tmp.setOnClickListener {
                    urlRegexChips.removeView(tmp)
                }
                urlRegexChips.addView(tmp)
            }
        }

        urlRegexInput.setEndIconOnClickListener {
            val text = urlRegexInput.editText!!.text
            urlRegexInput.editText!!.setText("")
            val tmp = requireActivity().layoutInflater.inflate(R.layout.input_chip, urlRegexChips, false) as Chip
            tmp.text = text
            tmp.setOnClickListener {
                urlRegexChips.removeView(tmp)
            }
            urlRegexChips.addView(tmp)
        }

        okBtn.setOnClickListener {
            val titleVal = title.editText!!.text.toString()
            if (titleVal.isBlank()) {
                title.error = "Player Client tag shouldn't be empty"
                return@setOnClickListener
            }

            if(existingConfigs.any { it2 -> it2.playerClient == titleVal && it2.enabled } && ( currentValue == null || currentValue.playerClient != titleVal )) {
                title.error = "Player Client is already created"
                return@setOnClickListener
            }

            if (useOnlyPOToken.isChecked && (poTokenInputs.filter { it.tag.toString().startsWith("potoken") }.all { it.editText!!.text.isBlank() })) {
                poTokenInputs.first().error = "You need to write at least one PO Token"
                return@setOnClickListener
            }

            val obj = YoutubePlayerClientItem(titleVal, mutableListOf(), true, useOnlyPOToken.isChecked)
            poTokenInputs.filter { it.editText!!.text.isNotBlank() && it.tag.toString().startsWith("potoken") }.forEach {
                obj.poTokens.add(YoutubePoTokenItem(it.tag.toString().split("potoken_")[1], it.editText!!.text.toString()))
            }

            val urlRegexes = urlRegexChips.children.map { (it as Chip).text.toString() }
            obj.urlRegex.addAll(urlRegexes)

            bottomSheet.cancel()
            newValue(obj)
        }

        deleteBtn.setOnClickListener {
            bottomSheet.cancel()
            deleted()
        }


        bottomSheet.show()
        bottomSheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }


    override fun onItemClick(item: YoutubePlayerClientItem, index: Int) {
        showYoutubePlayerClientSheet(
            item,
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