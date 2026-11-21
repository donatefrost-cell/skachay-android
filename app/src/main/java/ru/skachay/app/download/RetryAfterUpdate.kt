package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLException

object RetryAfterUpdate {
    fun <T> run(
        operation: () -> T,
        update: () -> Unit,
    ): T =
        try {
            operation()
        } catch (original: YoutubeDLException) {
            try {
                update()
            } catch (updateError: Exception) {
                if (updateError is InterruptedException) throw updateError
                original.addSuppressed(updateError)
                throw original
            }
            operation()
        }
}
