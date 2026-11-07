package ru.skachay.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun defaultsToSimpleAutomaticDownloads() = runBlocking {
        withRepository { repository ->
            val settings = repository.settings.first()
            assertFalse(settings.onboardingComplete)
            assertEquals(UserMode.SIMPLE, settings.mode)
            assertEquals(Quality.AUTO, settings.quality)
            assertEquals(ShareMode.AUTO, settings.shareMode)
            assertEquals(EngineChannel.STABLE, settings.engineChannel)
            assertEquals(2, settings.parallelDownloads)
            assertEquals(8, settings.connections)
            assertEquals(2, settings.retries)
            assertEquals(VideoBitrateLimit.ANY, settings.videoBitrateLimit)
            assertFalse(settings.rememberQualityChoice)
            assertEquals(null, settings.lastVideoQualityTier)
            assertEquals(null, settings.lastAudioQualityKind)
            assertEquals(false, settings.lastQualityWasAudio)
            assertTrue(settings.largeMode)
            assertFalse(settings.wifiOnly)
            assertEquals(Quality.AUTO, settings.qualityFor(DownloadPlatform.YOUTUBE))
            assertEquals(Quality.AUTO, settings.qualityFor(DownloadPlatform.OTHER))
        }
    }

    @Test
    fun savesChoicesAndBoundsTheNumberOfConnections() = runBlocking {
        withRepository { repository ->
            repository.selectMode(UserMode.ADVANCED)
            repository.setQuality(Quality.BEST)
            repository.setShareMode(ShareMode.CHOOSE)
            repository.setParallelDownloads(8)
            repository.setConnections(0)
            repository.setRetries(99)
            repository.setEngineChannel(EngineChannel.NIGHTLY)
            repository.setLargeMode(false)
            repository.setWifiOnly(true)
            repository.setVideoBitrateLimit(VideoBitrateLimit.K5000)
            repository.setRememberQualityChoice(true)
            repository.setLastQualityChoices("ECONOMICAL", "COMPACT_MP3", wasAudio = true)
            val settings = repository.settings.first()
            assertTrue(settings.onboardingComplete)
            assertEquals(UserMode.ADVANCED, settings.mode)
            assertEquals(Quality.BEST, settings.quality)
            assertEquals(ShareMode.CHOOSE, settings.shareMode)
            assertEquals(4, settings.parallelDownloads)
            assertEquals(1, settings.connections)
            assertEquals(5, settings.retries)
            assertEquals(EngineChannel.NIGHTLY, settings.engineChannel)
            assertFalse(settings.largeMode)
            assertTrue(settings.wifiOnly)
            assertEquals(VideoBitrateLimit.K5000, settings.videoBitrateLimit)
            assertTrue(settings.rememberQualityChoice)
            assertEquals("ECONOMICAL", settings.lastVideoQualityTier)
            assertEquals("COMPACT_MP3", settings.lastAudioQualityKind)
            assertEquals(true, settings.lastQualityWasAudio)
        }
    }

    @Test
    fun switchingToSimpleRestoresAutomaticShareAndDefaultConcurrency() = runBlocking {
        withRepository { repository ->
            repository.selectMode(UserMode.ADVANCED)
            repository.setShareMode(ShareMode.CHOOSE)
            repository.setParallelDownloads(4)
            repository.setConnections(16)
            repository.setRetries(5)
            repository.selectMode(UserMode.SIMPLE)
            val settings = repository.settings.first()
            assertEquals(ShareMode.AUTO, settings.shareMode)
            assertEquals(2, settings.parallelDownloads)
            assertEquals(8, settings.connections)
            assertEquals(2, settings.retries)
        }
    }

    @Test
    fun unknownUpdateChannelFallsBackToStable() = runBlocking {
        val store = MemoryStore()
        store.updateData { preferences -> preferences.toMutablePreferences().apply { this[stringPreferencesKey("engine_channel")] = "unknown" } }
        assertEquals(EngineChannel.STABLE, SettingsRepository(store).settings.first().engineChannel)
    }

    @Test
    fun platformQualityOverridesDefaultOnlyForThatPlatform() = runBlocking {
        withRepository { repository ->
            repository.setQuality(Quality.P1080)
            repository.setPlatformQuality(DownloadPlatform.YOUTUBE, Quality.P2160)
            val settings = repository.settings.first()
            assertEquals(Quality.P2160, settings.qualityFor(DownloadPlatform.YOUTUBE))
            assertEquals(Quality.P1080, settings.qualityFor(DownloadPlatform.OTHER))
        }
    }

    private suspend fun withRepository(block: suspend (SettingsRepository) -> Unit) {
        block(SettingsRepository(MemoryStore()))
    }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return transform(data.value).also { data.value = it }
        }
    }
}
