package ru.skachay.app.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveValidationTest {
    @Test
    fun requiresTheSamePackageVersionAndSigningCertificate() {
        assertTrue(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1002, listOf("cert"), listOf("cert")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "other.app", 1002, 1002, listOf("cert"), listOf("cert")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1001, listOf("cert"), listOf("cert")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1002, listOf("cert"), listOf("other")))
        assertFalse(ArchiveValidation.matches("ru.skachay.app", "ru.skachay.app", 1002, 1002, emptyList(), emptyList()))
    }
}
