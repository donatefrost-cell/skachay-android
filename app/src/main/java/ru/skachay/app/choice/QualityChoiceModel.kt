package ru.skachay.app.choice

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import ru.skachay.app.settings.Quality

internal enum class QualityVideoTier(val title: String) {
    MAXIMUM("Максимум"),
    RECOMMENDED("Хорошее"),
    ECONOMICAL("Экономное"),
    MINIMUM("Минимум"),
}

internal data class QualityVideoCard(
    val tier: QualityVideoTier,
    val video: AvailableFormat,
    val audio: AvailableFormat?,
    val estimatedSizeBytes: Long?,
) {
    val recommended: Boolean get() = tier == QualityVideoTier.RECOMMENDED
    val sizeWarning: Boolean get() = estimatedSizeBytes?.let { it >= LARGE_VIDEO_WARNING_BYTES } == true
}

internal enum class QualityAudioKind(val title: String) {
    BEST_M4A("Лучшее · m4a"),
    COMPACT_MP3("Компактное · MP3 128 кбит/с"),
}

internal data class QualityAudioCard(
    val kind: QualityAudioKind,
    val source: AvailableFormat,
    val estimatedSizeBytes: Long?,
)

internal object QualityChoiceBuilder {
    fun videoCards(preview: VideoPreview): List<QualityVideoCard> {
        val audio = bestM4a(preview)
        val variants = preview.formats.asSequence()
            .filter { it.hasVideo && it.height != null && it.extension == "mp4" }
            .filter { it.videoCodec?.startsWith("avc1", ignoreCase = true) == true }
            .filter { format ->
                if (format.hasAudio) format.audioCodec?.startsWith("mp4a", ignoreCase = true) == true
                else audio != null
            }
            .groupBy { it.height }
            .values
            .mapNotNull { formats ->
                formats.minWithOrNull(
                    compareBy<AvailableFormat> { kotlin.math.abs((it.fps ?: 30) - 30) }
                        .thenBy { it.videoBitrate ?: Int.MAX_VALUE }
                        .thenBy { it.id },
                )
            }
            .toList()
        if (variants.isEmpty()) return emptyList()

        val maximum = variants.maxWithOrNull(compareBy<AvailableFormat> { it.height ?: 0 }
            .thenBy { it.fps ?: 30 }.thenBy { it.videoBitrate ?: 0 })
        val recommended = variants.minWithOrNull(compareBy<AvailableFormat> { kotlin.math.abs((it.height ?: 720) - 720) }
            .thenBy { kotlin.math.abs((it.fps ?: 30) - 30) }
            .thenByDescending { it.height ?: 0 }
            .thenByDescending { it.fps ?: 30 })
        val economical = variants.minWithOrNull(compareBy<AvailableFormat> { kotlin.math.abs((it.height ?: 360) - 360) }
            .thenBy { kotlin.math.abs((it.fps ?: 30) - 30) }
            .thenByDescending { it.height ?: 0 }
            .thenByDescending { it.fps ?: 30 })
        val minimum = variants.minWithOrNull(compareBy<AvailableFormat> { it.height ?: Int.MAX_VALUE }
            .thenBy { it.fps ?: 30 }
            .thenBy { it.videoBitrate ?: Int.MAX_VALUE })

        val candidates = listOfNotNull(
            maximum?.let { QualityVideoTier.MAXIMUM to it },
            recommended?.let { QualityVideoTier.RECOMMENDED to it },
            economical?.let { QualityVideoTier.ECONOMICAL to it },
            minimum?.let { QualityVideoTier.MINIMUM to it },
        )
        val uniqueTiers = linkedMapOf<String, Pair<QualityVideoTier, AvailableFormat>>()
        candidates.forEach { candidate ->
            val (tier, format) = candidate
            if (format.id !in uniqueTiers || tier == QualityVideoTier.RECOMMENDED) uniqueTiers[format.id] = candidate
        }

        return uniqueTiers.values.map { (tier, format) ->
            val selectedAudio = if (format.hasAudio) null else audio
            QualityVideoCard(
                tier = tier,
                video = format,
                audio = selectedAudio,
                estimatedSizeBytes = estimateVideoSize(format, selectedAudio, preview.durationSeconds),
            )
        }
    }

    fun audioCards(preview: VideoPreview): List<QualityAudioCard> {
        val source = bestM4a(preview) ?: return emptyList()
        val originalSize = estimateFormatSize(source, preview.durationSeconds)
        val mp3Size = preview.durationSeconds?.takeIf { it > 0 }?.toLong()?.times(128_000L)?.div(8L)
        return listOf(
            QualityAudioCard(QualityAudioKind.BEST_M4A, source, originalSize),
            QualityAudioCard(QualityAudioKind.COMPACT_MP3, source, mp3Size),
        )
    }

    fun initialVideoChoice(
        cards: List<QualityVideoCard>,
        defaultQuality: Quality,
        remember: Boolean,
        lastTier: String?,
    ): QualityVideoCard? {
        if (cards.isEmpty()) return null
        val requested = if (remember) lastTier?.let { name -> QualityVideoTier.entries.firstOrNull { it.name == name } } else null
        val default = requested ?: when (defaultQuality) {
            Quality.P360, Quality.P480 -> QualityVideoTier.ECONOMICAL
            Quality.AUTO, Quality.P720 -> QualityVideoTier.RECOMMENDED
            Quality.MAX -> QualityVideoTier.MAXIMUM
            Quality.BEST, Quality.P1080, Quality.P1440, Quality.P2160 -> null
        }
        if (default != null) return cards.firstOrNull { it.tier == default }
            ?: cards.firstOrNull { it.tier == QualityVideoTier.RECOMMENDED }
            ?: cards.first()
        val maxHeight = defaultQuality.maximumHeight ?: Int.MAX_VALUE
        return cards.filter { (it.video.height ?: Int.MAX_VALUE) <= maxHeight }
            .maxByOrNull { it.video.height ?: 0 }
            ?: cards.minByOrNull { it.video.height ?: Int.MAX_VALUE }
    }

    fun initialAudioChoice(cards: List<QualityAudioCard>, remember: Boolean, lastKind: String?): QualityAudioCard? {
        if (cards.isEmpty()) return null
        val requested = if (remember) lastKind?.let { name -> QualityAudioKind.entries.firstOrNull { it.name == name } } else null
        return cards.firstOrNull { it.kind == (requested ?: QualityAudioKind.BEST_M4A) } ?: cards.first()
    }

    private fun bestM4a(preview: VideoPreview): AvailableFormat? = preview.formats.asSequence()
        .filter { !it.hasVideo && it.hasAudio && it.extension == "m4a" }
        .filter { it.audioCodec?.startsWith("mp4a", ignoreCase = true) == true }
        .maxByOrNull { it.audioBitrate ?: 0 }

    private fun estimateVideoSize(video: AvailableFormat, audio: AvailableFormat?, duration: Int?): Long? {
        val videoSize = estimateFormatSize(video, duration) ?: return null
        val audioSize = audio?.let { estimateFormatSize(it, duration) ?: return null } ?: 0L
        return videoSize + audioSize
    }

    private fun estimateFormatSize(format: AvailableFormat, duration: Int?): Long? = format.sizeBytes ?: duration
        ?.takeIf { it > 0 }
        ?.let { seconds ->
            val bitrate = when {
                format.hasVideo -> format.videoBitrate
                else -> format.audioBitrate
            } ?: return null
            bitrate.toLong().times(125L).times(seconds.toLong())
        }
}

internal const val LARGE_VIDEO_WARNING_BYTES = 500L * 1_000_000L

internal fun formatMediaSizeRu(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "Размер неизвестен"
    val symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"))
    val value: Double
    val unit: String
    when {
        bytes >= 1_000_000L -> { value = bytes / 1_000_000.0; unit = "МБ" }
        bytes >= 1_000L -> { value = bytes / 1_000.0; unit = "КБ" }
        else -> { value = bytes.toDouble(); unit = "Б" }
    }
    val pattern = if (value < 10.0) "0.#" else "#,##0"
    return "≈${DecimalFormat(pattern, symbols).format(value)} $unit"
}
