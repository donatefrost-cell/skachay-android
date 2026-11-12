package ru.skachay.app.updates

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateDownloaderInstrumentedTest {
    @Test
    fun downloadVerifiesDigestAndInstalledSignatureWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installedApk = File(context.applicationInfo.sourceDir)
        val version = ReleaseStore.currentVersion(context)
        val updateFile = File(context.cacheDir, "updates/update.apk")
        updateFile.delete()
        val downloader = UpdateDownloader(context) { _, target -> installedApk.copyTo(target, overwrite = true) }
        val digest = ArchiveValidation.sha256(installedApk)
        val release =
            ReleaseInfo(
                "v0.3.8",
                version,
                "https://github.com/donatefrost-cell/skachay-android/releases/download/v0.3.8/app-release.apk",
                digest,
            )
        assertTrue(downloader.validateArchive(installedApk, version))
        assertFalse(downloader.validateArchive(installedApk, version + 1))
        try {
            try {
                downloader.download(release.copy(sha256 = "0".repeat(64)))
                fail("A mismatched APK digest must be rejected")
            } catch (_: InvalidUpdateException) {
                assertFalse(updateFile.exists())
            }
            val uri = downloader.download(release)
            assertEquals("content", uri.scheme)
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                assertEquals('P'.code, input.read())
                assertEquals('K'.code, input.read())
            }
        } finally {
            updateFile.delete()
        }
    }
}
