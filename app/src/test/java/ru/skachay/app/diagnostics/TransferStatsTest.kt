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
}
