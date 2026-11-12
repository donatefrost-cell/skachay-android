package ru.skachay.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpProgressParserTest {
    @Test
    fun parsesPercentSizeSpeedAndEtaFromDownloaderOutput() {
        val progress = requireNotNull(YtDlpProgressParser.parse("[download]  25.0% of    4.00MiB at    1.50MiB/s ETA 00:02"))
        assertEquals(25, progress.percent)
        assertEquals(1L * 1024 * 1024, progress.receivedBytes)
        assertEquals(4L * 1024 * 1024, progress.totalBytes)
        assertEquals(1_572_864L, progress.bytesPerSecond)
        assertEquals(2_000L, progress.remainingMillis)
    }

    @Test
    fun parsesCompletedLineWithoutEta() {
        val progress = YtDlpProgressParser.parse("[download] 100% of 1.00MiB in 00:01 at 1.00MiB/s")
        assertEquals(100, progress?.percent)
        assertEquals(1L * 1024 * 1024, progress?.receivedBytes)
        assertEquals(0L, progress?.remainingMillis)
    }
}
