package ru.skachay.app.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaStoreWriter(private val context: Context) {
    fun save(source: File, mime: String, suggestedName: String? = null, itemNumber: Int? = null): Uri {
        val (directory, extension) = when (mime) {
            "image/jpeg" -> Environment.DIRECTORY_PICTURES to "jpg"
            "image/png" -> Environment.DIRECTORY_PICTURES to "png"
            "image/gif" -> Environment.DIRECTORY_PICTURES to "gif"
            "image/webp" -> Environment.DIRECTORY_PICTURES to "webp"
            "video/mp4" -> Environment.DIRECTORY_MOVIES to "mp4"
            "video/webm" -> Environment.DIRECTORY_MOVIES to "webm"
            "audio/mp4" -> Environment.DIRECTORY_MUSIC to "m4a"
            "audio/mpeg" -> Environment.DIRECTORY_MUSIC to "mp3"
            "audio/ogg" -> Environment.DIRECTORY_MUSIC to "ogg"
            else -> throw IOException("Unsupported media type")
        }
        val name = SavedFileName.displayName(suggestedName, extension, itemNumber)
            ?: "Skachay_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.$extension"
        return if (Build.VERSION.SDK_INT >= 29) saveScoped(source, mime, directory, name)
        else saveLegacy(source, mime, directory, name)
    }

    private fun saveScoped(source: File, mime: String, directory: String, name: String): Uri {
        val resolver = context.contentResolver
        val collection = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Скачай")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create gallery entry")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("Could not open gallery entry")
            output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) != 1) throw IOException("Could not publish gallery entry")
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(source: File, mime: String, directory: String, name: String): Uri {
        val folder = File(Environment.getExternalStoragePublicDirectory(directory), "Скачай")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create gallery folder")
        val target = SavedFileName.reserve(folder, name)
        try {
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            val latch = CountDownLatch(1)
            var result: Uri? = null
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime)) { _, uri ->
                result = uri
                latch.countDown()
            }
            if (!latch.await(15, TimeUnit.SECONDS)) throw IOException("Gallery scan timed out")
            return result ?: throw IOException("Could not add file to gallery")
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }
}
