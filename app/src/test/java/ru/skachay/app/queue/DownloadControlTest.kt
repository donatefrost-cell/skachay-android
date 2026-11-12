package ru.skachay.app.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DownloadControlTest {
    @Test
    fun pausedTransferWaitsUntilItIsResumed() {
        val control = DownloadControl()
        control.pause()
        val started = CountDownLatch(1)
        val passedCheckpoint = CountDownLatch(1)
        val thread =
            Thread {
                started.countDown()
                control.checkpoint()
                passedCheckpoint.countDown()
            }.apply { start() }

        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertFalse(passedCheckpoint.await(100, TimeUnit.MILLISECONDS))
        control.resume()
        assertTrue(passedCheckpoint.await(1, TimeUnit.SECONDS))
        thread.join(1_000)
    }

    @Test
    fun cancellingWakesAPausedTransferAndFailsItsCheckpoint() {
        val control = DownloadControl()
        control.pause()
        val stopped = CountDownLatch(1)
        val thread =
            Thread {
                try {
                    control.checkpoint()
                } catch (_: Exception) {
                    stopped.countDown()
                }
            }.apply { start() }

        control.cancel()

        assertTrue(stopped.await(1, TimeUnit.SECONDS))
        assertTrue(control.isCancelled())
        thread.join(1_000)
    }
}
