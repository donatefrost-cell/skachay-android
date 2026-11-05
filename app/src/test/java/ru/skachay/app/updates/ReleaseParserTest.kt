package ru.skachay.app.updates

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {
    private fun release(tag: String = "v1.2.3", url: String = "https://github.com/donatefrost-cell/skachay-android/releases/download/v1.2.3/app-release.apk"): JSONObject =
        JSONObject("""{"tag_name":"$tag","draft":false,"prerelease":false,"assets":[{"name":"app-release.apk","browser_download_url":"$url"}]}""")

    @Test
    fun acceptsOnlyNewerStableReleasesWithTheProjectApk() {
        val parsed = ReleaseParser.parse(release().toString(), currentVersion = 1_002_002)
        assertEquals("v1.2.3", parsed?.tag)
        assertEquals(1_002_003, parsed?.versionCode)
        assertNull(ReleaseParser.parse(release().toString(), currentVersion = 1_002_003))
        assertNull(ReleaseParser.parse(release().put("prerelease", true).toString(), currentVersion = 1))
    }

    @Test
    fun rejectsUntrustedDownloadAddressesAndInvalidVersions() {
        assertNull(ReleaseParser.parse(release(url = "https://example.com/app-release.apk").toString(), currentVersion = 1))
        assertNull(ReleaseParser.parse(release(tag = "v1.1000").toString(), currentVersion = 1))
        assertNull(ReleaseParser.parse(release(url = "https://github.com/other/repo/releases/download/v1.2.3/app-release.apk").toString(), currentVersion = 1))
    }
}
