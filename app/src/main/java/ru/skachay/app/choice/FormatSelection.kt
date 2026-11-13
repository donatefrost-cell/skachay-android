package ru.skachay.app.choice

import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Complete editable state for the downloader's video/audio format selection. */
internal data class FormatSelection(
    val resolution: Int? = null,
    val qualityPreset: Quality = Quality.AUTO,
    val bitrateStop: VideoBitrateLimit = VideoBitrateLimit.ANY,
    val customBitrateKbps: Int? = null,
    val codec: String? = null,
    val fps: Int? = null,
    val container: String? = null,
    val audio: Boolean = false,
    val pinnedFormatId: String? = null,
    val videoOnly: Boolean = false,
    val audioExport: AudioExport = AudioExport.SOURCE,
    val audioBitrate: AudioBitrate = AudioBitrate.K128,
    val subtitleLanguage: String? = null,
    val subtitleAutomatic: Boolean = false,
    val embedSubtitles: Boolean = false,
    val embedThumbnail: Boolean = false,
) {
    fun withFilters(
        resolution: Int?,
        bitrateStop: VideoBitrateLimit,
        codec: String?,
        fps: Int?,
        container: String?,
        audio: Boolean,
        customBitrateKbps: Int? = null,
    ): FormatSelection =
        copy(
            resolution = resolution,
            bitrateStop = bitrateStop,
            codec = codec,
            fps = fps,
            container = container,
            audio = audio,
            pinnedFormatId = null,
            customBitrateKbps = customBitrateKbps,
        )

    fun pin(format: AvailableFormat): FormatSelection {
        val height = format.height
        val preset =
            when {
                height == null -> qualityPreset
                height > 2160 -> Quality.MAX
                height >= 2160 -> Quality.P2160
                height >= 1440 -> Quality.P1440
                height >= 1080 -> Quality.P1080
                height >= 720 -> Quality.P720
                height >= 480 -> Quality.P480
                height >= 360 -> Quality.P360
                height >= 240 -> Quality.P240
                else -> Quality.P144
            }
        return copy(
            resolution = height,
            qualityPreset = preset,
            bitrateStop = VideoBitrateLimit.ANY,
            customBitrateKbps = format.videoBitrate,
            codec = codecFamily(format),
            fps = format.fps,
            container = format.extension,
            audio = !format.hasVideo && format.hasAudio,
            videoOnly = format.hasVideo && !format.hasAudio,
            pinnedFormatId = format.id,
            audioExport = AudioExport.SOURCE,
        )
    }
}

internal object FormatSelectionResolver {
    fun candidates(
        formats: List<AvailableFormat>,
        selection: FormatSelection,
    ): List<AvailableFormat> =
        formats
            .asSequence()
            .filter { format ->
                if (selection.audio) {
                    !format.hasVideo && format.hasAudio
                } else {
                    format.hasVideo && (!selection.videoOnly || !format.hasAudio)
                }
            }.filter { format ->
                selection.audio || selection.resolution == null || (format.height ?: Int.MAX_VALUE) <= selection.resolution
            }.filter { format ->
                (selection.customBitrateKbps ?: selection.bitrateStop.maxKbps)?.let { limit ->
                    val bitrate = if (selection.audio) format.audioBitrate else format.videoBitrate
                    bitrate != null && bitrate <= limit
                } ?: true
            }.filter { format -> selection.codec == null || codecFamily(format) == selection.codec }
            .filter { selection.fps == null || it.fps == selection.fps }
            .filter { selection.container == null || it.extension.equals(selection.container, ignoreCase = true) }
            .sortedWith(
                compareBy<AvailableFormat> { it.height ?: 0 }
                    .thenBy { it.fps ?: 0 }
                    .thenBy { if (isGalleryCompatible(it)) 0 else 1 }
                    .thenBy { it.videoBitrate ?: it.audioBitrate ?: 0 }
                    .thenBy { it.id },
            ).toList()

    fun resolve(
        formats: List<AvailableFormat>,
        selection: FormatSelection,
    ): AvailableFormat? {
        val candidates = candidates(formats, selection)
        selection.pinnedFormatId?.let { pinned -> candidates.firstOrNull { it.id == pinned }?.let { return it } }
        if (candidates.isEmpty()) return null
        if (selection.audio) {
            return candidates.maxWithOrNull(
                compareBy<AvailableFormat> { if (it.extension == "m4a") 1 else 0 }
                    .thenBy { it.audioBitrate ?: 0 }
                    .thenBy { it.sizeBytes ?: 0L },
            )
        }

        val maximumHeight = selection.resolution ?: selection.qualityPreset.maximumHeight
        val pool = if (maximumHeight == null) candidates else candidates.filter { (it.height ?: Int.MAX_VALUE) <= maximumHeight }
        if (pool.isEmpty()) return null
        return pool.maxWithOrNull(
            compareBy<AvailableFormat> { it.height ?: 0 }
                .thenBy { if (isGalleryCompatible(it)) 1 else 0 }
                .thenBy { -kotlin.math.abs((it.fps ?: 30) - 30) }
                .thenBy { if (it.hasAudio) 1 else 0 }
                .thenBy { it.videoBitrate ?: 0 }
                .thenBy { it.sizeBytes ?: 0L },
        )
    }

    fun estimateBytes(
        preview: VideoPreview,
        selection: FormatSelection,
    ): Long? {
        val selected = resolve(preview.formats, selection) ?: return null
        if (selection.audio) {
            if (selection.audioExport == AudioExport.MP3) {
                val seconds = preview.durationSeconds?.takeIf { it > 0 }?.toLong() ?: return null
                return seconds * selection.audioBitrate.kilobits * 125L
            }
            return YouTubeSizeEstimator.estimatedFormatBytes(selected, preview.durationSeconds)
        }
        val videoSize = YouTubeSizeEstimator.estimatedFormatBytes(selected, preview.durationSeconds) ?: return null
        if (selection.videoOnly || selected.hasAudio) return videoSize
        val audio =
            preview.formats
                .asSequence()
                .filter { !it.hasVideo && it.hasAudio && it.extension == "m4a" }
                .filter { it.audioCodec?.startsWith("mp4a", ignoreCase = true) == true }
                .maxByOrNull { it.audioBitrate ?: 0 } ?: return videoSize
        return YouTubeSizeEstimator.estimatedFormatBytes(audio, preview.durationSeconds)?.let { videoSize + it }
    }
}

/** Resolution choices for the simple picker; prefer gallery-friendly MP4/H.264 when available. */
internal fun availableVideoResolutions(
    formats: List<AvailableFormat>,
    videoOnly: Boolean = false,
): List<Int> {
    val videoFormats = formats.filter { it.hasVideo && it.height != null && (!videoOnly || !it.hasAudio) }
    val compatibleFormats = videoFormats.filter(::isGalleryCompatible)
    return (compatibleFormats.ifEmpty { videoFormats })
        .mapNotNull { it.height }
        .distinct()
        .sorted()
}

internal fun qualityPresetForResolution(height: Int): Quality =
    when {
        height > 2160 -> Quality.MAX
        height >= 2160 -> Quality.P2160
        height >= 1440 -> Quality.P1440
        height >= 1080 -> Quality.P1080
        height >= 720 -> Quality.P720
        height >= 480 -> Quality.P480
        height >= 360 -> Quality.P360
        height >= 240 -> Quality.P240
        else -> Quality.P144
    }

private object YouTubeSizeEstimator {
    fun estimatedFormatBytes(
        format: AvailableFormat,
        durationSeconds: Int?,
    ): Long? =
        format.sizeBytes ?: durationSeconds
            ?.takeIf { it > 0 }
            ?.let { seconds ->
                val bitrate = if (format.hasVideo) format.videoBitrate else format.audioBitrate ?: return null
                bitrate?.toLong()?.times(125L)?.times(seconds.toLong())
            }
}

internal fun formatMediaSizeRu(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "Размер неизвестен"
    val symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"))
    val value: Double
    val unit: String
    when {
        bytes >= 1_000_000L -> {
            value = bytes / 1_000_000.0
            unit = "МБ"
        }

        bytes >= 1_000L -> {
            value = bytes / 1_000.0
            unit = "КБ"
        }

        else -> {
            value = bytes.toDouble()
            unit = "Б"
        }
    }
    val pattern = if (value < 10.0) "0.#" else "#,##0"
    return "≈${DecimalFormat(pattern, symbols).format(value)}\u00a0$unit"
}
