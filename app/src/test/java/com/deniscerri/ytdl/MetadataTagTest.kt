package com.deniscerri.ytdl

import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.enums.MetadataTag
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataTagTest {
    private fun pick(json: String, type: DownloadType) =
        MetadataTag.pickValues(JsonParser.parseString(json).asJsonObject, type)

    @Test
    fun escapesPercentColonAndBackslash() {
        assertEquals(
            """ 100%% \\: a\\b:(?s)^ (?P<meta_album>.+)""",
            MetadataTag.toParseMetadataArg("album", """100% : a\b""")
        )
    }

    @Test
    fun trailingBackslashCannotEscapeSeparator() {
        assertEquals(" a:(?s)^ (?P<meta_album>.+)", MetadataTag.toParseMetadataArg("album", "a\\\\"))
    }

    @Test
    fun keepsNewlines() {
        assertTrue(MetadataTag.toParseMetadataArg("description", "a\nb").startsWith(" a\nb:"))
    }

    @Test
    fun descriptionAlsoWritesSynopsisAndBlankIsSkipped() {
        val args = MetadataTag.parseMetadataArgs(mapOf("description" to "d", "album" to "  "), DownloadType.audio)
        assertEquals(2, args.size)
        assertTrue(args[0].contains("meta_description"))
        assertTrue(args[1].contains("meta_synopsis"))
    }

    @Test
    fun synopsisOverridesTheDescriptionCopy() {
        val args = MetadataTag.parseMetadataArgs(mapOf("description" to "d", "synopsis" to "s"), DownloadType.video)
        assertTrue(args.last().startsWith(" s:") && args.last().contains("meta_synopsis"))
    }

    @Test
    fun setFieldDropsTheLeadingSpaceInItsRegex() {
        assertEquals(" Rose:(?s)^ (?P<uploader>.+)", MetadataTag.setFieldArg("uploader", "Rose"))
    }

    @Test
    fun oldAndNewCommandFormsCompareEqual() {
        val old = """--parse-metadata " My Title:title" --parse-metadata "%(title)s:%(meta_title)s" -o "a.%(ext)s""""
        val new = """--parse-metadata " My Title:(?s)^ (?P<title>.+)" --parse-metadata "%(title)s:%(meta_title)s" -o "a.%(ext)s""""
        assertEquals(old, MetadataTag.normalizeForComparison(new))
        assertEquals(old, MetadataTag.normalizeForComparison(old))
    }

    @Test
    fun containerTagsOnlyWhenNeeded() {
        assertFalse(MetadataTag.needsContainerTags(mapOf("album" to "a", "publisher" to " ")))
        assertTrue(MetadataTag.needsContainerTags(mapOf("copyright" to "c")))
    }

    @Test
    fun onlySharedValuesAreCommon() {
        val common = MetadataTag.commonValues(listOf(
            mapOf("album" to "A", "track" to "1"),
            mapOf("album" to "A", "track" to "2")
        ))
        assertEquals(mapOf("album" to "A"), common)
    }

    @Test
    fun untouchedFieldsKeepEachItemsValue() {
        val first = mutableMapOf("album" to "A", "track" to "1", "genre" to "Rock")
        val second = mutableMapOf("album" to "A", "track" to "2")
        val initial = MetadataTag.commonValues(listOf(first, second))
        // album cleared, disc added, track and genre left empty because the items differ
        val changed = MetadataTag.applyEdits(listOf(first, second), initial, mapOf("album" to "", "track" to "", "genre" to "", "disc" to "9"))
        assertTrue(changed)
        assertEquals(mapOf("track" to "1", "genre" to "Rock", "disc" to "9"), first)
        assertEquals(mapOf("track" to "2", "disc" to "9"), second)
    }

    @Test
    fun noEditsMeansNothingToSave() {
        val tags = mutableMapOf("album" to "A")
        assertFalse(MetadataTag.applyEdits(listOf(tags), tags.toMap(), mapOf("album" to "A", "disc" to "")))
        assertEquals(mapOf("album" to "A"), tags)
    }

    @Test
    fun ignoresTagsOfOtherType() {
        assertTrue(MetadataTag.parseMetadataArgs(mapOf("show" to "s"), DownloadType.audio).isEmpty())
    }

    @Test
    fun firstNonEmptySourceWinsAndArraysAreJoined() {
        val values = pick("""{"album":"","playlist_title":"P","genres":["a","b"],"track_number":3}""", DownloadType.audio)
        assertEquals("P", values["album"])
        assertEquals("a, b", values["genre"])
        assertEquals("3", values["track"])
        assertFalse(values.containsKey("disc"))
    }

    @Test
    fun datesFollowTheTab() {
        val json = """{"upload_date":"20240229"}"""
        assertEquals("2024", pick(json, DownloadType.audio)["date"])
        assertEquals("2024-02-29", pick(json, DownloadType.video)["date"])
    }
}
