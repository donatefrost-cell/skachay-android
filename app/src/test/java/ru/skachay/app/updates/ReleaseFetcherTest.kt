package ru.skachay.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFetcherTest {
    private val releaseJson = """{"tag_name":"v1.0","draft":false,"prerelease":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://github.com/donatefrost-cell/skachay-android/releases/download/v1.0/app-release.apk","digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}"""

    @Test
    fun readsAvailableReleaseAndReportsUnavailableOrCurrentVersions() {
        val found = ReleaseFetcher { releaseJson }.check(1_000)
        assertEquals("v1.0", (found as ReleaseCheck.Found).release.tag)
        assertTrue(ReleaseFetcher { null }.check(1_000) is ReleaseCheck.Unavailable)
        assertTrue(ReleaseFetcher { releaseJson }.check(1_000_000) is ReleaseCheck.UpToDate)
    }

    @Test
    fun metadataEndpointIsPinnedToGitHubApi() {
        assertTrue(ReleaseFetcher.isAllowedEndpoint(ReleaseFetcher.API_URL))
        assertFalse(ReleaseFetcher.isAllowedEndpoint(ReleaseFetcher.API_URL.replace("https://", "http://")))
        assertFalse(ReleaseFetcher.isAllowedEndpoint(ReleaseFetcher.API_URL.replace("api.github.com", "api.github.com.evil.example")))
        assertFalse(ReleaseFetcher.isAllowedEndpoint(ReleaseFetcher.API_URL + "?url=https://example.com"))
        assertFalse(ReleaseFetcher.isAllowedEndpoint("https://user@api.github.com/repos/donatefrost-cell/skachay-android/releases/latest"))
    }
}
