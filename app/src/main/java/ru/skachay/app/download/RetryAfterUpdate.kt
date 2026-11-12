package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLException

object RetryAfterUpdate {
    fun <T> run(
        operation: () -> T,
        update: () -> Unit,
    ): T =
        try {
            operation()
        } catch (_: YoutubeDLException) {
            update()
            operation()
        }
}
