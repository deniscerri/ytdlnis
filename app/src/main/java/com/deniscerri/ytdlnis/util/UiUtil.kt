package com.deniscerri.ytdlnis.util

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.Format

class UiUtil(private val fileUtil: FileUtil) {
    fun populateFormatCard(formatCard : ConstraintLayout, chosenFormat: Format){
        Log.e("aa", chosenFormat.toString())
        formatCard.findViewById<TextView>(R.id.container).text = chosenFormat.container.uppercase()
        formatCard.findViewById<TextView>(R.id.format_note).text = chosenFormat.format_note.uppercase()
        formatCard.findViewById<TextView>(R.id.format_id).text = "id: ${chosenFormat.format_id}"
        val codec =
            if (chosenFormat.encoding != "") {
                chosenFormat.encoding.uppercase()
            }else if (chosenFormat.vcodec != "none" && chosenFormat.vcodec != ""){
                chosenFormat.vcodec.uppercase()
            } else {
                chosenFormat.acodec.uppercase()
            }
        if (codec == "" || codec == "none"){
            formatCard.findViewById<TextView>(R.id.codec).visibility = View.GONE
        }else{
            formatCard.findViewById<TextView>(R.id.codec).visibility = View.VISIBLE
            formatCard.findViewById<TextView>(R.id.codec).text = codec
        }
        formatCard.findViewById<TextView>(R.id.file_size).text = fileUtil.convertFileSize(chosenFormat.filesize)

    }
}