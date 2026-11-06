package ru.skachay.app.settings

enum class UserMode { SIMPLE, ADVANCED }
enum class Quality { AUTO, BEST }
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
)
