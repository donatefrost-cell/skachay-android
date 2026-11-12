package ru.skachay.app.updates

import java.io.File
import java.security.MessageDigest

internal object ArchiveValidation {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun matchesSha256(
        actualHex: String,
        expected: String,
    ): Boolean {
        val actual = actualHex.lowercase().takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: return false
        val expectedHex =
            expected
                .removePrefix("sha256:")
                .lowercase()
                .takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: return false
        return actual == expectedHex
    }

    fun matches(
        expectedPackage: String,
        archivePackage: String,
        expectedVersion: Int,
        archiveVersion: Int,
        installedSigners: List<String>,
        archiveSigners: List<String>,
    ): Boolean =
        expectedPackage == archivePackage && expectedVersion == archiveVersion &&
            installedSigners.isNotEmpty() && installedSigners.toSet() == archiveSigners.toSet()
}
