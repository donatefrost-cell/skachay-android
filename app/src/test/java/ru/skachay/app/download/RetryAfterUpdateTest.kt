package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RetryAfterUpdateTest {
    @Test
    fun updatesOnceAndRetriesOnceAfterEngineError() {
        var attempts = 0
        var updates = 0
        val result =
            RetryAfterUpdate.run(
                operation = { if (++attempts == 1) throw YoutubeDLException("failed") else "ok" },
                update = { updates++ },
            )
        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals(1, updates)
    }

    @Test
    fun doesNotUpdateOnCancellation() {
        var updates = 0
        try {
            RetryAfterUpdate.run(operation = { throw InterruptedException() }, update = { updates++ })
            fail("Expected cancellation")
        } catch (_: InterruptedException) {
            assertEquals(0, updates)
        }
    }
}
