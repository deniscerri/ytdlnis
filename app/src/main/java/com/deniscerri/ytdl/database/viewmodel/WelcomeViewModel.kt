package com.deniscerri.ytdl.database.viewmodel

import androidx.lifecycle.ViewModel

/** State shared by the welcome pages. Nothing is written to preferences until the user finishes. */
class WelcomeViewModel : ViewModel() {
    var page = 0

    var updateApp = true
    var autoUpdateYtdlp = true

    // matches what the yt-dlp update settings store: the value is a channel name or a repo, the label its title
    var ytdlpSource = "stable"
    var ytdlpSourceLabel = ""
}