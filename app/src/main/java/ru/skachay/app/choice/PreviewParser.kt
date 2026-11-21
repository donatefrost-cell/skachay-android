package ru.skachay.app.choice

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.skachay.app.network.UrlNetworkPolicy
import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.io.Writer

internal data class VideoPreview(
    val title: String,
    val author: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val formats: List<AvailableFormat>,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

internal data class SubtitleTrack(
    val language: String,
    val manual: Boolean,
    val automatic: Boolean,
)

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
    private const val MAX_EXTRACTION_BYTES = 32L * 1024 * 1024
    const val MAX_JSON_CHARS = 5 * 1024 * 1024
    private val safeId = Regex("[A-Za-z0-9._-]{1,80}")
    private val safeLanguage = Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")
    private val safeHeaderName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")
    private val allowedHeaders =
        mapOf(
            "user-agent" to "User-Agent",
            "referer" to "Referer",
            "origin" to "Origin",
            "accept-language" to "Accept-Language",
        )

    fun parse(json: String): VideoPreview? {
        if (json.length > MAX_JSON_CHARS || !isWithinByteLimit(json)) return null
        return parse(StringReader(json))
    }

    fun parseExtraction(file: File): VideoPreview {
        val bytes = file.length()
        if (bytes == 0L) throw IOException("yt-dlp returned empty media metadata")
        if (bytes > MAX_EXTRACTION_BYTES) throw IOException("yt-dlp media metadata is too large ($bytes bytes)")
        val preview =
            file.bufferedReader(Charsets.UTF_8).use { parse(it) }
                ?: throw IOException("yt-dlp returned malformed media metadata ($bytes bytes)")
        if (preview.formats.isEmpty()) throw IOException("No formats found in yt-dlp metadata")
        return preview
    }

    private fun parse(input: Reader): VideoPreview? {
        return try {
            JsonReader(input).use { reader ->
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
                val subtitles =
                    (manualSubtitles + automaticSubtitles).sorted().map { language ->
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
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return null
        }
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
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return emptySet()
        }
        val languages = linkedSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val language = reader.nextName()
            if (languages.size < 250 && language.length <= 48 && safeLanguage.matches(language)) languages += language
            reader.skipValue()
        }
        reader.endObject()
        return languages
    }

    fun writeCache(
        preview: VideoPreview,
        destination: Writer,
    ) {
        val writer = JsonWriter(destination)
        writer.setSerializeNulls(false)
        writer.beginObject()
        writer.name("title").value(preview.title)
        writer.name("uploader").value(preview.author)
        writer.name("duration").value(preview.durationSeconds)
        writer.name("thumbnail").value(preview.thumbnailUrl)
        writer.name("formats").beginArray()
        preview.formats.forEach { format ->
            writer.beginObject()
            writer.name("format_id").value(format.id)
            writer.name("ext").value(format.extension)
            writer.name("vcodec").value(format.videoCodec)
            writer.name("acodec").value(format.audioCodec)
            writer.name("height").value(format.height)
            writer.name("fps").value(format.fps)
            writer.name("abr").value(format.audioBitrate)
            writer.name("vbr").value(format.videoBitrate)
            writer.name("filesize").value(format.sizeBytes)
            writer.name("language").value(format.language)
            if (format.isOriginalAudio || format.isVolumeBalanced) {
                val note = listOfNotNull("original".takeIf { format.isOriginalAudio }, "DRC".takeIf { format.isVolumeBalanced })
                writer.name("format_note").value(note.joinToString(" "))
            }
            format.directUrl?.let { url ->
                writer.name("url").value(url)
                writer.name("http_headers").beginObject()
                format.httpHeaders.forEach { (name, value) -> writer.name(name).value(value) }
                writer.endObject()
            }
            writer.endObject()
        }
        writer.endArray()
        writer.name("subtitles").beginObject()
        preview.subtitles.filter { it.manual }.forEach { writer.name(it.language).beginArray().endArray() }
        writer.endObject()
        writer.name("automatic_captions").beginObject()
        preview.subtitles.filter { it.automatic }.forEach { writer.name(it.language).beginArray().endArray() }
        writer.endObject()
        writer.endObject()
        writer.flush()
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
        var protocol: String? = null
        var headers: Map<String, String> = emptyMap()
        var drm = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "format_id" -> {
                    id = reader.stringValue()
                }

                "ext" -> {
                    ext = reader.stringValue()?.lowercase()
                }

                "vcodec" -> {
                    video = reader.stringValue().codec()
                }

                "acodec" -> {
                    audio = reader.stringValue().codec()
                }

                "height" -> {
                    height = reader.positiveInt(8640)
                }

                "fps" -> {
                    fps = reader.positiveInt(240)
                }

                "abr" -> {
                    bitrate = reader.positiveInt(Int.MAX_VALUE)
                }

                "vbr" -> {
                    videoBitrate = reader.positiveInt(Int.MAX_VALUE)
                }

                "tbr" -> {
                    totalBitrate = reader.positiveInt(Int.MAX_VALUE)
                }

                "filesize" -> {
                    size = reader.positiveLong()
                }

                "filesize_approx" -> {
                    approximateSize = reader.positiveLong()
                }

                "language" -> {
                    language = reader.stringValue()?.takeIf(safeLanguage::matches)
                }

                "format_note" -> {
                    note = reader.stringValue()?.take(300)
                }

                "url" -> {
                    url = reader.stringValue()?.takeIf(::safeMediaUrl)
                }

                "protocol" -> {
                    protocol = reader.stringValue()
                }

                "http_headers" -> {
                    headers = readHeaders(reader)
                }

                "has_drm" -> {
                    drm =
                        if (reader.peek() == JsonToken.BOOLEAN) {
                            reader.nextBoolean()
                        } else {
                            reader.skipValue()
                            false
                        }
                }

                else -> {
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
        val safeFormatId = id?.takeIf(safeId::matches) ?: return null
        val extension = ext?.takeIf { it in setOf("mp4", "webm", "m4a") } ?: return null
        if (drm || (video == null && audio == null)) return null
        if (video == null && extension == "webm" && audio?.lowercase() !in setOf("opus", "vorbis")) return null
        val directUrl = url?.takeIf { protocol == null || protocol == "https" || protocol == "http" }
        return AvailableFormat(
            safeFormatId,
            extension,
            video,
            audio,
            height,
            fps,
            bitrate,
            size ?: approximateSize,
            language,
            note?.contains("original", ignoreCase = true) == true,
            note?.let { Regex("\\bDRC\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true,
            videoBitrate ?: totalBitrate,
            directUrl,
            headers.takeIf { directUrl != null }.orEmpty(),
        )
    }

    private fun readHeaders(reader: JsonReader): Map<String, String> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return emptyMap()
        }
        val headers = linkedMapOf<String, String>()
        var totalBytes = 0
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            val value = reader.stringValue()
            val allowedName = name.takeIf(safeHeaderName::matches)?.lowercase()?.let(allowedHeaders::get)
            if (allowedName != null && value != null && value.length <= 4096 &&
                value.none { it == '\r' || it == '\n' } && headers.size < 40
            ) {
                totalBytes += name.length + value.length
                if (totalBytes <= 16_384) headers[allowedName] = value
            }
        }
        reader.endObject()
        return headers
    }

    private fun JsonReader.stringValue(): String? =
        when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> {
                nextString()
            }

            JsonToken.NULL -> {
                nextNull()
                null
            }

            else -> {
                skipValue()
                null
            }
        }

    private fun JsonReader.positiveInt(maximum: Int): Int? =
        stringValue()
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 1 && it <= maximum }
            ?.toInt()

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
            bytes +=
                when {
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
