package ru.skachay.app.download

object MediaType {
    fun detect(header: ByteArray, url: String): String? {
        fun starts(vararg bytes: Int): Boolean =
            header.size >= bytes.size && bytes.indices.all { header[it].toInt() and 0xff == bytes[it] }
        fun textAt(offset: Int, text: String): Boolean =
            header.size >= offset + text.length && text.indices.all { header[offset + it].toInt() == text[it].code }

        return when {
            starts(0xff, 0xd8, 0xff) -> "image/jpeg"
            starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
            textAt(0, "GIF87a") || textAt(0, "GIF89a") -> "image/gif"
            textAt(0, "RIFF") && textAt(8, "WEBP") -> "image/webp"
            textAt(4, "ftyp") -> if (url.substringBefore('?').endsWith(".m4a", true)) "audio/mp4" else "video/mp4"
            starts(0x1a, 0x45, 0xdf, 0xa3) -> "video/webm"
            textAt(0, "ID3") -> "audio/mpeg"
            textAt(0, "OggS") -> "audio/ogg"
            else -> null
        }
    }
}
