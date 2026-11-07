package ru.skachay.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                onboardingComplete = preferences[ONBOARDING] ?: false,
                mode = enumOrDefault(preferences[MODE], UserMode.SIMPLE),
                quality = enumOrDefault(preferences[QUALITY], Quality.AUTO),
                shareMode = enumOrDefault(preferences[SHARE_MODE], ShareMode.AUTO),
                parallelDownloads = (preferences[PARALLEL] ?: 2).coerceIn(1, 4),
                connections = (preferences[CONNECTIONS] ?: 8).coerceIn(1, 16),
                retries = (preferences[RETRIES] ?: 2).coerceIn(0, 5),
                engineChannel = enumOrDefault(preferences[ENGINE_CHANNEL], EngineChannel.STABLE),
                largeMode = preferences[LARGE_MODE] ?: ((preferences[MODE] ?: UserMode.SIMPLE.name) == UserMode.SIMPLE.name),
                wifiOnly = preferences[WIFI_ONLY] ?: false,
                youtubeQuality = preferences[YOUTUBE_QUALITY]?.let { enumOrDefault(it, preferences[QUALITY]?.let { saved -> enumOrDefault(saved, Quality.AUTO) } ?: Quality.AUTO) },
                videoBitrateLimit = enumOrDefault(preferences[VIDEO_BITRATE_LIMIT], VideoBitrateLimit.ANY),
                rememberQualityChoice = preferences[REMEMBER_QUALITY_CHOICE] ?: false,
                lastVideoQualityTier = preferences[LAST_VIDEO_QUALITY_TIER],
                lastAudioQualityKind = preferences[LAST_AUDIO_QUALITY_KIND],
                lastQualityWasAudio = preferences[LAST_QUALITY_WAS_AUDIO] ?: false,
            )
        }

    suspend fun selectMode(mode: UserMode) {
        store.edit {
            it[MODE] = mode.name
            it[ONBOARDING] = true
            if (!it.contains(LARGE_MODE)) it[LARGE_MODE] = mode == UserMode.SIMPLE
            if (mode == UserMode.SIMPLE) {
                it[SHARE_MODE] = ShareMode.AUTO.name
                it[PARALLEL] = 2
                it[CONNECTIONS] = 8
                it[RETRIES] = 2
            }
        }
    }

    suspend fun setQuality(quality: Quality) { store.edit { it[QUALITY] = quality.name } }
    suspend fun setShareMode(mode: ShareMode) { store.edit { it[SHARE_MODE] = mode.name } }
    suspend fun setParallelDownloads(value: Int) { store.edit { it[PARALLEL] = value.coerceIn(1, 4) } }
    suspend fun setConnections(value: Int) { store.edit { it[CONNECTIONS] = value.coerceIn(1, 16) } }
    suspend fun setRetries(value: Int) { store.edit { it[RETRIES] = value.coerceIn(0, 5) } }
    suspend fun setEngineChannel(channel: EngineChannel) { store.edit { it[ENGINE_CHANNEL] = channel.name } }
    suspend fun setLargeMode(enabled: Boolean) { store.edit { it[LARGE_MODE] = enabled } }
    suspend fun setWifiOnly(enabled: Boolean) { store.edit { it[WIFI_ONLY] = enabled } }
    suspend fun setVideoBitrateLimit(limit: VideoBitrateLimit) { store.edit { it[VIDEO_BITRATE_LIMIT] = limit.name } }
    suspend fun setRememberQualityChoice(enabled: Boolean) { store.edit { it[REMEMBER_QUALITY_CHOICE] = enabled } }
    suspend fun setLastQualityChoices(videoTier: String?, audioKind: String?, wasAudio: Boolean? = null) {
        store.edit { preferences ->
            if (videoTier == null) preferences.remove(LAST_VIDEO_QUALITY_TIER) else preferences[LAST_VIDEO_QUALITY_TIER] = videoTier
            if (audioKind == null) preferences.remove(LAST_AUDIO_QUALITY_KIND) else preferences[LAST_AUDIO_QUALITY_KIND] = audioKind
            if (wasAudio != null) preferences[LAST_QUALITY_WAS_AUDIO] = wasAudio
        }
    }
    suspend fun setPlatformQuality(platform: DownloadPlatform, quality: Quality) {
        store.edit { preferences ->
            preferences[when (platform) {
                DownloadPlatform.YOUTUBE -> YOUTUBE_QUALITY
                DownloadPlatform.OTHER -> QUALITY
            }] = quality.name
        }
    }

    companion object {
        private val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        private val MODE = stringPreferencesKey("mode")
        private val QUALITY = stringPreferencesKey("quality")
        private val SHARE_MODE = stringPreferencesKey("share_mode")
        private val PARALLEL = intPreferencesKey("parallel_downloads")
        private val CONNECTIONS = intPreferencesKey("connections")
        private val RETRIES = intPreferencesKey("retries")
        private val ENGINE_CHANNEL = stringPreferencesKey("engine_channel")
        private val LARGE_MODE = booleanPreferencesKey("large_mode")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val YOUTUBE_QUALITY = stringPreferencesKey("youtube_quality")
        private val VIDEO_BITRATE_LIMIT = stringPreferencesKey("video_bitrate_limit")
        private val REMEMBER_QUALITY_CHOICE = booleanPreferencesKey("remember_quality_choice")
        private val LAST_VIDEO_QUALITY_TIER = stringPreferencesKey("last_video_quality_tier")
        private val LAST_AUDIO_QUALITY_KIND = stringPreferencesKey("last_audio_quality_kind")
        private val LAST_QUALITY_WAS_AUDIO = booleanPreferencesKey("last_quality_was_audio")

        fun forContext(context: Context): SettingsRepository = SettingsRepository(context.applicationContext.settingsStore)

        private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
