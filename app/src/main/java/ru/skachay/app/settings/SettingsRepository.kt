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

        fun forContext(context: Context): SettingsRepository = SettingsRepository(context.applicationContext.settingsStore)

        private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
