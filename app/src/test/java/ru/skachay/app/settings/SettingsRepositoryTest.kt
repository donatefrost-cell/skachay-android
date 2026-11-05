package ru.skachay.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
            assertEquals(2, settings.parallelDownloads)
            assertEquals(8, settings.connections)
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
            val settings = repository.settings.first()
            assertTrue(settings.onboardingComplete)
            assertEquals(UserMode.ADVANCED, settings.mode)
            assertEquals(Quality.BEST, settings.quality)
            assertEquals(ShareMode.CHOOSE, settings.shareMode)
            assertEquals(4, settings.parallelDownloads)
            assertEquals(1, settings.connections)
        }
    }

    @Test
    fun switchingToSimpleRestoresAutomaticShareAndDefaultConcurrency() = runBlocking {
        withRepository { repository ->
            repository.selectMode(UserMode.ADVANCED)
            repository.setShareMode(ShareMode.CHOOSE)
            repository.setParallelDownloads(4)
            repository.setConnections(16)
            repository.selectMode(UserMode.SIMPLE)
            val settings = repository.settings.first()
            assertEquals(ShareMode.AUTO, settings.shareMode)
            assertEquals(2, settings.parallelDownloads)
            assertEquals(8, settings.connections)
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
