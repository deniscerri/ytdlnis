package com.deniscerri.ytdl.database.enums

import android.text.InputType
import androidx.annotation.StringRes
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.google.gson.JsonArray
import com.google.gson.JsonObject

private val AUDIO = setOf(DownloadType.audio)
private val VIDEO = setOf(DownloadType.video)
private val BOTH = setOf(DownloadType.audio, DownloadType.video)

/**
 * The tags editable in the metadata sheet. [key] is the ffmpeg tag name, written by yt-dlp through
 * a meta_<key> field. Title and artist are not here, they live in DownloadItem and lyrics have their own sheet.
 */
enum class MetadataTag(
    val key: String,
    @StringRes val label: Int,
    /** yt-dlp fields for autofill, the first non empty one wins */
    val sources: List<String>,
    val types: Set<DownloadType>,
    val alsoWrites: List<String> = emptyList(),
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    /** values that are sure to be short share a row in the metadata sheet */
    val short: Boolean = false
) {
    ALBUM("album", R.string.metadata_album, listOf("album", "playlist_title", "playlist"), AUDIO),
    ALBUM_ARTIST("album_artist", R.string.metadata_album_artist, listOf("album_artist", "album_artists", "playlist_uploader"), AUDIO),
    TRACK("track", R.string.metadata_track, listOf("track_number", "playlist_index"), AUDIO, short = true),
    DISC("disc", R.string.metadata_disc, listOf("disc_number"), AUDIO, short = true),
    YEAR("date", R.string.metadata_year, listOf("release_year", "release_date", "upload_date"), AUDIO, short = true),
    SHOW("show", R.string.metadata_show, listOf("series"), VIDEO),
    SEASON("season_number", R.string.metadata_season, listOf("season_number"), VIDEO, inputType = InputType.TYPE_CLASS_NUMBER, short = true),
    EPISODE_NUMBER("episode_sort", R.string.metadata_episode_number, listOf("episode_number"), VIDEO, inputType = InputType.TYPE_CLASS_NUMBER, short = true),
    EPISODE("episode_id", R.string.metadata_episode, listOf("episode", "episode_id"), VIDEO),
    DATE("date", R.string.metadata_date, listOf("release_date", "upload_date"), VIDEO, short = true),
    GENRE("genre", R.string.metadata_genre, listOf("genre", "genres", "categories"), BOTH, short = true),
    LANGUAGE("language", R.string.metadata_language, listOf("language"), BOTH),
    COMPOSER("composer", R.string.metadata_composer, listOf("composer", "composers"), AUDIO),
    PUBLISHER("publisher", R.string.metadata_publisher, emptyList(), AUDIO),
    NETWORK("network", R.string.metadata_network, listOf("channel"), VIDEO),
    KEYWORDS("keywords", R.string.metadata_keywords, listOf("tags"), VIDEO),
    COPYRIGHT("copyright", R.string.metadata_copyright, listOf("license"), BOTH),
    COMMENT(
        "comment", R.string.metadata_comment, listOf("webpage_url"), BOTH,
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    ),
    PURL("purl", R.string.metadata_purl, listOf("webpage_url"), BOTH),
    // yt-dlp fills both from the description, editing only one would leave the old text in the other
    DESCRIPTION(
        "description", R.string.metadata_description, listOf("description"), BOTH,
        alsoWrites = listOf("synopsis"),
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    ),
    // after DESCRIPTION so its value wins. No autofill, by default it just follows the description
    SYNOPSIS(
        "synopsis", R.string.metadata_synopsis, emptyList(), BOTH,
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    );

    companion object {
        fun forType(type: DownloadType) = entries.filter { type in it.types }

        /** The tags of the tab this item belongs to, null for types that don't embed them */
        fun tagsOf(item: DownloadItem): HashMap<String, String>? = when (item.type) {
            DownloadType.audio -> item.audioPreferences.metadataTags
            DownloadType.video -> item.videoPreferences.metadataTags
            else -> null
        }

        /** Tags every item agrees on. The rest stay out, so one item's value isn't shown as if it were everyone's */
        fun commonValues(all: List<Map<String, String>>): Map<String, String> =
            all.firstOrNull().orEmpty().filter { (key, value) -> all.all { it[key] == value } }

        /**
         * Writes only the fields that differ from what the sheet opened with, so untouched fields keep each
         * item's own value. Returns false when nothing changed, so callers can skip saving to the database.
         */
        fun applyEdits(all: List<MutableMap<String, String>>, initial: Map<String, String>, current: Map<String, String>): Boolean {
            val edits = current.filter { (key, text) -> text != initial[key].orEmpty() }
            all.forEach { tags ->
                edits.forEach { (key, text) -> if (text.isBlank()) tags.remove(key) else tags[key] = text }
            }
            return edits.isNotEmpty()
        }

        /** Prints only the fields autofill reads, the full info dict can be megabytes */
        fun printTemplate() = "%(.{${entries.flatMap { it.sources }.distinct().joinToString(",")}})j"

        /** Values to autofill, by tag key, from the output of [printTemplate] */
        fun pickValues(json: JsonObject, type: DownloadType): Map<String, String> =
            forType(type).mapNotNull { tag ->
                tag.sources.firstNotNullOfOrNull { source -> readValue(json, source) }
                    ?.let { tag.key to formatValue(tag, it) }
            }.toMap()

        /** --parse-metadata arguments in a stable order, since the command text is compared for duplicates */
        fun parseMetadataArgs(tags: Map<String, String>, type: DownloadType): List<String> =
            forType(type).flatMap { tag ->
                val value = tags[tag.key]?.trim()
                if (value.isNullOrEmpty()) emptyList()
                else (listOf(tag.key) + tag.alsoWrites).map { toParseMetadataArg(it, value) }
            }

        /** The postprocessor is only needed for these, see the ContainerTags plugin */
        fun needsContainerTags(tags: Map<String, String>) =
            listOf(PUBLISHER, COPYRIGHT, COMMENT, PURL).any { !tags[it.key].isNullOrBlank() }

        fun toParseMetadataArg(key: String, value: String) = setFieldArg("meta_$key", value)

        /**
         * --parse-metadata argument that sets a yt-dlp field to a literal value. The leading space stops
         * yt-dlp reading the value as a field name, the regex drops it again and (?s) keeps multi line values.
         */
        fun setFieldArg(field: String, value: String): String {
            // a trailing backslash would escape the FROM:TO separator and yt-dlp would reject the argument
            val from = " " + value.trimEnd('\\').replace("%", "%%").replace(":", "\\:")
            return from.replace("\\", "\\\\") + ":(?s)^ (?P<$field>.+)"
        }

        private val setFieldRegexForm = Regex("""\(\?s\)\^ \(\?P<(\w+)>\.\+\)"""")

        /** Older commands in history wrote these as VALUE:field, so compare both forms as the same command */
        fun normalizeForComparison(command: String) = command.replace(setFieldRegexForm, "$1\"")

        private fun readValue(json: JsonObject, source: String): String? {
            val element = json.get(source) ?: return null
            val value = when {
                element.isJsonPrimitive -> element.asString
                element is JsonArray -> element.filter { it.isJsonPrimitive }.joinToString(", ") { it.asString }
                else -> ""
            }
            return value.trim().ifEmpty { null }
        }

        private fun formatValue(tag: MetadataTag, value: String): String = when (tag) {
            YEAR -> value.take(4)
            DATE -> if (value.matches(Regex("\\d{8}"))) "${value.take(4)}-${value.substring(4, 6)}-${value.substring(6)}" else value
            else -> value
        }
    }
}
