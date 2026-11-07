package ru.skachay.app.settings

enum class UserMode { SIMPLE, ADVANCED }
enum class DownloadPlatform { YOUTUBE, OTHER }
enum class Quality(val maximumHeight: Int?, val label: String) {
    AUTO(720, "Авто — до 720p"),
    P360(360, "До 360p"),
    P480(480, "До 480p"),
    P720(720, "До 720p"),
    P1080(1080, "До 1080p"),
    P1440(1440, "До 1440p"),
    P2160(2160, "До 2160p (4K)"),
    BEST(1080, "Лучшее — до 1080p"),
    MAX(null, "Максимум из доступного");

    companion object {
        val presets = listOf(P360, P480, P720, P1080, P1440, P2160, MAX)
    }
}
enum class ShareMode { AUTO, CHOOSE }
enum class EngineChannel { STABLE, NIGHTLY }

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
)

fun AppSettings.qualityFor(platform: DownloadPlatform): Quality = when (platform) {
    DownloadPlatform.YOUTUBE -> youtubeQuality
    DownloadPlatform.OTHER -> null
} ?: quality
