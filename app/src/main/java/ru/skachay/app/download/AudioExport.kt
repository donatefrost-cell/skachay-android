package ru.skachay.app.download

internal enum class AudioExport(
    val ytDlpFormat: String?,
    val label: String,
) {
    SOURCE(null, "Исходный"),
    M4A("m4a", "M4A"),
    MP3("mp3", "MP3"),
    OPUS("opus", "Opus"),
}

internal enum class AudioBitrate(
    val kilobits: Int,
) {
    K96(96),
    K128(128),
    K192(192),
    K256(256),
    K320(320),
}
