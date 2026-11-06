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
}
