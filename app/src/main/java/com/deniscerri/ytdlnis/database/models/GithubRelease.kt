package com.deniscerri.ytdlnis.database.models

data class GithubRelease(
    var tag_name: String,
    var body: String,
    var assets: List<GithubReleaseAsset>
)


data class GithubReleaseAsset(
    var name: String,
    var browser_download_url: String
)