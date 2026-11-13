package ru.skachay.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferStatsTest {
    @Test
    fun measuresTimeToFirstByteAverageSpeedAndRemainingTime() {
        var clock = 1_000L
        val stats = TransferStats { clock }
        stats.start()
        clock = 1_500L
        stats.update(0, 3_000_000)
        clock = 2_000L
        val snapshot = stats.update(1_000_000, 3_000_000)
        assertEquals(1_000L, snapshot.firstByteMillis)
        assertEquals(1_000_000L, snapshot.averageBytesPerSecond)
        assertEquals(2_000L, snapshot.remainingMillis)
    }

    @Test
    fun leavesUnknownSizeEstimatesEmpty() {
        val stats = TransferStats { 10L }
        stats.start()
        val snapshot = stats.update(100, null)
        assertNull(snapshot.totalBytes)
        assertNull(snapshot.remainingMillis)
    }

    @Test
    fun changingEstimatedSizeDoesNotCountTheSameStreamRepeatedly() {
        val stats = TransferStats { 10L }
        stats.start()
        stats.update(250_000_000, 500_000_000)
        stats.update(249_000_000, 498_000_000)
        stats.update(248_000_000, 496_000_000)
        val snapshot = stats.update(260_000_000, 500_000_000)
        assertEquals(260_000_000L, snapshot.bytesReceived)
        assertEquals(500_000_000L, snapshot.totalBytes)
    }

    @Test
    fun countsOnlyFinishedStreamsWhenAnotherStreamStarts() {
        val stats = TransferStats { 10L }
        stats.start()
        stats.update(500, 1000)
        stats.nextStream()
        assertEquals(100L, stats.update(100, 1000).bytesReceived)
        stats.update(1000, 1000)
        stats.nextStream()
        val snapshot = stats.update(200, 300)
        assertEquals(1200L, snapshot.bytesReceived)
        assertEquals(1300L, snapshot.totalBytes)
    }
}
