package ru.skachay.app.settings

enum class UserMode { SIMPLE, ADVANCED }

enum class DownloadPlatform { YOUTUBE, OTHER }

enum class Quality(
    val maximumHeight: Int?,
    val label: String,
) {
    AUTO(720, "Авто — до 720p"),
    P144(144, "До 144p"),
    P240(240, "До 240p"),
    P360(360, "До 360p"),
    P480(480, "До 480p"),
    P720(720, "До 720p"),
    P1080(1080, "До 1080p"),
    P1440(1440, "До 1440p"),
    P2160(2160, "До 2160p (4K)"),
    BEST(1080, "Лучшее — до 1080p"),
    MAX(null, "Максимум из доступного"),
    ;

    companion object {
        val presets = listOf(P144, P240, P360, P480, P720, P1080, P1440, P2160, MAX)
    }
}

enum class ShareMode { AUTO, CHOOSE }

enum class EngineChannel { STABLE, NIGHTLY }

enum class VideoBitrateLimit(
    val maxKbps: Int?,
    val label: String,
) {
    ANY(null, "Макс"),
    K1000(1_000, "До 1 Мбит/с"),
    K2000(2_000, "До 2 Мбит/с"),
    K4000(4_000, "До 4 Мбит/с"),
    K8000(8_000, "До 8 Мбит/с"),
    K16000(16_000, "До 16\u00a0Мбит/с"),

    // Retained so previously stored values can be migrated without losing the user's preference.
    K2500(2_500, "До 2,5 Мбит/с"),
    K5000(5_000, "До 5 Мбит/с"),
    K12000(12_000, "До 12 Мбит/с"),
    ;

    val stopCaption: String
        get() =
            when (this) {
                ANY -> "Макс"
                K1000 -> "1"
                K2000 -> "2"
                K4000 -> "4"
                K8000 -> "8"
                K16000 -> "16\u00a0Мбит/с"
                K2500 -> "2,5"
                K5000 -> "5"
                K12000 -> "12"
            }

    companion object {
        val stops = listOf(K1000, K2000, K4000, K8000, K16000, ANY)

        fun fromStoredName(name: String?): VideoBitrateLimit =
            when (name) {
                "K2500" -> K2000
                "K5000" -> K4000
                "K12000" -> K16000
                else -> entries.firstOrNull { it.name == name && it in stops } ?: ANY
            }
    }
}

internal data class QualityPresetOption(
    val quality: Quality,
    val label: String,
)

internal object QualityPresetPresentation {
    val options =
        listOf(
            QualityPresetOption(Quality.AUTO, "Авто"),
            QualityPresetOption(Quality.P144, "144p"),
            QualityPresetOption(Quality.P240, "240p"),
            QualityPresetOption(Quality.P360, "360p"),
            QualityPresetOption(Quality.P480, "480p"),
            QualityPresetOption(Quality.P720, "720p"),
            QualityPresetOption(Quality.P1080, "1080p"),
            QualityPresetOption(Quality.P1440, "1440p"),
            QualityPresetOption(Quality.P2160, "4K"),
            QualityPresetOption(Quality.MAX, "Макс"),
        )

    fun label(quality: Quality): String = options.firstOrNull { it.quality == normalized(quality) }?.label ?: "Авто"

    fun summary(quality: Quality): String = "Выбрано: ${label(quality)}"

    fun hint(quality: Quality): String =
        when (normalized(quality)) {
            Quality.AUTO -> "Авто выбирает совместимое видео до 720p."
            Quality.MAX -> "Будет выбрано наибольшее доступное разрешение."
            else -> "Выбирается видео не выше выбранного разрешения."
        }

    fun normalized(quality: Quality): Quality = if (quality == Quality.BEST) Quality.P1080 else quality
}

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val mode: UserMode = UserMode.SIMPLE,
    val quality: Quality = Quality.AUTO,
    val shareMode: ShareMode = ShareMode.AUTO,
    val parallelDownloads: Int = 2,
    val connections: Int = 8,
    val retries: Int = 2,
    val engineChannel: EngineChannel = EngineChannel.STABLE,
    val largeMode: Boolean = true,
    val wifiOnly: Boolean = false,
    val youtubeQuality: Quality? = null,
    val platformQualityEnabled: Boolean = false,
    val videoBitrateLimit: VideoBitrateLimit = VideoBitrateLimit.ANY,
    val rememberQualityChoice: Boolean = false,
    val lastVideoQualityTier: String? = null,
    val lastAudioQualityKind: String? = null,
    val lastQualityWasAudio: Boolean = false,
    val lastFormatId: String? = null,
    val downloadFolderUri: String? = null,
    val fileNameTemplate: String? = null,
)

fun AppSettings.qualityFor(platform: DownloadPlatform): Quality {
    if (!platformQualityEnabled) return quality
    return when (platform) {
        DownloadPlatform.YOUTUBE -> youtubeQuality
        DownloadPlatform.OTHER -> null
    } ?: quality
}
