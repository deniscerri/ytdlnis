package com.deniscerri.ytdl.ui.more.cookies

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.ui.adapter.CookieAdapter
import com.deniscerri.ytdl.ui.more.cookies.WebViewActivity
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CookiesFragment : Fragment(), CookieAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: CookieAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var useCookies : MaterialSwitch
    private lateinit var cookiesViewModel: CookieViewModel
    private lateinit var cookiesList: List<CookieItem>
    private lateinit var noResults : RelativeLayout
    private lateinit var mainActivity: MainActivity
    private lateinit var preferences: SharedPreferences
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_cookies, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topAppBar = view.findViewById(R.id.logs_toolbar)
        useCookies = view.findViewById(R.id.use_cookies)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        cookiesList = listOf()
        noResults = view.findViewById(R.id.no_results)

        listAdapter =
            CookieAdapter(
                this,
                mainActivity
            )
        val newCookie = view.findViewById<Chip>(R.id.newCookie)
        recyclerView = view.findViewById(R.id.template_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("cookies")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        cookiesViewModel.items.observe(viewLifecycleOwner) {
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
            cookiesList = it
            listAdapter.submitList(it)
        }
        initMenu()
        initChips()


        useCookies.setOnCheckedChangeListener { compoundButton, b ->
            newCookie.isEnabled = useCookies.isChecked
            preferences.edit().putBoolean("use_cookies", useCookies.isChecked).apply()
        }

        val useCookiesPref = preferences.getBoolean("use_cookies", false)
        useCookies.isChecked = useCookiesPref
        useCookies.jumpDrawablesToCurrentState()
        newCookie.isEnabled = useCookiesPref
    }

    private fun initChips() {
        val new = view?.findViewById<Chip>(R.id.newCookie)
        new?.setOnClickListener {
            showBottomSheet(null)
        }
    }

    private fun initMenu() {
        topAppBar.menu.getItem(1).isChecked = preferences.getBoolean("use_header", false)
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.delete_cookies -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                    deleteDialog.setMessage(getString(R.string.confirm_delete_cookies_desc))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        cookiesViewModel.deleteAll()
                        runCatching {
                            FileUtil.getCookieFile(requireContext(), true){
                                File(it).apply { writeText("") }
                            }
                        }
                    }
                    deleteDialog.show()
                }
                R.id.use_header -> {
                    m.isChecked = !m.isChecked
                    preferences.edit().putBoolean("use_header", m.isChecked).apply()
                }
                R.id.import_clipboard -> {
                    lifecycleScope.launch(Dispatchers.IO){
                        cookiesViewModel.importFromClipboard()
                    }
                }
                R.id.export_clipboard -> {
                    cookiesViewModel.exportToClipboard()
                    Snackbar.make(recyclerView, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
                }
                R.id.export_file -> {
                    cookiesViewModel.exportToFile {f ->
                        if (f == null){
                            Snackbar.make(recyclerView, getString(R.string.couldnt_parse_file), Snackbar.LENGTH_LONG).show()
                        }else{
                            val snack = Snackbar.make(recyclerView, getString(R.string.backup_created_successfully), Snackbar.LENGTH_LONG)
                            snack.setAction(R.string.share) {
                                FileUtil.shareFileIntent(requireContext(), listOf(f.absolutePath))
                            }
                            snack.show()
                        }
                    }
                }
            }
            true
        }
    }

    private fun showBottomSheet(item: CookieItem?, position: Int = 0){
        lifecycleScope.launch {
            val layout = BottomSheetDialog(requireContext())
            layout.requestWindowFeature(Window.FEATURE_NO_TITLE)
            layout.setContentView(R.layout.cookie_bottom_sheet)

            val urlEditText = layout.findViewById<EditText>(R.id.url_edittext)!!
            val urlText = item?.url ?: "https://"
            urlEditText.setText(urlText)
            urlEditText.setSelection(urlEditText.text.length)

            val descriptionEditText = layout.findViewById<EditText>(R.id.title_edittext)!!
            descriptionEditText.setText(item?.description ?: "")
            layout.findViewById<TextInputLayout>(R.id.description_input_layout)?.isVisible = item != null

            val current = layout.findViewById<MaterialCardView>(R.id.current)!!
            current.isVisible = item != null
            item?.apply {
                current.findViewById<TextView>(R.id.currentText).apply {
                    setText(item.content)
                    if (preferences.getBoolean("use_code_color_highlighter", true)) {
                        enableTextHighlight()
                    }
                }
            }

            val clipboard = layout.findViewById<MaterialButton>(R.id.clipboard)!!
            val save = layout.findViewById<MaterialButton>(R.id.save)!!
            save.isVisible = item != null
            val getCookies = layout.findViewById<MaterialButton>(R.id.getCookiesBtn)!!

            getCookies.setOnClickListener {
                val myIntent = Intent(requireContext(), WebViewActivity::class.java)
                myIntent.putExtra("url", urlEditText.text.toString())
                myIntent.putExtra("description", descriptionEditText.text.toString())
                layout.dismiss()
                startActivity(myIntent)
            }

            item?.apply {
                clipboard.setOnClickListener {
                    UiUtil.copyToClipboard(cookiesViewModel.cookieHeader + "\n" + item.content, requireActivity())
                    layout.dismiss()
                }

                getCookies.text = getString(R.string.update)

                save.setOnClickListener {
                    item.description = descriptionEditText.text.toString()
                    item.url = urlEditText.text.toString()
                    cookiesViewModel.update(item)
                    listAdapter.notifyItemChanged(position)
                    layout.dismiss()
                }
            }

            urlEditText.doOnTextChanged { text, start, before, count ->
                getCookies.isEnabled = urlEditText.text.isNotEmpty()
            }

            val imm = mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            urlEditText.postDelayed({
                urlEditText.requestFocus()
                imm.showSoftInput(urlEditText, 0)
            }, 300)

            layout.show()
            layout.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            layout.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            getCookies.isEnabled = urlEditText.text.isNotEmpty()
        }

    }

    override fun onItemClick(cookieItem: CookieItem, position: Int) {
        showBottomSheet(cookieItem, position)
    }

    override fun onSelected(cookieItem: CookieItem) {
    }

    override fun onItemEnabledChanged(cookieItem: CookieItem, isEnabled: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                cookiesViewModel.changeCookieEnabledState(cookieItem.id, isEnabled)
            }
        }
    }

    override fun onDelete(cookieItem: CookieItem) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + cookieItem.url + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            cookiesViewModel.delete(cookieItem)
        }
        deleteDialog.show()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = cookiesList[position]
                        listAdapter.notifyItemChanged(position)
                        onDelete(deletedItem)
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    context,
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }
}