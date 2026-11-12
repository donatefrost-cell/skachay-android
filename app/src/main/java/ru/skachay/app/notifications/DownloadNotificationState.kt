package ru.skachay.app.notifications

import java.nio.ByteBuffer
import java.security.MessageDigest

internal data class DownloadNotificationSnapshot<T>(
    val foregroundId: Int?,
    val active: Map<Int, T>,
    val totalCount: Int,
    val detachForeground: Boolean = false,
    val completed: Pair<Int, T>? = null,
) {
    val showSummary: Boolean get() = active.size > 1
}

/** Keeps the foreground row and per-download rows on the same stable notification IDs. */
internal class DownloadNotificationState<T> {
    private val active = linkedMapOf<Int, T>()
    private var foregroundId: Int? = null
    private var totalCount = 0

    @Synchronized
    fun start(
        id: Int,
        notification: T,
    ): DownloadNotificationSnapshot<T> = startIfAbsent(id, notification) ?: currentSnapshot()

    @Synchronized
    fun startIfAbsent(
        id: Int,
        notification: T,
    ): DownloadNotificationSnapshot<T>? {
        if (id in active) return null
        active[id] = notification
        totalCount++
        if (foregroundId == null) foregroundId = id
        return currentSnapshot()
    }

    @Synchronized
    fun update(
        id: Int,
        notification: T,
    ): DownloadNotificationSnapshot<T> {
        if (id in active) active[id] = notification
        return currentSnapshot()
    }

    @Synchronized
    fun complete(
        id: Int,
        notification: T,
    ): DownloadNotificationSnapshot<T> {
        if (active.remove(id) == null) return currentSnapshot(completed = id to notification)
        val wasForeground = foregroundId == id
        if (wasForeground) foregroundId = active.keys.firstOrNull()
        return currentSnapshot(detachForeground = wasForeground, completed = id to notification)
    }

    @Synchronized
    fun discard(id: Int): DownloadNotificationSnapshot<T> {
        if (active.remove(id) == null) return currentSnapshot()
        val wasForeground = foregroundId == id
        if (wasForeground) foregroundId = active.keys.firstOrNull()
        return currentSnapshot(detachForeground = wasForeground)
    }

    @Synchronized
    fun contains(id: Int): Boolean = id in active

    @Synchronized
    fun currentSnapshot(
        detachForeground: Boolean = false,
        completed: Pair<Int, T>? = null,
    ): DownloadNotificationSnapshot<T> =
        DownloadNotificationSnapshot(
            foregroundId = foregroundId,
            active = active.toMap(),
            totalCount = totalCount,
            detachForeground = detachForeground,
            completed = completed,
        )
}

internal object DownloadNotificationId {
    const val MAX_DOWNLOAD_ID = 0x3FFF_FFFF

    fun from(identity: String): Int {
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val candidate = ByteBuffer.wrap(bytes, 0, Int.SIZE_BYTES).int and MAX_DOWNLOAD_ID
        return candidate.coerceAtLeast(2)
    }
}
