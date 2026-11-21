package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException

class TikTokVideoTransferTest {
    private val original = ResolvedMedia("https://www.tiktok.com/old.mp4", ResolvedMedia.Kind.VIDEO)
    private val refreshed = original.copy(url = "https://www.tiktok.com/new.mp4")

    @Test
    fun refreshesTheMediaUrlAfterCdnRejectsTheOriginal() {
        val attempts = mutableListOf<ResolvedMedia>()
        val result =
            TikTokVideoTransfer.download(
                original = original,
                direct = {
                    attempts += it
                    if (it == original) throw IOException("Range request failed", DirectDownloader.HttpStatusException(403))
                    File("fresh.mp4")
                },
                refresh = { refreshed },
                alternate = { error("Fresh media was available") },
            )
        assertEquals(listOf(original, refreshed), attempts)
        assertEquals("fresh.mp4", result.name)
    }

    @Test
    fun sameRejectedUrlIsNotRetriedAndAlternateFailureKeepsThe403() {
        val rejection = DirectDownloader.HttpStatusException(403)
        val alternate = IOException("TikTok extractor failed")
        var requests = 0
        val error =
            assertThrows(IOException::class.java) {
                TikTokVideoTransfer.download(
                    original = original,
                    direct = {
                        requests++
                        throw rejection
                    },
                    refresh = { original },
                    alternate = { throw alternate },
                )
            }
        assertEquals(1, requests)
        assertSame(alternate, error)
        assertSame(rejection, error.suppressed.single())
    }

    @Test
    fun anUnrelatedFailureDoesNotRestartTheDownload() {
        val diskError = IOException("Not enough free storage")
        val error =
            assertThrows(IOException::class.java) {
                TikTokVideoTransfer.download(
                    original = original,
                    direct = { throw diskError },
                    refresh = { error("Disk errors do not need fresh media") },
                    alternate = { error("Disk errors do not need a different downloader") },
                )
            }
        assertSame(diskError, error)
    }
}
