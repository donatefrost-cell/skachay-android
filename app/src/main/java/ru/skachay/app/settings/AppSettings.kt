package ru.skachay.app.settings

enum class UserMode { SIMPLE, ADVANCED }
enum class DownloadPlatform { YOUTUBE, OTHER }
enum class Quality(val maximumHeight: Int?, val label: String) {
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
    MAX(null, "Максимум из доступного");

    companion object {
        val presets = listOf(P144, P240, P360, P480, P720, P1080, P1440, P2160, MAX)
    }
}
enum class ShareMode { AUTO, CHOOSE }
enum class EngineChannel { STABLE, NIGHTLY }
enum class VideoBitrateLimit(val maxKbps: Int?, val label: String) {
    ANY(null, "Без ограничения"),
    K1000(1_000, "До 1 Мбит/с"),
    K2500(2_500, "До 2,5 Мбит/с"),
    K5000(5_000, "До 5 Мбит/с"),
    K8000(8_000, "До 8 Мбит/с"),
    K12000(12_000, "До 12 Мбит/с"),
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
