package ru.skachay.app.download

import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit

internal data class YouTubeStreams(val video: AvailableFormat?, val audio: AvailableFormat?)

internal object YouTubeFormatSelector {
    fun automatic(
        preview: VideoPreview,
        quality: Quality,
        videoBitrateLimit: VideoBitrateLimit = VideoBitrateLimit.ANY,
    ): YouTubeStreams? {
        val video = preferredVideo(preview, quality, videoBitrateLimit)?.takeIf { it.directUrl != null } ?: return null
        val audio = bestM4a(preview, requireDirectUrl = true) ?: return null
        return YouTubeStreams(video, audio)
    }

    fun preferredVideo(
        preview: VideoPreview,
        quality: Quality,
        videoBitrateLimit: VideoBitrateLimit = VideoBitrateLimit.ANY,
    ): AvailableFormat? {
        val maxHeight = quality.maximumHeight ?: Int.MAX_VALUE
        return preview.formats.asSequence()
            .filter { it.hasVideo && it.height != null && it.height <= maxHeight }
            .filter { it.videoCodec?.startsWith("avc1", ignoreCase = true) == true }
            .filter { format -> videoBitrateLimit.maxKbps?.let { cap -> format.videoBitrate?.let { it <= cap } == true } ?: true }
            .maxWithOrNull(compareBy<AvailableFormat> { it.height ?: 0 }
                .thenBy { it.fps ?: 0 }.thenBy { it.videoBitrate ?: 0 })
    }

    fun selected(preview: VideoPreview, selected: AvailableFormat, videoOnly: Boolean): YouTubeStreams? {
        if (selected.directUrl == null) return null
        if (!selected.hasVideo) return if (selected.hasAudio) YouTubeStreams(null, selected) else null
        if (videoOnly || selected.hasAudio) return YouTubeStreams(selected, null)
        val audio = bestM4a(preview, requireDirectUrl = true) ?: return null
        return YouTubeStreams(selected, audio)
    }

    fun estimate(
        preview: VideoPreview,
        quality: Quality,
        selected: AvailableFormat?,
        videoOnly: Boolean,
        videoBitrateLimit: VideoBitrateLimit = VideoBitrateLimit.ANY,
    ): Long? {
        val video = selected?.takeIf { it.hasVideo } ?: if (selected == null) preferredVideo(preview, quality, videoBitrateLimit) else null
        if (selected == null && video == null) return null
        val audio = selected?.takeIf { it.hasAudio } ?: if (selected == null || (video != null && !videoOnly && !video.hasAudio)) {
            bestM4a(preview, requireDirectUrl = false)
        } else null
        if (selected != null && selected.hasVideo && selected.hasAudio) return estimatedBytes(YouTubeStreams(selected, null), preview.durationSeconds)
        return estimatedBytes(YouTubeStreams(video, audio), preview.durationSeconds)
    }

    fun estimatedBytes(streams: YouTubeStreams, durationSeconds: Int?): Long? {
        val duration = durationSeconds?.takeIf { it > 0 }
        val sizes = listOfNotNull(streams.video, streams.audio).map { estimatedFormatBytes(it, duration) ?: return null }
        return sizes.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun bestM4a(preview: VideoPreview, requireDirectUrl: Boolean): AvailableFormat? = preview.formats.asSequence()
        .filter { !it.hasVideo && it.hasAudio && it.extension == "m4a" && (!requireDirectUrl || it.directUrl != null) }
        .filter { it.audioCodec?.startsWith("mp4a", ignoreCase = true) == true }
        .maxByOrNull { it.audioBitrate ?: 0 }

    fun estimatedFormatBytes(format: AvailableFormat, durationSeconds: Int?): Long? =
        format.sizeBytes ?: durationSeconds?.let { duration ->
            val kilobits = if (format.hasVideo) format.videoBitrate else format.audioBitrate
            kilobits?.toLong()?.times(125L)?.times(duration.toLong())
        }
}
