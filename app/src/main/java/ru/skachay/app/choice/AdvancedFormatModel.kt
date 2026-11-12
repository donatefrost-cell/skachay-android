package ru.skachay.app.choice

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal enum class AdvancedFormatFilter(
    val label: String,
) {
    COMPATIBLE("Работает везде"),
    ALL("Все"),
    H264("H.264"),
    VP9("VP9"),
    AV1("AV1"),
    VIDEO_ONLY("Без звука"),
}

internal fun filterAdvancedFormats(
    formats: List<AvailableFormat>,
    filter: AdvancedFormatFilter,
): List<AvailableFormat> =
    when (filter) {
        AdvancedFormatFilter.COMPATIBLE -> formats.filter(::isGalleryCompatible)
        AdvancedFormatFilter.ALL -> formats
        AdvancedFormatFilter.H264 -> formats.filter { it.hasVideo && codecFamily(it) == "H.264" }
        AdvancedFormatFilter.VP9 -> formats.filter { it.hasVideo && codecFamily(it) == "VP9" }
        AdvancedFormatFilter.AV1 -> formats.filter { it.hasVideo && codecFamily(it) == "AV1" }
        AdvancedFormatFilter.VIDEO_ONLY -> formats.filter { it.hasVideo && !it.hasAudio }
    }

internal fun isGalleryCompatible(format: AvailableFormat): Boolean =
    when {
        format.hasVideo -> {
            format.extension.equals("mp4", ignoreCase = true) && codecFamily(format) == "H.264"
        }

        format.hasAudio -> {
            format.extension.equals("m4a", ignoreCase = true) &&
                format.audioCodec?.startsWith("mp4a", ignoreCase = true) == true
        }

        else -> {
            false
        }
    }

internal fun codecFamily(format: AvailableFormat): String {
    val codec = if (format.hasVideo) format.videoCodec.orEmpty() else format.audioCodec.orEmpty()
    return when {
        codec.startsWith("avc1", true) || codec.startsWith("avc3", true) -> "H.264"
        codec.startsWith("vp09", true) || codec.startsWith("vp9", true) -> "VP9"
        codec.startsWith("av01", true) || codec.startsWith("av1", true) -> "AV1"
        codec.startsWith("hev1", true) || codec.startsWith("hvc1", true) -> "HEVC"
        codec.startsWith("mp4a", true) -> "AAC"
        codec.isBlank() -> "Кодек неизвестен"
        else -> codec.substringBefore('.')
    }
}

internal fun formatAdvancedBitrateRu(kilobitsPerSecond: Int?): String =
    when {
        kilobitsPerSecond == null || kilobitsPerSecond <= 0 -> {
            "Битрейт неизвестен"
        }

        kilobitsPerSecond >= 1_000 -> {
            val symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"))
            val number = DecimalFormat("0.#", symbols).format(kilobitsPerSecond / 1_000.0)
            "$number\u00a0Мбит/с"
        }

        else -> {
            "$kilobitsPerSecond\u00a0кбит/с"
        }
    }

internal fun formatAdvancedSizeRu(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "Размер неизвестен"
    val symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"))
    val (value, unit) =
        when {
            bytes >= 1_000_000L -> bytes / 1_000_000.0 to "МБ"
            bytes >= 1_000L -> bytes / 1_000.0 to "КБ"
            else -> bytes.toDouble() to "Б"
        }
    val pattern = if (value < 10) "0.#" else "#,##0"
    return DecimalFormat(pattern, symbols).format(value) + "\u00a0" + unit
}
