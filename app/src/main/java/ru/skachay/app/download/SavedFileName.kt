package ru.skachay.app.download

import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

internal object SavedFileName {
    private const val MAX_BASE_BYTES = 160
    private const val MAX_INPUT_CHARS = 4096
    private const val FORBIDDEN = "/\\:*?\"<>|"

    fun clean(input: String?): String? {
        if (input == null) return null
        val text = Normalizer.normalize(input.take(MAX_INPUT_CHARS), Normalizer.Form.NFC)
        val result = StringBuilder()
        var offset = 0
        var bytes = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT.toInt() ||
                codePoint in 0xd800..0xdfff
            ) {
                continue
            }
            val part = if (codePoint < 128 && FORBIDDEN.indexOf(codePoint.toChar()) >= 0) "_" else String(Character.toChars(codePoint))
            val size = part.toByteArray(Charsets.UTF_8).size
            if (bytes + size > MAX_BASE_BYTES) break
            result.append(part)
            bytes += size
        }
        val cleaned = result.toString().trim { it == '.' || it.isWhitespace() }.takeIf { it.isNotEmpty() } ?: return null
        val firstExtensionSeparator = cleaned.indexOf('.').takeIf { it > 0 } ?: cleaned.length
        val deviceName = cleaned.substring(0, firstExtensionSeparator).trimEnd().uppercase(Locale.ROOT)
        return if (deviceName in RESERVED_DEVICE_NAMES) {
            cleaned.substring(0, firstExtensionSeparator).trimEnd() + "_" + cleaned.substring(firstExtensionSeparator)
        } else {
            cleaned
        }
    }

    fun displayName(
        suggestion: String?,
        extension: String,
        itemNumber: Int? = null,
    ): String? {
        require(extension.matches(Regex("[a-z0-9]{1,8}")))
        require(itemNumber == null || itemNumber > 0)
        var base = clean(suggestion) ?: return null
        if (base.endsWith(".$extension", ignoreCase = true)) base = clean(base.dropLast(extension.length + 1)) ?: return null
        return base + (itemNumber?.let { " ($it)" } ?: "") + ".$extension"
    }

    fun reserve(
        folder: File,
        name: String,
    ): File {
        require(folder.isDirectory && '/' !in name && '\\' !in name && name !in setOf("", ".", ".."))
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val extension = name.substring(dot)
        for (attempt in 0 until 10_000) {
            val candidate = File(folder, if (attempt == 0) name else "$base ($attempt)$extension")
            if (candidate.createNewFile()) return candidate
        }
        throw IOException("Could not reserve a unique file name")
    }

    private val RESERVED_DEVICE_NAMES =
        buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            (1..9).forEach { number ->
                add("COM$number")
                add("LPT$number")
            }
        }
}
