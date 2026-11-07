package ru.skachay.app.choice

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.InstagramResolver
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.download.TikTokResolver

internal object SocialPreviewStore {
    private val lock = Any()
    private val prefetcher = Executors.newSingleThreadExecutor()
    private const val MAX_BYTES = 1_000_000L

    fun getOrFetch(context: Context, url: String): SocialPreview = synchronized(lock) {
        load(context, url)?.takeIf { PreviewLifetime.isFresh(System.currentTimeMillis() - it.createdAt) }?.let { return it }
        val folder = directory(context)
        folder.listFiles()?.filter { PreviewLifetime.isExpired(System.currentTimeMillis() - it.lastModified()) }?.forEach(File::delete)
        val items = when {
            InstagramLink.supports(url) -> InstagramResolver().resolve(url)
            TikTokLink.supports(url) -> TikTokResolver().resolve(url)
            else -> emptyList()
        }
        if (items.isEmpty()) throw IOException("No media preview")
        val source = digest(url)
        val now = System.currentTimeMillis()
        val json = SocialPreviewCodec.encode(source, UUID.randomUUID().toString(), now, items)
        val preview = SocialPreviewCodec.decode(json, source, now) ?: throw IOException("Invalid media preview")
        val temp = File.createTempFile("social-", ".tmp", folder)
        try {
            temp.writeText(json, Charsets.UTF_8)
            if (temp.length() > MAX_BYTES) throw IOException("Media preview too large")
            Files.move(temp.toPath(), File(folder, "$source.json").toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
        preview
    }

    fun prefetch(context: Context, url: String) {
        val appContext = context.applicationContext
        prefetcher.execute {
            try { getOrFetch(appContext, url) } catch (_: Exception) { }
        }
    }

    fun load(context: Context, url: String, id: String? = null): SocialPreview? {
        val source = digest(url)
        val file = File(directory(context), "$source.json")
        if (!file.isFile || file.length() > MAX_BYTES) return null
        return try {
            SocialPreviewCodec.decode(file.readText(Charsets.UTF_8), source, System.currentTimeMillis())
                ?.takeIf { id == null || it.id == id }
        } catch (_: IOException) {
            null
        }
    }

    private fun directory(context: Context): File = File(context.cacheDir, "social-previews").apply {
        if (!isDirectory && !mkdirs()) throw IOException("Could not create preview folder")
    }

    private fun digest(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
