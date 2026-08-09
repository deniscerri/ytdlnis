package com.deniscerri.ytdl.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import androidx.core.net.toUri
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.extractors.music.MusicHttp
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Handles the album cover of a music download, wherever it comes from: a catalogue url, a
 * picture the user picked on the device, or nothing at all.
 *
 * A picked image is copied into app storage instead of being referenced. The download runs
 * long after the picker handed out its permission, and the document it points at may be gone
 * or unreadable by then, so the bytes are taken while they are still there to take.
 */
object MusicCoverUtil {
    private const val TAG = "MusicCoverUtil"
    private const val DIR = "music_covers"

    /** Picked covers outlive their download by a week, long enough to survive a retry. */
    private val MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)

    /** The longest edge a notification large icon is drawn at, on the densest screens. */
    private const val ICON_SIZE = 256

    /** @return the stored path to keep as the cover, or null when the image could not be read. */
    fun store(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, DIR).apply { mkdirs(); prune() }
        val file = File(dir, "cover_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { source ->
            file.outputStream().use { source.copyTo(it) }
        }
        file.absolutePath
    }.getOrElse { Log.w(TAG, "Could not store the picked cover", it); null }

    /** A stored cover is an absolute path, a catalogue one is always a url. */
    fun isLocal(cover: String): Boolean = cover.startsWith("/")

    /** Renders a catalogue cover into the card thumbnail, where the http cache earns its keep. */
    fun load(cover: String, target: ImageView) {
        if (cover.isBlank()) {
            target.setImageResource(R.drawable.ic_music)
            return
        }
        val request = if (isLocal(cover)) Picasso.get().load(File(cover)) else Picasso.get().load(cover)
        request.placeholder(R.drawable.ic_music).into(target)
    }

    /**
     * Decodes a cover at full size for the preview, whatever it is: a stored file, a catalogue
     * url or a document the user just picked.
     *
     * Read straight from the source rather than through the image cache. A preview is looked at
     * once and decided on, and a picked document is only readable while its grant lasts, so
     * there is nothing here worth caching and nothing worth risking on a stale handle.
     *
     * @param maxSize the longest edge to decode down to, covers are routinely larger than a screen.
     * @return the bitmap, or null when the source is empty or unreadable.
     */
    suspend fun preview(context: Context, cover: String, maxSize: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (cover.isBlank()) return@withContext null
            runCatching {
                val bytes = open(context, cover) ?: return@withContext null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrElse { Log.w(TAG, "Could not read the cover", it); null }
        }

    /**
     * The cover as the large icon of a notification, or null when there is none to show.
     *
     * The same decode as the preview, capped at the size a notification icon is ever drawn at:
     * the system scales anything larger down anyway, and pays for it in memory while it does.
     */
    suspend fun notificationIcon(context: Context, cover: String): Bitmap? =
        preview(context, cover, ICON_SIZE)

    /** The three shapes a cover comes in, each read once into memory so it can be measured then decoded. */
    private fun open(context: Context, cover: String): ByteArray? = when {
        isLocal(cover) -> File(cover).takeIf { it.exists() }?.readBytes()
        cover.startsWith("http") -> MusicHttp.bytes(cover)
        else -> context.contentResolver.openInputStream(cover.toUri())?.use { it.readBytes() }
    }

    /**
     * Halving is the only reduction [BitmapFactory] does, so stop one step before the image
     * would drop under [maxSize]: a preview that is a little large costs memory once, one that
     * is too small looks soft for as long as it is on screen.
     */
    private fun sampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSize) sample *= 2
        return sample
    }

    private fun File.prune() {
        val expiry = System.currentTimeMillis() - MAX_AGE_MS
        listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.delete() }
    }
}
