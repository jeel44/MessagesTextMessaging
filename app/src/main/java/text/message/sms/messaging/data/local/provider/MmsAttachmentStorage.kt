package text.message.sms.messaging.data.local.provider

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.provider.mms.MmsPart
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns where MMS part bytes live on disk. Everything downloaded or attached stays in
 * app-private storage (no runtime permission needed, and it survives the app losing the
 * default-SMS role) until the user asks to export it, at which point [exportToMediaStore]
 * copies it into shared storage the scoped-storage way.
 */
@Singleton
class MmsAttachmentStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Writes one decoded part into this message's private directory and returns the file. */
    suspend fun savePart(messageId: Long, index: Int, part: MmsPart): File = withContext(Dispatchers.IO) {
        val dir = messageDir(messageId).apply { mkdirs() }
        val fileName = safeFileName(index, part)
        val file = File(dir, fileName)
        file.writeBytes(part.data)
        file
    }

    /** A `content://` URI the app itself (and anyone it explicitly grants access to) can read,
     * for a file previously written by [savePart]. */
    fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun deleteMessageDir(messageId: Long) = withContext(Dispatchers.IO) {
        messageDir(messageId).deleteRecursively()
    }

    /** Copies the file [savePart] wrote into the shared MediaStore collection matching
     * [mimeType]. See the [exportToMediaStore] overload below for the scoped-storage note. */
    suspend fun exportToMediaStore(source: File, mimeType: String, displayName: String): Uri? =
        exportToMediaStore({ source.inputStream() }, mimeType, displayName)

    /** Same as [exportToMediaStore], reading from an already-published `content://` Uri (such
     * as an [Attachment][text.message.sms.messaging.domain.model.Attachment]'s own
     * `contentUri`) instead of a raw [File]. */
    suspend fun exportToMediaStore(source: Uri, mimeType: String, displayName: String): Uri? =
        exportToMediaStore({ context.contentResolver.openInputStream(source) }, mimeType, displayName)

    /**
     * Copies whatever [openSource] streams into the shared MediaStore collection matching
     * [mimeType], the scoped storage way: no filesystem path, no `WRITE_EXTERNAL_STORAGE`.
     *
     * Scoped storage -- and with it, permission-free [MediaStore] writes from any app -- only
     * exists from Android 10 (API 29). This app's minSdk is 26, and the brief for this pass
     * rules out falling back to a legacy `WRITE_EXTERNAL_STORAGE` file path, so on API 26-28
     * export deliberately does nothing (the source stays readable from inside the app, just not
     * pushed into the Gallery/Downloads). That gap is two platform versions wide and shrinking
     * every day those devices age out of service.
     */
    suspend fun exportToMediaStore(
        openSource: () -> java.io.InputStream?,
        mimeType: String,
        displayName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null

        val (collection, relativeDir) = collectionFor(mimeType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val destination = resolver.insert(collection, values) ?: return@withContext null

        val copied = resolver.openOutputStream(destination)?.use { output ->
            openSource()?.use { input -> input.copyTo(output) } != null
        } ?: false

        if (!copied) {
            resolver.delete(destination, null, null)
            return@withContext null
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(destination, values, null, null)

        destination
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collectionFor(mimeType: String): Pair<Uri, String> = when {
        mimeType.startsWith("image/") ->
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES

        mimeType.startsWith("video/") ->
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES

        mimeType.startsWith("audio/") ->
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC

        else ->
            MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
    }

    private fun messageDir(messageId: Long): File = File(context.filesDir, "mms/$messageId")

    private fun safeFileName(index: Int, part: MmsPart): String {
        val requested = part.name?.substringAfterLast('/')?.trim().orEmpty()
        if (requested.isNotEmpty() && NAME_PATTERN.matches(requested)) return requested
        val extension = EXTENSION_BY_MIME[part.contentType] ?: "bin"
        return "part_$index.$extension"
    }

    private companion object {
        val NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")

        val EXTENSION_BY_MIME: Map<String, String> = mapOf(
            "text/plain" to "txt",
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/gif" to "gif",
            "image/vnd.wap.wbmp" to "wbmp",
            "audio/amr" to "amr",
            "audio/mpeg" to "mp3",
            "video/3gpp" to "3gp",
            "video/mp4" to "mp4",
            "application/smil" to "smil",
        )
    }
}
