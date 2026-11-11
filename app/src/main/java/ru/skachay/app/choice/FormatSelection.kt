package ru.skachay.app.choice

import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit

/** Complete editable state for the downloader's video/audio format selection. */
internal data class FormatSelection(
    val resolution: Int? = null,
    val qualityPreset: Quality = Quality.AUTO,
    val bitrateStop: VideoBitrateLimit = VideoBitrateLimit.ANY,
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
    ): FormatSelection = copy(
        resolution = resolution,
        bitrateStop = bitrateStop,
        codec = codec,
        fps = fps,
        container = container,
        audio = audio,
        pinnedFormatId = null,
    )

    fun pin(format: AvailableFormat): FormatSelection = copy(pinnedFormatId = format.id)
}

internal object FormatSelectionResolver {
    fun candidates(formats: List<AvailableFormat>, selection: FormatSelection): List<AvailableFormat> =
        formats.asSequence()
            .filter { format ->
                if (selection.audio) !format.hasVideo && format.hasAudio
                else format.hasVideo && (!selection.videoOnly || !format.hasAudio)
            }
            .filter { selection.resolution == null || it.height == selection.resolution }
            .filter { format ->
                selection.bitrateStop.maxKbps?.let { limit ->
                    val bitrate = if (selection.audio) format.audioBitrate else format.videoBitrate
                    bitrate != null && bitrate <= limit
                } ?: true
            }
            .filter { format -> selection.codec == null || codecFamily(format) == selection.codec }
            .filter { selection.fps == null || it.fps == selection.fps }
            .filter { selection.container == null || it.extension.equals(selection.container, ignoreCase = true) }
            .sortedWith(
                compareBy<AvailableFormat> { it.height ?: 0 }
                    .thenBy { it.fps ?: 0 }
                    .thenBy { if (isGalleryCompatible(it)) 0 else 1 }
                    .thenBy { it.videoBitrate ?: it.audioBitrate ?: 0 }
                    .thenBy { it.id },
            )
            .toList()

    fun resolve(formats: List<AvailableFormat>, selection: FormatSelection): AvailableFormat? {
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
        val underPreset = if (maximumHeight == null) candidates else candidates.filter { (it.height ?: Int.MAX_VALUE) <= maximumHeight }
        val pool = underPreset.ifEmpty { candidates.minByOrNull { it.height ?: Int.MAX_VALUE }?.let(::listOf).orEmpty() }
        return pool.maxWithOrNull(
            compareBy<AvailableFormat> { if (selection.resolution == null) it.height ?: 0 else 0 }
                .thenBy { if (isGalleryCompatible(it)) 1 else 0 }
                .thenBy { -kotlin.math.abs((it.fps ?: 30) - 30) }
                .thenBy { it.videoBitrate ?: 0 }
                .thenBy { if (it.hasAudio) 1 else 0 }
                .thenBy { it.sizeBytes ?: 0L },
        )
    }

    fun estimateBytes(preview: VideoPreview, selection: FormatSelection): Long? {
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
        val audio = preview.formats.asSequence()
            .filter { !it.hasVideo && it.hasAudio && it.extension == "m4a" }
            .filter { it.audioCodec?.startsWith("mp4a", ignoreCase = true) == true }
            .maxByOrNull { it.audioBitrate ?: 0 } ?: return videoSize
        return YouTubeSizeEstimator.estimatedFormatBytes(audio, preview.durationSeconds)?.let { videoSize + it }
    }
}

private object YouTubeSizeEstimator {
    fun estimatedFormatBytes(format: AvailableFormat, durationSeconds: Int?): Long? = format.sizeBytes ?: durationSeconds
        ?.takeIf { it > 0 }
        ?.let { seconds ->
            val bitrate = if (format.hasVideo) format.videoBitrate else format.audioBitrate ?: return null
            bitrate?.toLong()?.times(125L)?.times(seconds.toLong())
        }
}
