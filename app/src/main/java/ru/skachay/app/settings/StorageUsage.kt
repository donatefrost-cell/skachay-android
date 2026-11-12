package ru.skachay.app.settings

import java.io.File
import java.nio.file.Files

internal object StorageUsage {
    fun bytes(folder: File): Long {
        if (!folder.isDirectory || Files.isSymbolicLink(folder.toPath())) return 0
        var total = 0L
        folder.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
            if (!Files.isSymbolicLink(file.toPath()) && file.isFile) {
                val size = file.length().coerceAtLeast(0)
                total = if (Long.MAX_VALUE - total < size) Long.MAX_VALUE else total + size
            }
        }
        return total
    }

    fun clearCache(folder: File): Boolean {
        return try {
            if (!folder.exists()) return true
            if (!folder.isDirectory || Files.isSymbolicLink(folder.toPath())) return false
            val entries = folder.listFiles() ?: return false
            var cleared = true
            entries.forEach { entry ->
                if (entry.name.startsWith("job-") && !Files.isSymbolicLink(entry.toPath())) return@forEach
                cleared = deleteWithoutFollowingLinks(entry) && cleared
            }
            cleared
        } catch (_: SecurityException) {
            false
        }
    }

    private fun deleteWithoutFollowingLinks(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) {
            return try {
                file.delete() || !Files.isSymbolicLink(file.toPath())
            } catch (_: SecurityException) {
                false
            }
        }
        if (!file.exists()) return true
        if (!file.isDirectory) return file.delete() || !file.exists()

        var cleared = true
        val children = file.listFiles() ?: return false
        children.forEach { child -> cleared = deleteWithoutFollowingLinks(child) && cleared }
        val directoryDeleted = file.delete() || !file.exists()
        return cleared && directoryDeleted
    }
}
