package ru.skachay.app.diagnostics

import kotlin.math.roundToLong

internal data class TransferSnapshot(
    val bytesReceived: Long,
    val totalBytes: Long?,
    val firstByteMillis: Long?,
    val averageBytesPerSecond: Long?,
    val remainingMillis: Long?,
)

internal class TransferStats(private val now: () -> Long) {
    private var startedAt: Long? = null
    private var firstByteAt: Long? = null
    private var bytesBeforeStream = 0L
    private var totalBeforeStream = 0L
    private var lastStreamReceived = 0L
    private var lastStreamTotal: Long? = null
    private var latest = TransferSnapshot(0, null, null, null, null)

    @Synchronized
    fun start() {
        startedAt = now()
        firstByteAt = null
        bytesBeforeStream = 0
        totalBeforeStream = 0
        lastStreamReceived = 0
        lastStreamTotal = null
        latest = TransferSnapshot(0, null, null, null, null)
    }

    @Synchronized
    fun update(received: Long, total: Long?): TransferSnapshot {
        val current = now()
        val start = startedAt ?: current.also { startedAt = it }
        val safeStreamReceived = received.coerceAtLeast(0)
        if (safeStreamReceived < lastStreamReceived) {
            bytesBeforeStream += lastStreamReceived
            totalBeforeStream += lastStreamTotal ?: 0
            lastStreamReceived = 0
            lastStreamTotal = null
        }
        lastStreamReceived = safeStreamReceived
        if (total != null && total >= 0) lastStreamTotal = total
        val safeReceived = bytesBeforeStream + safeStreamReceived
        val validTotal = total?.takeIf { it >= 0 }
        val combinedTotal = if (validTotal != null || totalBeforeStream > 0) totalBeforeStream + (validTotal ?: 0) else null
        if (safeReceived > 0 && firstByteAt == null) firstByteAt = current
        val elapsed = (current - start).coerceAtLeast(0)
        val average = if (elapsed > 0 && safeReceived > 0) (safeReceived.toDouble() * 1000 / elapsed).roundToLong() else null
        val remaining = combinedTotal?.takeIf { it > safeReceived && average != null && average > 0 }
            ?.let { (((it - safeReceived).toDouble() * 1000) / requireNotNull(average)).roundToLong() }
        latest = TransferSnapshot(
            bytesReceived = safeReceived,
            totalBytes = combinedTotal,
            firstByteMillis = firstByteAt?.let { (it - start).coerceAtLeast(0) },
            averageBytesPerSecond = average,
            remainingMillis = remaining,
        )
        return latest
    }

    @Synchronized
    fun markFirstByte() {
        if (firstByteAt == null) {
            val start = startedAt ?: now().also { startedAt = it }
            val firstByte = now()
            firstByteAt = firstByte
            latest = latest.copy(firstByteMillis = (firstByte - start).coerceAtLeast(0))
        }
    }

    @Synchronized
    fun snapshot(): TransferSnapshot = latest
}
