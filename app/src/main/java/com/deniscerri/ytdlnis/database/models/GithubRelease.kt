package com.deniscerri.ytdlnis.database.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class GithubRelease(
    @SerializedName(value = "html_url")
    val html_url: String,
    @SerializedName(value = "tag_name")
    var tag_name: String,
    @SerializedName(value = "body")
    var body: String,
    @SerializedName(value = "published_at")
    var published_at: Date,
    @SerializedName(value = "assets")
    var assets: List<GithubReleaseAsset>
)


data class GithubReleaseAsset(
    @SerializedName(value = "name")
    var name: String,
    @SerializedName(value = "browser_download_url")
    var browser_download_url: String
)