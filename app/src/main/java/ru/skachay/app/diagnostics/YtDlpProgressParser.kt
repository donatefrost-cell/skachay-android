package ru.skachay.app.diagnostics

import kotlin.math.roundToLong
import kotlin.math.pow

internal data class YtDlpProgress(
    val percent: Int,
    val receivedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long?,
    val remainingMillis: Long?,
)

internal object YtDlpProgressParser {
    private val ansi = Regex("\\u001B\\[[;\\d]*m")
    private val percentPattern = Regex("\\[download\\]\\s+([\\d.]+)%")
    private val sizePattern = Regex("\\bof\\s+~?\\s*([\\d.]+)\\s*([KMGT]?i?B)\\b")
    private val speedPattern = Regex("\\bat\\s+([\\d.]+)\\s*([KMGT]?i?B)/s\\b")
    private val etaPattern = Regex("\\bETA\\s+(\\d{2}:\\d{2}(?::\\d{2})?)")

    fun parse(rawLine: String): YtDlpProgress? {
        val line = rawLine.replace(ansi, "")
        val percent = percentPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val size = sizePattern.find(line)?.groupValues ?: return null
        val total = toBytes(size[1], size[2]) ?: return null
        val bytesPerSecond = speedPattern.find(line)?.groupValues?.let { toBytes(it[1], it[2]) }
        val received = (total.toDouble() * percent / 100).roundToLong().coerceAtLeast(0)
        val remaining = etaPattern.find(line)?.groupValues?.get(1)?.let(::parseEta)
            ?: if (percent >= 100) 0L else if (bytesPerSecond != null && bytesPerSecond > 0) {
                (((total - received).coerceAtLeast(0).toDouble() * 1000) / bytesPerSecond).roundToLong()
            } else null
        return YtDlpProgress(
            percent.roundToLong().toInt().coerceIn(0, 100),
            received,
            total,
            bytesPerSecond,
            remaining,
        )
    }

    private fun toBytes(amount: String, unit: String): Long? {
        val value = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val base = if (unit.contains('i')) 1024.0 else 1000.0
        val power = when (unit.firstOrNull()?.uppercaseChar()) {
            'K' -> 1
            'M' -> 2
            'G' -> 3
            'T' -> 4
            else -> 0
        }
        return (value * base.pow(power.toDouble())).roundToLong().takeIf { it >= 0 }
    }

    private fun parseEta(value: String): Long? {
        val parts = value.split(':').map(String::toLongOrNull)
        val first = parts.getOrNull(0) ?: return null
        val second = parts.getOrNull(1) ?: return null
        val seconds = when (parts.size) {
            2 -> first * 60 + second
            3 -> first * 3600 + second * 60 + (parts.getOrNull(2) ?: return null)
            else -> return null
        }
        return seconds * 1000
    }
}
