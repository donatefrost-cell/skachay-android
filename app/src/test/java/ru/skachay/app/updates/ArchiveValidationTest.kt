package ru.skachay.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveValidationTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun requiresTheSamePackageVersionAndSigningCertificate() {
        assertTrue(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1002, listOf("cert"), listOf("cert")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "other.app", 1002, 1002, listOf("cert"), listOf("cert")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1001, listOf("cert"), listOf("cert")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1002, listOf("cert"), listOf("other")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1002, emptyList(), emptyList()))
    }

    @Test
    fun acceptsOnlyMatchingSha256Digests() {
        val hash = "a".repeat(64)
        assertTrue(ArchiveValidation.matchesSha256(hash, "sha256:$hash"))
        assertFalse(ArchiveValidation.matchesSha256(hash, "sha256:${"b".repeat(64)}"))
        assertFalse(ArchiveValidation.matchesSha256(hash, "sha256:1234"))
        assertFalse(ArchiveValidation.matchesSha256("bad", "sha256:$hash"))
    }

    @Test
    fun computesTheArchiveDigestFromFileContents() {
        val apk = temp.newFile("update.apk").apply { writeText("hello") }
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", ArchiveValidation.sha256(apk))
    }
}
