package com.deniscerri.ytdlnis.ui.more

import android.content.ClipboardManager
import android.content.Context
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.TemplatesAdapter
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout


class CommandTemplatesActivity : AppCompatActivity(), TemplatesAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var templatesAdapter: TemplatesAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var uiUtil: UiUtil
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_command_templates)
        context = baseContext

        topAppBar = findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        templatesAdapter =
            TemplatesAdapter(
                this,
                this@CommandTemplatesActivity
            )
        recyclerView = findViewById(R.id.template_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = templatesAdapter

        uiUtil = UiUtil(FileUtil())

        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        commandTemplateViewModel.items.observe(this) {
            templatesAdapter.submitList(it)
        }
        initMenu()
        initChips()
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.export_clipboard) {
                commandTemplateViewModel.exportToClipboard()
            }else if (itemId == R.id.import_clipboard){
                commandTemplateViewModel.importFromClipboard()
            }
            true
        }
    }

    private fun initChips() {
        val new = findViewById<Chip>(R.id.newTemplate)
        new.setOnClickListener {
            uiUtil.showCreationSheet(this@CommandTemplatesActivity, this, commandTemplateViewModel) {}
        }
        val shortcuts = findViewById<Chip>(R.id.shortcuts)
        shortcuts.setOnClickListener {
            uiUtil.showShortcutsSheet(this@CommandTemplatesActivity,this, commandTemplateViewModel)
        }
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }

    override fun onItemClick(commandTemplate: CommandTemplate) {
        TODO("Not yet implemented")
    }

    override fun onSelected(commandTemplate: CommandTemplate) {
        TODO("Not yet implemented")
    }
}