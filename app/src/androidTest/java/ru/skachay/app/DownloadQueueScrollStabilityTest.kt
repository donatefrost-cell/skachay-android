package ru.skachay.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.queue.DownloadQueueEntry
import ru.skachay.app.queue.DownloadQueueStatus

@RunWith(AndroidJUnit4::class)
class DownloadQueueScrollStabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressUpdatesDoNotChangeScrollRangeAtTheBottom() {
        val entry = mutableStateOf(progressEntry(percent = 1, bytesReceived = 1, speed = null))
        var scrollState: ScrollState? = null

        composeRule.setContent {
            val state = rememberScrollState()
            SideEffect { scrollState = state }
            SkachayTheme {
                Column(
                    Modifier
                        .width(300.dp)
                        .height(400.dp)
                        .verticalScroll(state),
                ) {
                    Spacer(Modifier.height(420.dp))
                    DownloadQueueCard(entry.value, loadThumbnail = false)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val state = requireNotNull(scrollState)
            state.dispatchRawDelta(state.maxValue.toFloat())
        }

        var initialMaxValue = -1
        composeRule.runOnIdle { initialMaxValue = requireNotNull(scrollState).maxValue }

        listOf(
            progressEntry(percent = 54, bytesReceived = 19L * 1024 * 1024, speed = 2L * 1024 * 1024),
            progressEntry(percent = 99, bytesReceived = 30L * 1024 * 1024, speed = 8L * 1024 * 1024),
            progressEntry(percent = 2, bytesReceived = 2, speed = null),
        ).forEach { update ->
            composeRule.runOnIdle { entry.value = update }
            composeRule.waitForIdle()
            var currentMaxValue = -1
            composeRule.runOnIdle {
                val state = requireNotNull(scrollState)
                currentMaxValue = state.maxValue
                assertEquals("The scroll position must stay at the bottom", currentMaxValue, state.value)
            }

            assertEquals("Progress text must not move the bottom scroll boundary", initialMaxValue, currentMaxValue)
        }
    }

    private fun progressEntry(
        percent: Int,
        bytesReceived: Long,
        speed: Long?,
    ) = DownloadQueueEntry(
        id = 1,
        url = "https://youtu.be/scroll-test",
        title = "Короткое видео",
        requestJson = "{}",
        status = DownloadQueueStatus.RUNNING,
        createdAt = 1,
        updatedAt = 1,
        percent = percent,
        bytesReceived = bytesReceived,
        totalBytes = 31L * 1024 * 1024,
        speedBytesPerSecond = speed,
    )
}
