package com.deniscerri.ytdl.util.extractors.music

import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.database.models.MusicSource
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * A catalogue the song tags can be resolved from.
 *
 * The contract is split in two on purpose. [search] stays cheap, one request that returns the
 * candidates the picker needs, so a lookup never costs more than the user can see. The tags a
 * catalogue only exposes on its detail endpoints are left to [details], which runs for the one
 * candidate the card actually shows instead of the whole result list.
 *
 * Both are suspending because they hit the network, and both may block: they are always called
 * from [MusicMetadataUtil] on a background dispatcher.
 */
interface MusicProvider {

    /** Matches [MusicSource.provider], so a result finds its own catalogue again later. */
    val id: String

    suspend fun search(query: String, limit: Int): List<MusicMetadata>

    /**
     * Completes [metadata] with the fields [search] could not carry. Catalogues that already
     * return everything leave this alone.
     */
    suspend fun details(metadata: MusicMetadata): MusicMetadata = metadata
}

// ── JSON reading shared by the providers ───────────────────────────────────────
//
// The APIs are loosely typed and occasionally omit or null out fields, so every read degrades
// to an empty value rather than throwing: a missing tag is simply one that stays blank.

internal fun JsonObject.str(key: String): String =
    runCatching { get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty() }.getOrDefault("")

internal fun JsonObject.obj(key: String): JsonObject? =
    runCatching { getAsJsonObject(key) }.getOrNull()

internal fun JsonObject.arr(key: String): JsonArray =
    runCatching { getAsJsonArray(key) }.getOrNull() ?: JsonArray()

/** The first value a catalogue actually filled in, used to fall back between equivalent fields. */
internal fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
