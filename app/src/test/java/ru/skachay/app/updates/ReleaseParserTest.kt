package ru.skachay.app.updates

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {
    private fun release(
        tag: String = "v1.2.3",
        url: String = "https://github.com/donatefrost-cell/skachay-android/releases/download/v1.2.3/app-release.apk",
        digest: String? = "sha256:" + "a".repeat(64),
    ): JSONObject {
        val asset = JSONObject().put("name", "app-release.apk").put("browser_download_url", url)
        digest?.let { asset.put("digest", it) }
        return JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", false)
            .put("assets", org.json.JSONArray().put(asset))
    }

    @Test
    fun acceptsOnlyNewerStableReleasesWithTheProjectApk() {
        val parsed = ReleaseParser.parse(release().toString(), currentVersion = 1_002_002)
        assertEquals("v1.2.3", parsed?.tag)
        assertEquals(1_002_003, parsed?.versionCode)
        assertEquals("a".repeat(64), parsed?.sha256)
        assertNull(ReleaseParser.parse(release().toString(), currentVersion = 1_002_003))
        assertNull(ReleaseParser.parse(release().put("prerelease", true).toString(), currentVersion = 1))
    }

    @Test
    fun rejectsUntrustedDownloadAddressesAndInvalidVersions() {
        assertNull(ReleaseParser.parse(release(url = "https://example.com/app-release.apk").toString(), currentVersion = 1))
        assertNull(ReleaseParser.parse(release(tag = "v1.1000").toString(), currentVersion = 1))
        assertNull(ReleaseParser.parse(release(url = "https://github.com/other/repo/releases/download/v1.2.3/app-release.apk").toString(), currentVersion = 1))
        assertNull(ReleaseParser.parse(release(digest = null).toString(), currentVersion = 1))
        assertNull(ReleaseParser.parse(release(digest = "sha256:1234").toString(), currentVersion = 1))
    }
}
