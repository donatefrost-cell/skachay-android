package ru.skachay.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationStateTest {
    @Test
    fun firstActiveDownloadOwnsTheForegroundNotification() {
        val state = DownloadNotificationState<String>()

        val snapshot = state.start(101, "first")

        assertEquals(101, snapshot.foregroundId)
        assertEquals(mapOf(101 to "first"), snapshot.active)
        assertEquals(1, snapshot.totalCount)
        assertFalse(snapshot.showSummary)
    }

    @Test
    fun duplicateActiveRequestDoesNotReplaceItsNotification() {
        val state = DownloadNotificationState<String>()
        state.start(101, "first request")

        val duplicate = state.startIfAbsent(101, "duplicate request")

        assertNull(duplicate)
        assertEquals("first request", state.currentSnapshot().active[101])
    }

    @Test
    fun parallelDownloadsKeepOneForegroundAndNeedOneSummary() {
        val state = DownloadNotificationState<String>()
        state.start(101, "first")

        val snapshot = state.start(202, "second")

        assertEquals(101, snapshot.foregroundId)
        assertEquals(mapOf(101 to "first", 202 to "second"), snapshot.active)
        assertEquals(2, snapshot.totalCount)
        assertTrue(snapshot.showSummary)
    }

    @Test
    fun progressReplacesTheCurrentPayloadWithoutChangingItsId() {
        val state = DownloadNotificationState<String>()
        state.start(101, "0%")

        val snapshot = state.update(101, "64%")

        assertEquals(101, snapshot.foregroundId)
        assertEquals("64%", snapshot.active[101])
    }

    @Test
    fun completingForegroundPromotesAnotherActiveDownloadAndKeepsFinalPayload() {
        val state = DownloadNotificationState<String>()
        state.start(101, "first progress")
        state.start(202, "second progress")

        val snapshot = state.complete(101, "first done")

        assertTrue(snapshot.detachForeground)
        assertEquals(202, snapshot.foregroundId)
        assertEquals("first done", snapshot.completed?.second)
        assertEquals(mapOf(202 to "second progress"), snapshot.active)
        assertEquals(2, snapshot.totalCount)
        assertFalse(snapshot.showSummary)
    }

    @Test
    fun completingLastDownloadDetachesForegroundAndLeavesFinalNotification() {
        val state = DownloadNotificationState<String>()
        state.start(101, "progress")

        val snapshot = state.complete(101, "done")

        assertTrue(snapshot.detachForeground)
        assertNull(snapshot.foregroundId)
        assertEquals("done", snapshot.completed?.second)
        assertTrue(snapshot.active.isEmpty())
        assertFalse(snapshot.showSummary)
    }

    @Test
    fun discardedDuplicateDoesNotLeaveACompletionNotification() {
        val state = DownloadNotificationState<String>()
        state.start(101, "first")
        state.start(202, "duplicate")

        val snapshot = state.discard(202)

        assertEquals(101, snapshot.foregroundId)
        assertEquals(mapOf(101 to "first"), snapshot.active)
        assertNull(snapshot.completed)
        assertFalse(snapshot.showSummary)
    }

    @Test
    fun stableIdsDependOnRequestAndAvoidSummaryId() {
        val first = DownloadNotificationId.from("youtube|video-1|720p")

        assertEquals(first, DownloadNotificationId.from("youtube|video-1|720p"))
        assertNotEquals(first, DownloadNotificationId.from("youtube|video-1|1080p"))
        assertTrue(first > 1)
        assertTrue(first < DownloadNotificationId.MAX_DOWNLOAD_ID)
    }
}
