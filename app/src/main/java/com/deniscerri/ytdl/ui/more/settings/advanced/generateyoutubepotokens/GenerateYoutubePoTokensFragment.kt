package com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens

import android.app.Activity
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.models.YoutubeGeneratePoTokenItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.ui.more.cookies.WebViewActivity
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.webview.PoTokenWebViewLoginActivity
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.launch

class GenerateYoutubePoTokensFragment : Fragment() {
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences
    private lateinit var configuration : MutableList<YoutubeGeneratePoTokenItem>
    private lateinit var workManager : WorkManager

    private lateinit var webPoTokenResultLauncher : ActivityResultLauncher<Intent>

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

        initWeb()
    }

    private fun initWeb() {
        val conf = configuration.find { it.clients.any { it2 -> it2.contains("web") } }
            ?: YoutubeGeneratePoTokenItem(false, mutableListOf("mweb"), mutableListOf(), "", false)


        val switch = requireView().findViewById<MaterialSwitch>(R.id.web_client_switch)
        val gvs = requireView().findViewById<TextView>(R.id.content_gvs)
        val player = requireView().findViewById<TextView>(R.id.content_player)
        val subs = requireView().findViewById<TextView>(R.id.content_subs)
        val visitorData = requireView().findViewById<TextView>(R.id.content_visitordata)

        val playerClientDiv = requireView().findViewById<View>(R.id.playerclient_div)
        val playerClientText = requireView().findViewById<TextView>(R.id.content_playerclient)

        val regenerate = requireView().findViewById<MaterialButton>(R.id.regenerate_webview_potokens)
        val useVisitorData = requireView().findViewById<MaterialSwitch>(R.id.use_visitor_data)

        switch.isChecked = conf.enabled
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
            val webClients = requireContext().getStringArray(R.array.web_player_clients)

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
                        preferences.edit().putString("youtube_generated_po_tokens", Gson().toJson(configuration).toString()).apply()
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
                val streamingDataPoTokenResult = result.data!!.getStringExtra("streaming_potoken") ?: ""
                val playerRequestPoTokenResult = result.data!!.getStringExtra("player_potoken") ?: ""
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
                preferences.edit().putString("youtube_generated_po_tokens", Gson().toJson(configuration).toString()).apply()
            }
        }

        regenerate.setOnClickListener {
            showBottomSheet()
        }

        switch.setOnClickListener {
            configuration.remove(conf)
            conf.enabled = switch.isChecked
            useVisitorData.isEnabled = switch.isChecked
            configuration.add(conf)
            preferences.edit().putString("youtube_generated_po_tokens", Gson().toJson(configuration).toString()).apply()
            if (conf.poTokens.isEmpty()) {
                regenerate.performClick()
            }
        }

        useVisitorData?.apply {
            this.isChecked = conf.useVisitorData
            useVisitorData.jumpDrawablesToCurrentState()
            setOnCheckedChangeListener { _, b ->
                configuration.remove(conf)
                conf.useVisitorData = b
                configuration.add(conf)
                preferences.edit().putString("youtube_generated_po_tokens", Gson().toJson(configuration).toString()).apply()
            }
        }

    }

    private fun showBottomSheet(){
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
                editText.setSelection(editText.text.length)

                val regenerateBtn = layout.findViewById<MaterialButton>(R.id.getPoTokenBtn)!!
                regenerateBtn.isEnabled = false

                editText.doOnTextChanged { text, start, before, count ->
                    regenerateBtn.isEnabled = editText.text.toString().isYoutubeURL()
                }

                regenerateBtn.setOnClickListener {
                    layout.dismiss()

                    val intent = Intent(requireContext(), PoTokenWebViewLoginActivity::class.java)
                    intent.putExtra("url", "https://www.youtube.com")
                    intent.putExtra("redirect_url", "https://www.youtube.com/embed/${editText.text.toString().getIDFromYoutubeURL()}")
                    webPoTokenResultLauncher.launch(intent)
                }


                val imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
                intent.putExtra("url", "https://www.youtube.com/account")
                webPoTokenResultLauncher.launch(intent)
            }

            dialog.setNeutralButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }

            dialog.show()
        }

    }
}