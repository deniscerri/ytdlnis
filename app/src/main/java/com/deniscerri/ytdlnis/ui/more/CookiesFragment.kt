package com.deniscerri.ytdlnis.ui.more

import android.content.Context.INPUT_METHOD_SERVICE
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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CookieItem
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.deniscerri.ytdlnis.ui.adapter.CookieAdapter
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
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
        newCookie.isEnabled = useCookiesPref
    }

    private fun initChips() {
        val new = view?.findViewById<Chip>(R.id.newCookie)
        new?.setOnClickListener {
            showDialog("")
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
                        kotlin.runCatching {
                            File(context?.cacheDir, "cookies.txt").apply { writeText("") }
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
                                UiUtil.shareFileIntent(requireContext(), listOf(f.absolutePath))
                            }
                            snack.show()
                        }
                    }
                }
            }
            true
        }
    }

    private fun showDialog(url: String){
        lifecycleScope.launch {
            var item = withContext(Dispatchers.IO){
                cookiesViewModel.getByURL(url)
            }


            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setTitle(getString(R.string.cookies))
            val inputLayout = layoutInflater.inflate(R.layout.textinput, null)
            val editText = inputLayout.findViewById<EditText>(R.id.url_edittext)
            inputLayout.findViewById<TextInputLayout>(R.id.url_textinput).hint = "URL"
            val text = if (url.isNullOrBlank()) "https://" else url
            editText.setText(text)
            editText.setSelection(editText.text.length)
            builder.setView(inputLayout)
            builder.setNeutralButton(
                getString(android.R.string.copy)
            ) { dialog: DialogInterface?, which: Int ->
                UiUtil.copyToClipboard(item.content, requireActivity())
            }
            builder.setPositiveButton(
                getString(R.string.get_cookies)
            ) { dialog: DialogInterface?, which: Int ->
                val myIntent = Intent(requireContext(), WebViewActivity::class.java)
                myIntent.putExtra("url", editText.text.toString())
                startActivity(myIntent)
            }

            // handle the negative button of the alert dialog
            builder.setNegativeButton(
                getString(R.string.cancel)
            ) { dialog: DialogInterface?, which: Int -> }
            val dialog = builder.create()
            editText.doOnTextChanged { text, start, before, count ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
            }
            dialog.show()
            val imm = mainActivity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            editText!!.postDelayed({
                editText.requestFocus()
                imm.showSoftInput(editText, 0)
            }, 300)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
        }

    }

    companion object {
        private const val TAG = "CookiesActivity"
    }

    override fun onItemClick(cookie: CookieItem) {
        showDialog(cookie.url)
    }

    override fun onSelected(cookie: CookieItem) {
    }

    override fun onDelete(cookie: CookieItem) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + cookie.url + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            cookiesViewModel.delete(cookie)
        }
        deleteDialog.show()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
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