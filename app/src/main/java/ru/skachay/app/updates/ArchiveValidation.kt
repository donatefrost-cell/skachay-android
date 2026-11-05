package ru.skachay.app.updates

internal object ArchiveValidation {
    fun matches(
        expectedPackage: String,
        archivePackage: String,
        expectedVersion: Int,
        archiveVersion: Int,
        installedSigners: List<String>,
        archiveSigners: List<String>,
    ): Boolean = expectedPackage == archivePackage && expectedVersion == archiveVersion &&
        installedSigners.isNotEmpty() && installedSigners.toSet() == archiveSigners.toSet()
}
