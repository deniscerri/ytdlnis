package com.deniscerri.ytdl.ui.more.settings.advanced

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubeGeneratePoTokenItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.extractors.potoken.PoTokenGenerator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerateYoutubePoTokensFragment : Fragment() {
    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences
    private lateinit var configuration : MutableList<YoutubeGeneratePoTokenItem>
    private lateinit var poTokenGenerator: PoTokenGenerator

    private val sampleURL = "https://www.youtube.com/watch?v=aqz-KE-bpKQ" // Big Buck Bunny

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.generate_potokens))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        poTokenGenerator = PoTokenGenerator()
        return inflater.inflate(R.layout.fragment_generate_youtube_po_token, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configuration = Gson().fromJson(
            preferences.getString("youtube_generated_po_tokens", "[]"),
            Array<YoutubeGeneratePoTokenItem>::class.java
        ).toMutableList()

        initWeb()
    }

    private fun initWeb() {
        val conf = configuration.find { it.clients.any { it2 -> it2.contains("web") } }
            ?: YoutubeGeneratePoTokenItem(false, mutableListOf("mweb"), mutableListOf(), "")


        val switch = requireView().findViewById<MaterialSwitch>(R.id.web_client_switch)
        val gvs = requireView().findViewById<TextView>(R.id.content_gvs)
        val player = requireView().findViewById<TextView>(R.id.content_player)
        val visitorData = requireView().findViewById<TextView>(R.id.content_visitordata)

        val playerClientDiv = requireView().findViewById<View>(R.id.playerclient_div)
        val playerClientText = requireView().findViewById<TextView>(R.id.content_playerclient)

        val regenerate = requireView().findViewById<MaterialButton>(R.id.regenerate_webview_potokens)

        switch.isChecked = conf.enabled

        fun setValues(conf: YoutubeGeneratePoTokenItem) {
            gvs.text = conf.poTokens.find { it.context == "gvs" }?.token ?: ""
            player.text = conf.poTokens.find { it.context == "player" }?.token ?: ""
            visitorData.text = conf.visitorData
            playerClientText.text = conf.clients.joinToString(", ")
        }

        setValues(conf)

        val clicker = View.OnClickListener {
            UiUtil.copyToClipboard((it as TextView).text.toString(), requireActivity())
        }

        gvs.setOnClickListener(clicker)
        player.setOnClickListener(clicker)
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

        regenerate.setOnClickListener {
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    poTokenGenerator.getWebClientPoToken(sampleURL.getIDFromYoutubeURL())
                }

                if (res == null) {
                    Snackbar.make(requireView(), getString(R.string.network_error), Snackbar.LENGTH_LONG).show()
                }else{
                    configuration.remove(conf)
                    conf.poTokens.clear()
                    conf.poTokens.add(YoutubePoTokenItem("gvs", res.streamingDataPoToken ?: ""))
                    conf.poTokens.add(YoutubePoTokenItem("player", res.playerRequestPoToken))
                    conf.visitorData = res.visitorData

                    configuration.add(conf)
                    setValues(conf)
                    preferences.edit().putString("youtube_generated_po_tokens", Gson().toJson(configuration).toString()).apply()
                }
            }
        }

        switch.setOnClickListener {
            configuration.remove(conf)
            conf.enabled = switch.isChecked
            configuration.add(conf)
            preferences.edit().putString("youtube_generated_po_tokens", Gson().toJson(configuration).toString()).apply()
            if (conf.poTokens.isEmpty()) {
                regenerate.performClick()
            }
        }
    }
}