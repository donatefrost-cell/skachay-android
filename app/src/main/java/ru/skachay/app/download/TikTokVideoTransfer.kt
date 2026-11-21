package ru.skachay.app.download

import java.io.File
import java.io.IOException

internal object TikTokVideoTransfer {
    fun download(
        original: ResolvedMedia,
        direct: (ResolvedMedia) -> File,
        refresh: () -> ResolvedMedia?,
        alternate: () -> File,
    ): File {
        val rejection =
            try {
                return direct(original)
            } catch (error: IOException) {
                if (!wasRejected(error) || Thread.currentThread().isInterrupted) throw error
                error
            }
        val replacement = refresh()?.takeIf { it.type == ResolvedMedia.Kind.VIDEO && it != original }
        if (replacement != null) {
            try {
                return direct(replacement)
            } catch (error: IOException) {
                if (!wasRejected(error) || Thread.currentThread().isInterrupted) {
                    error.addSuppressed(rejection)
                    throw error
                }
                rejection.addSuppressed(error)
            }
        }
        return try {
            alternate()
        } catch (error: Exception) {
            error.addSuppressed(rejection)
            throw error
        }
    }

    private fun wasRejected(error: IOException): Boolean =
        generateSequence<Throwable>(error) { it.cause }
            .take(10)
            .filterIsInstance<DirectDownloader.HttpStatusException>()
            .any { it.code == 403 || it.code == 410 }
}
