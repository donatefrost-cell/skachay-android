package ru.skachay.app.choice

import java.io.IOException
import java.io.StringReader
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.skachay.app.network.UrlNetworkPolicy

internal data class VideoPreview(
    val title: String,
    val author: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val formats: List<AvailableFormat>,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

internal data class SubtitleTrack(val language: String, val manual: Boolean, val automatic: Boolean)

internal data class AvailableFormat(
    val id: String,
    val extension: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val height: Int?,
    val fps: Int?,
    val audioBitrate: Int?,
    val sizeBytes: Long?,
    val language: String? = null,
    val isOriginalAudio: Boolean = false,
    val isVolumeBalanced: Boolean = false,
    val videoBitrate: Int? = null,
    val directUrl: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
) {
    val hasVideo: Boolean get() = videoCodec != null
    val hasAudio: Boolean get() = audioCodec != null
}

internal object PreviewParser {
    private const val MAX_JSON_BYTES = 5L * 1024 * 1024
    const val MAX_JSON_CHARS = 5 * 1024 * 1024
    private val safeId = Regex("[A-Za-z0-9._-]{1,80}")
    private val safeLanguage = Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")
    private val safeHeaderName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")
    private val allowedHeaders = mapOf(
        "user-agent" to "User-Agent",
        "referer" to "Referer",
        "origin" to "Origin",
        "accept-language" to "Accept-Language",
    )

    fun parse(json: String): VideoPreview? {
        if (json.length > MAX_JSON_CHARS || !isWithinByteLimit(json)) return null
        return try {
            JsonReader(StringReader(json)).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_OBJECT) return null
                reader.beginObject()
                var title: String? = null
                var author: String? = null
                var duration: Int? = null
                var thumbnail: String? = null
                var formats: List<AvailableFormat>? = null
                var manualSubtitles = emptySet<String>()
                var automaticSubtitles = emptySet<String>()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "title" -> title = reader.stringValue()?.takeIf { it.isNotBlank() }?.take(300)
                        "uploader" -> author = reader.stringValue()?.takeIf { it.isNotBlank() }?.take(200)
                        "duration" -> duration = reader.positiveInt(Int.MAX_VALUE)
                        "thumbnail" -> thumbnail = reader.stringValue()?.takeIf(::safeThumbnail)
                        "formats" -> formats = readFormats(reader) ?: return null
                        "subtitles" -> manualSubtitles = readSubtitleLanguages(reader)
                        "automatic_captions" -> automaticSubtitles = readSubtitleLanguages(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) return null
                val subtitles = (manualSubtitles + automaticSubtitles).sorted().map { language ->
                    SubtitleTrack(language, language in manualSubtitles, language in automaticSubtitles)
                }
                VideoPreview(title ?: "Видео", author, duration, thumbnail, formats ?: return null, subtitles)
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun readFormats(reader: JsonReader): List<AvailableFormat>? {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return null }
        reader.beginArray()
        val formats = mutableListOf<AvailableFormat>()
        var count = 0
        while (reader.hasNext()) {
            if (++count > 2_000) return null
            if (reader.peek() == JsonToken.BEGIN_OBJECT) readFormat(reader)?.let(formats::add) else reader.skipValue()
        }
        reader.endArray()
        return formats.distinctBy { it.id }
    }

    private fun readSubtitleLanguages(reader: JsonReader): Set<String> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return emptySet() }
        val languages = linkedSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val language = reader.nextName()
            if (safeLanguage.matches(language) && languages.size < 100) languages += language
            reader.skipValue()
        }
        reader.endObject()
        return languages
    }

    private fun readFormat(reader: JsonReader): AvailableFormat? {
        var id: String? = null
        var ext: String? = null
        var video: String? = null
        var audio: String? = null
        var height: Int? = null
        var fps: Int? = null
        var bitrate: Int? = null
        var videoBitrate: Int? = null
        var totalBitrate: Int? = null
        var size: Long? = null
        var approximateSize: Long? = null
        var language: String? = null
        var note: String? = null
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var drm = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "format_id" -> id = reader.stringValue()
                "ext" -> ext = reader.stringValue()?.lowercase()
                "vcodec" -> video = reader.stringValue().codec()
                "acodec" -> audio = reader.stringValue().codec()
                "height" -> height = reader.positiveInt(8640)
                "fps" -> fps = reader.positiveInt(240)
                "abr" -> bitrate = reader.positiveInt(Int.MAX_VALUE)
                "vbr" -> videoBitrate = reader.positiveInt(Int.MAX_VALUE)
                "tbr" -> totalBitrate = reader.positiveInt(Int.MAX_VALUE)
                "filesize" -> size = reader.positiveLong()
                "filesize_approx" -> approximateSize = reader.positiveLong()
                "language" -> language = reader.stringValue()?.takeIf(safeLanguage::matches)
                "format_note" -> note = reader.stringValue()?.take(300)
                "url" -> url = reader.stringValue()?.takeIf(::safeMediaUrl)
                "http_headers" -> headers = readHeaders(reader)
                "has_drm" -> drm = if (reader.peek() == JsonToken.BOOLEAN) reader.nextBoolean() else { reader.skipValue(); false }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val safeFormatId = id?.takeIf(safeId::matches) ?: return null
        val extension = ext?.takeIf { it in setOf("mp4", "webm", "m4a") } ?: return null
        if (drm || (video == null && audio == null)) return null
        if (video == null && extension == "webm" && audio?.lowercase() !in setOf("opus", "vorbis")) return null
        return AvailableFormat(safeFormatId, extension, video, audio, height, fps, bitrate, size ?: approximateSize,
            language, note?.contains("original", ignoreCase = true) == true,
            note?.let { Regex("\\bDRC\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true,
            videoBitrate ?: totalBitrate, url, headers.takeIf { url != null }.orEmpty())
    }

    private fun readHeaders(reader: JsonReader): Map<String, String> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return emptyMap() }
        val headers = linkedMapOf<String, String>()
        var totalBytes = 0
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            val value = reader.stringValue()
            val allowedName = name.takeIf(safeHeaderName::matches)?.lowercase()?.let(allowedHeaders::get)
            if (allowedName != null && value != null && value.length <= 4096 &&
                value.none { it == '\r' || it == '\n' } && headers.size < 40) {
                totalBytes += name.length + value.length
                if (totalBytes <= 16_384) headers[allowedName] = value
            }
        }
        reader.endObject()
        return headers
    }

    private fun JsonReader.stringValue(): String? = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    private fun JsonReader.positiveInt(maximum: Int): Int? = stringValue()?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 1 && it <= maximum }?.toInt()

    private fun JsonReader.positiveLong(): Long? = stringValue()?.toLongOrNull()?.takeIf { it > 0 }

    private fun String?.codec(): String? = this?.takeIf { it.isNotBlank() && it != "none" && it != "null" }?.take(80)

    private fun safeThumbnail(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return UrlNetworkPolicy.isSafeHttpsUrl(url) &&
            setOf("ytimg.com", "ggpht.com").any { UrlNetworkPolicy.hostMatchesSuffix(url.host, it) }
    }

    private fun safeMediaUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return UrlNetworkPolicy.isSafeHttpsUrl(url) && UrlNetworkPolicy.hostMatchesSuffix(url.host, "googlevideo.com")
    }

    private fun isWithinByteLimit(value: String): Boolean {
        var bytes = 0L
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            bytes += when {
                codePoint <= 0x7f -> 1
                codePoint <= 0x7ff -> 2
                codePoint <= 0xffff -> 3
                else -> 4
            }
            if (bytes > MAX_JSON_BYTES) return false
            index += Character.charCount(codePoint)
        }
        return true
    }
}
