package com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens

import android.app.Activity
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.models.YoutubeGeneratePoTokenItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.services.BgUtilsPoTokenGeneratorService
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.webview.PoTokenWebViewLoginActivity
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerateYoutubePoTokensFragment : Fragment() {
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences
    private lateinit var configuration: MutableList<YoutubeGeneratePoTokenItem>
    private lateinit var workManager: WorkManager

    private lateinit var webPoTokenResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.generate_potokens))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        workManager = WorkManager.getInstance(requireContext())
        return inflater.inflate(R.layout.fragment_generate_youtube_po_token, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        configuration = kotlin.runCatching {
            Gson().fromJson(
                preferences.getString("youtube_generated_po_tokens", "[]")!!.ifEmpty { "[]" },
                Array<YoutubeGeneratePoTokenItem>::class.java
            ).toMutableList()
        }.getOrDefault(mutableListOf())

        RuntimeManager.getInstance().assertInit()
        initBGUtils()
        initWeb()
    }

    private fun initBGUtils() {
        val useBgUtils = preferences.getBoolean("use_bgutils_potoken_generator", false)
        val bgUtilsMethod = preferences.getString("bgutils_potoken_method", "generation_script")


        requireView().findViewById<LinearLayout>(R.id.bgutils_potoken_provider_collapsible).isVisible = useBgUtils

        requireView().findViewById<MaterialSwitch>(R.id.bgutils_potoken_provider_switch).apply {
            this.isChecked = useBgUtils
            val denoIsInstalled = RuntimeManager.getInstance().denoLocation.isAvailable
            val nodeIsInstalled = RuntimeManager.getInstance().nodeLocation.isAvailable

            this.setOnClickListener {
                if (!denoIsInstalled && !nodeIsInstalled) {
                    this.isChecked = false
                    Snackbar.make(requireActivity().findViewById(android.R.id.content), context.getString(R.string.please_install_package_or_package, "NodeJS", "Deno"), Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                preferences.edit(commit = true) {
                    putBoolean("use_bgutils_potoken_generator", this@apply.isChecked)
                }
                requireView().findViewById<LinearLayout>(R.id.bgutils_potoken_provider_collapsible).isVisible = this.isChecked

                if (!this.isChecked) {
                    BgUtilsPoTokenGeneratorUtil.stopServer(context)
                } else {
                    val builder = MaterialAlertDialogBuilder(context)
                    builder.setTitle("BgUtils POT Provider")
                    builder.setMessage(getString(R.string.please_wait))
                    builder.setCancelable(false)

                    val dialog = builder.create()
                    dialog.show()

                    lifecycleScope.launch {
                        val resp = withContext(Dispatchers.IO) {
                            BgUtilsPoTokenGeneratorUtil.runServer(context) { progress ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.Main) {
                                        dialog.setMessage(progress)
                                    }
                                }
                            }
                        }

                        resp.onSuccess {
                            dialog.dismiss()
                        }
                        resp.onFailure { error ->
                            dialog.dismiss()
                            view?.apply {
                                Snackbar.make(this, error.message ?: "", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            val scriptRadio = requireView().findViewById<RadioButton>(R.id.bgutils_pot_script_radio)
            val serverRadio = requireView().findViewById<RadioButton>(R.id.bgutils_pot_server_radio)

            scriptRadio.apply {
                isChecked = bgUtilsMethod == "generation_script" && nodeIsInstalled
                alpha = if (nodeIsInstalled) 1f else 0.3f

                setOnCheckedChangeListener { button, bool ->
                    if (nodeIsInstalled) {
                        preferences.edit(commit = true) {
                            putString("bgutils_potoken_method", "generation_script")
                        }
                        BgUtilsPoTokenGeneratorUtil.stopServer(context)
                    } else {
                        Snackbar.make(requireActivity().findViewById(android.R.id.content), context.getString(R.string.please_install_package, "NodeJS"), Snackbar.LENGTH_SHORT).show()
                        post {
                            serverRadio.performClick()
                        }
                    }
                }
            }

            serverRadio.apply {
                isChecked = bgUtilsMethod == "server"
                setOnClickListener {
                    preferences.edit(commit = true) {
                        putString("bgutils_potoken_method", "server")
                    }
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            BgUtilsPoTokenGeneratorUtil.runServer(context) {}
                        }
                    }
                }
            }
        }

        requireView().findViewById<Button>(R.id.redownload_bgutils).apply {
            this.setOnClickListener {
                val builder = MaterialAlertDialogBuilder(context)
                builder.setTitle("BgUtils POT Provider")
                builder.setMessage(getString(R.string.downloading_update))
                builder.setCancelable(false)

                val dialog = builder.create()
                dialog.show()

                lifecycleScope.launch {
                    val resp = withContext(Dispatchers.IO) {
                        BgUtilsPoTokenGeneratorUtil.downloadFiles(context, runServerAfterwards = bgUtilsMethod == "server") { progress ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.Main) {
                                    dialog.setMessage(progress)
                                }
                            }
                        }
                    }

                    resp.onSuccess {
                        dialog.dismiss()
                    }
                    resp.onFailure { error ->
                        dialog.dismiss()
                        view?.apply {
                            Snackbar.make(this, error.message ?: "", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun initWeb() {
        val confTmp = configuration.find { it.clients.any { it2 -> it2.contains("web") } }
        val conf = confTmp ?: YoutubeGeneratePoTokenItem(false, mutableListOf("mweb"), mutableListOf(), "", false)

        val switch = requireView().findViewById<MaterialSwitch>(R.id.manual_potoken_generator_switch)
        val gvs = requireView().findViewById<TextView>(R.id.content_gvs)
        val player = requireView().findViewById<TextView>(R.id.content_player)
        val subs = requireView().findViewById<TextView>(R.id.content_subs)
        val visitorData = requireView().findViewById<TextView>(R.id.content_visitordata)

        val playerClientDiv = requireView().findViewById<View>(R.id.playerclient_div)
        val playerClientText = requireView().findViewById<TextView>(R.id.content_playerclient)

        val regenerate =
            requireView().findViewById<MaterialButton>(R.id.regenerate_webview_potokens)
        val useVisitorData = requireView().findViewById<MaterialSwitch>(R.id.use_visitor_data)

        switch.isChecked = conf.enabled
        requireView().findViewById<LinearLayout>(R.id.manual_potoken_generator_collapsible).isVisible = conf.enabled
        switch.jumpDrawablesToCurrentState()
        useVisitorData.isEnabled = conf.enabled

        fun setValues(conf: YoutubeGeneratePoTokenItem) {
            gvs.text = conf.poTokens.find { it.context == "gvs" }?.token ?: ""
            player.text = conf.poTokens.find { it.context == "player" }?.token ?: ""
            subs.text = conf.poTokens.find { it.context == "subs" }?.token ?: ""
            visitorData.text = conf.visitorData
            playerClientText.text = conf.clients.joinToString(", ")
        }

        setValues(conf)

        val clicker = View.OnClickListener {
            UiUtil.copyToClipboard((it as TextView).text.toString(), requireActivity())
        }

        gvs.setOnClickListener(clicker)
        player.setOnClickListener(clicker)
        subs.setOnClickListener(clicker)
        visitorData.setOnClickListener(clicker)

        playerClientDiv.setOnClickListener {
            val webClients = requireContext().resources.getStringArray(R.array.web_player_clients)

            val selectedItems = webClients.map {
                conf.clients.contains(it)
            }.toBooleanArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.player_client))
                .setMultiChoiceItems(webClients, selectedItems) { _, which, isChecked ->
                    selectedItems[which] = isChecked
                }
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newValues = webClients
                        .filterIndexed { index, _ -> selectedItems[index] }
                        .toMutableSet()

                    if (newValues.isNotEmpty()) {
                        configuration.remove(conf)
                        conf.clients.clear()
                        conf.clients.addAll(newValues)
                        configuration.add(conf)
                        preferences.edit().putString(
                            "youtube_generated_po_tokens",
                            Gson().toJson(configuration).toString()
                        ).apply()
                        setValues(conf)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        webPoTokenResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val streamingDataPoTokenResult =
                    result.data!!.getStringExtra("streaming_potoken") ?: ""
                val playerRequestPoTokenResult =
                    result.data!!.getStringExtra("player_potoken") ?: ""
                val subsRequestPoTokenResult = result.data!!.getStringExtra("subs_potoken") ?: ""
                val visitorDataResult = result.data!!.getStringExtra("visitor_data") ?: ""


                configuration.remove(conf)
                conf.poTokens.clear()
                conf.poTokens.add(YoutubePoTokenItem("gvs", streamingDataPoTokenResult))
                conf.poTokens.add(YoutubePoTokenItem("player", playerRequestPoTokenResult))
                conf.poTokens.add(YoutubePoTokenItem("subs", subsRequestPoTokenResult))
                conf.visitorData = visitorDataResult

                configuration.add(conf)
                setValues(conf)
                preferences.edit(commit = true) {
                    putString(
                        "youtube_generated_po_tokens",
                        Gson().toJson(configuration).toString()
                    )
                }
            }
        }

        regenerate.setOnClickListener {
            showBottomSheet()
        }

        switch.setOnClickListener {
            requireView().findViewById<LinearLayout>(R.id.manual_potoken_generator_collapsible).isVisible = switch.isChecked

            configuration.remove(conf)
            conf.enabled = switch.isChecked
            useVisitorData.isEnabled = switch.isChecked
            configuration.add(conf)
            preferences.edit(commit = true) {
                putString(
                    "youtube_generated_po_tokens",
                    Gson().toJson(configuration).toString()
                )
            }
            if (switch.isChecked) {
                if (conf.poTokens.isEmpty()) {
                    regenerate.performClick()
                }
            }
        }

        useVisitorData?.apply {
            this.isChecked = conf.useVisitorData
            useVisitorData.jumpDrawablesToCurrentState()
            setOnCheckedChangeListener { _, b ->
                configuration.remove(conf)
                conf.useVisitorData = b
                configuration.add(conf)
                preferences.edit().putString(
                    "youtube_generated_po_tokens",
                    Gson().toJson(configuration).toString()
                ).apply()
            }
        }

    }

    private fun showBottomSheet() {
        lifecycleScope.launch {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(getString(R.string.generate_potokens))

            val text = getString(R.string.potoken_guide)
            dialog.setMessage(text)
            dialog.setNegativeButton(getString(R.string.no_auth)) { dialogInterface: DialogInterface, _: Int ->

                val layout = BottomSheetDialog(requireContext())
                layout.requestWindowFeature(Window.FEATURE_NO_TITLE)
                layout.setContentView(R.layout.generate_po_token_url_bottom_sheet)

                val editText = layout.findViewById<EditText>(R.id.url_edittext)!!
                val regenerateBtn = layout.findViewById<MaterialButton>(R.id.getPoTokenBtn)!!
                val placeHolderURL = "https://youtu.be/aqz-KE-bpKQ" //Big Buck Bunny
                editText.doOnTextChanged { text, start, before, count ->
                    regenerateBtn.isEnabled = editText.text.toString().isYoutubeURL()
                }
                editText.setText(placeHolderURL)
                editText.setSelection(editText.text.length)

                regenerateBtn.setOnClickListener {
                    layout.dismiss()

                    val intent = Intent(requireContext(), PoTokenWebViewLoginActivity::class.java)
                    intent.putExtra("url", "https://www.youtube.com")
                    intent.putExtra(
                        "redirect_url",
                        "https://www.youtube.com/watch?v=${
                            editText.text.toString().getIDFromYoutubeURL()
                        }"
                    )
                    intent.putExtra("no_auth", true)
                    webPoTokenResultLauncher.launch(intent)
                }


                val imm =
                    requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                editText.postDelayed({
                    editText.requestFocus()
                    imm.showSoftInput(editText, 0)
                }, 300)

                layout.show()
                layout.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                layout.window!!.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            dialog.setPositiveButton(getString(R.string.auth)) { dialogInterface: DialogInterface, _: Int ->
                val intent = Intent(requireContext(), PoTokenWebViewLoginActivity::class.java)
                intent.putExtra("url", "https://youtube.com/signin")
                intent.putExtra("no_auth", false)
                webPoTokenResultLauncher.launch(intent)
            }

            dialog.setNeutralButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }

            dialog.show()
        }

    }
}