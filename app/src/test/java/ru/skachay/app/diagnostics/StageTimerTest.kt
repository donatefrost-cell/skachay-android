package ru.skachay.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StageTimerTest {
    @Test
    fun measuresOnlyObservedStagesAndAccumulatesRepeatedPhases() {
        var clock = 0L
        val timer = StageTimer { clock }
        timer.switchTo(DownloadStage.EXTRACTION)
        clock = 50
        timer.switchTo(DownloadStage.TRANSFER)
        clock = 130
        timer.switchTo(DownloadStage.EXTRACTION)
        clock = 150
        timer.switchTo(DownloadStage.SAVING)
        clock = 180
        val result = timer.finish()
        assertEquals(70L, result[DownloadStage.EXTRACTION])
        assertEquals(80L, result[DownloadStage.TRANSFER])
        assertEquals(30L, result[DownloadStage.SAVING])
        assertNull(result[DownloadStage.MERGE])
    }

    @Test
    fun recognisesDownloadAndMergeMessagesWithoutTreatingMetadataAsTransfer() {
        assertEquals(DownloadStage.TRANSFER, stageFromYtDlpOutput("[download] Destination: video.mp4"))
        assertEquals(DownloadStage.MERGE, stageFromYtDlpOutput("[Merger] Merging formats"))
        assertNull(stageFromYtDlpOutput("[youtube] Downloading webpage"))
    }
}
