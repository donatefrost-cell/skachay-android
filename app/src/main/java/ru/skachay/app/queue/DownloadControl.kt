package ru.skachay.app.queue

import java.io.InterruptedIOException

internal class DownloadControl {
    private val lock = Object()
    @Volatile private var paused = false
    @Volatile private var cancelled = false

    fun pause() {
        paused = true
    }

    fun resume() {
        synchronized(lock) {
            paused = false
            lock.notifyAll()
        }
    }

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            paused = false
            lock.notifyAll()
        }
    }

    fun isPaused(): Boolean = paused
    fun isCancelled(): Boolean = cancelled

    fun checkpoint() {
        synchronized(lock) {
            while (paused && !cancelled) {
                try {
                    lock.wait()
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Download interrupted").apply { initCause(interrupted) }
                }
            }
            if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedIOException("Download cancelled")
        }
    }
}
