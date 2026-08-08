package com.deniscerri.ytdl.util

import android.util.Log
import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.util.extractors.music.MusicHttp
import com.deniscerri.ytdl.util.extractors.music.MusicMetadataUtil
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Writes the resolved [MusicMetadata] into finished audio files and renames them to
 * "Artist - Title.ext". Runs on the downloaded file before it is moved to its final
 * destination, so tagging never touches SAF trees.
 */
object MusicTagUtil {
    private const val TAG = "MusicTagUtil"

    /** Containers jaudiotagger can write tags into. Others are only renamed. */
    private val TAGGABLE = setOf("mp3", "m4a", "m4b", "mp4", "flac", "ogg", "wav", "aif", "aiff", "wma")

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** Tags + renames every audio file inside [dir] (cache download, pre-move). */
    fun applyToDirectory(dir: File, metadata: MusicMetadata) {
        val files = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in TAGGABLE }.toList()
        apply(files, metadata)
    }

    /** Tags + renames files already written to their destination, returning the updated paths. */
    fun applyToPaths(paths: List<String>, metadata: MusicMetadata): List<String> {
        val files = paths.map { File(it) }
        val renamed = apply(files, metadata).associate { (from, to) -> from to to }
        return paths.map { renamed[it] ?: it }
    }

    /** @return pairs of (old path, new path) for the files that were renamed. */
    private fun apply(files: List<File>, metadata: MusicMetadata): List<Pair<String, String>> {
        if (files.isEmpty() || !metadata.isUsable) return emptyList()

        val cover = metadata.coverUrl.takeIf { it.isNotBlank() }?.let { downloadCover(it) }
        val renames = mutableListOf<Pair<String, String>>()

        files.forEach { file ->
            if (file.extension.lowercase() in TAGGABLE) {
                runCatching { writeTags(file, metadata, cover) }
                    .onFailure { Log.w(TAG, "Tagging failed for ${file.name}", it) }
            }
            renameToCleanName(file, metadata)?.let { renames.add(file.absolutePath to it) }
        }

        cover?.delete()
        return renames
    }

    private fun writeTags(file: File, metadata: MusicMetadata, cover: File?) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateDefault
        tag.setField(FieldKey.TITLE, metadata.title)
        tag.setField(FieldKey.ARTIST, metadata.artist)
        tag.write(FieldKey.ALBUM_ARTIST, metadata.albumArtist.ifBlank { metadata.artist })
        tag.write(FieldKey.ALBUM, metadata.album)
        tag.write(FieldKey.YEAR, metadata.year)
        tag.write(FieldKey.GENRE, metadata.genre)
        tag.write(FieldKey.RECORD_LABEL, metadata.label)
        tag.write(FieldKey.TRACK, metadata.trackNumber)
        tag.write(FieldKey.TRACK_TOTAL, metadata.trackTotal)
        tag.write(FieldKey.DISC_NO, metadata.discNumber)
        tag.write(FieldKey.ISRC, metadata.isrc)
        cover?.let {
            tag.deleteArtworkField()
            tag.setField(ArtworkFactory.createArtworkFromFile(it))
        }
        audioFile.tag = tag
        AudioFileIO.write(audioFile)
    }

    /**
     * Writes an optional tag, skipping the blanks so an unresolved field never wipes what the
     * downloader already wrote, and tolerating the keys a given container cannot store.
     */
    private fun Tag.write(key: FieldKey, value: String) {
        if (value.isBlank()) return
        runCatching { setField(key, value) }.onFailure { Log.w(TAG, "Unsupported tag $key", it) }
    }

    /** @return the new absolute path, or null when the file was left untouched. */
    private fun renameToCleanName(file: File, metadata: MusicMetadata): String? {
        val target = File(file.parentFile, MusicMetadataUtil.buildFileName(metadata, file.extension))
        if (target.absolutePath == file.absolutePath || target.exists()) return null
        return if (file.renameTo(target)) target.absolutePath else null
    }

    private fun downloadCover(url: String): File? = runCatching {
        val bytes = MusicHttp.bytes(url) ?: return null
        File.createTempFile("cover_", ".jpg").apply { writeBytes(bytes) }
    }.getOrElse { Log.w(TAG, "Cover download failed", it); null }
}
