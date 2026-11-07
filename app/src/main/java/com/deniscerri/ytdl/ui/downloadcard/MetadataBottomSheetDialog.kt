package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.enums.MetadataTag
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MetadataBottomSheetDialog(
    private val items: List<DownloadItem> = emptyList(),
    private val nonSpecific: Boolean = false,
    private val onChanged: (() -> Unit)? = null
) : BottomSheetDialogFragment() {
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var fieldsContainer: ViewGroup
    private lateinit var shimmer: ShimmerFrameLayout
    private lateinit var autofill: Button
    private val inputs = mutableMapOf<MetadataTag, TextInputLayout>()
    private val allInputs = mutableListOf<TextInputLayout>()
    private var initialValues: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        ytdlpViewModel = ViewModelProvider(requireActivity())[YTDLPViewModel::class.java]
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.metadata_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())
        // fields at the bottom have to stay reachable above the keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // recreated by the system, the items only come through the constructor
        if (items.isEmpty()) {
            dismiss()
            return
        }

        dialog.setOnShowListener {
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            BottomSheetBehavior.from(view.parent as View).peekHeight = displayMetrics.heightPixels
        }

        fieldsContainer = view.findViewById(R.id.metadata_fields)
        shimmer = view.findViewById(R.id.metadata_form_shimmer)
        shimmer.hideShimmer()
        autofill = view.findViewById(R.id.metadata_autofill)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        addTags()

        val splitAudio = items.first().type == DownloadType.audio && items.first().audioPreferences.splitByChapters
        view.findViewById<View>(R.id.metadata_note).isVisible = splitAudio || !prefs.getBoolean("embed_metadata", true)

        // no single url to read metadata from
        autofill.isVisible = !nonSpecific && items.first().url.isNotBlank() && items.size == 1
        autofill.setOnClickListener { autofill() }
        view.findViewById<Button>(R.id.metadata_done).setOnClickListener {
            val current = inputs.entries.associate { (tag, layout) -> tag.key to layout.editText?.text?.toString().orEmpty() }
            val allTags = items.mapNotNull { MetadataTag.tagsOf(it) }
            // no callback without edits, since the multiple sheet writes every item to the database in it
            if (MetadataTag.applyEdits(allTags, initialValues, current)) onChanged?.invoke()
            dismiss()
        }
    }

    private fun addField(label: Int, value: String, parent: ViewGroup = fieldsContainer): TextInputLayout {
        val layout = LayoutInflater.from(requireContext()).inflate(R.layout.metadata_field_item, parent, false) as TextInputLayout
        layout.hint = getString(label)
        layout.editText!!.setText(value)
        parent.addView(layout)
        allInputs.add(layout)
        return layout
    }

    private fun newColumnsRow() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        dividerDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.divider_10dp)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        fieldsContainer.addView(this)
    }

    private fun addTags() {
        initialValues = MetadataTag.commonValues(items.mapNotNull { MetadataTag.tagsOf(it) })
        var openRow: LinearLayout? = null
        MetadataTag.forType(items.first().type).forEach { tag ->
            val parent = if (!tag.short) fieldsContainer else openRow ?: newColumnsRow()
            val layout = addField(tag.label, initialValues[tag.key].orEmpty(), parent)
            // the row is full once it has its second column, and a full width field ends it too
            openRow = if (!tag.short || openRow != null) null else parent as LinearLayout
            if (tag.short) {
                layout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            layout.editText!!.inputType = tag.inputType
            inputs[tag] = layout
        }
    }

    private fun setLoading(loading: Boolean) {
        autofill.isEnabled = !loading
        allInputs.forEach { it.isEnabled = !loading }
        if (loading) shimmer.showShimmer(true) else shimmer.hideShimmer()
    }

    private fun autofill() {
        setLoading(true)
        lifecycleScope.launch {
            val values = try {
                withContext(Dispatchers.IO) { ytdlpViewModel.getMetadata(items.first()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
            setLoading(false)
            if (values.isEmpty()) {
                Toast.makeText(requireContext(), R.string.metadata_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }
            // only empty fields, never over what the user typed
            inputs.forEach { (tag, layout) ->
                val value = values[tag.key]
                if (value != null && layout.editText?.text.isNullOrBlank()) layout.editText?.setText(value)
            }
        }
    }
}
