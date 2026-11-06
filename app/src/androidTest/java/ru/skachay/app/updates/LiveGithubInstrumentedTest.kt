package ru.skachay.app.updates

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.recipes.RecipeFetcher

@RunWith(AndroidJUnit4::class)
class LiveGithubInstrumentedTest {
    @Test
    fun publicRecipeCanBeFetchedByTheApp() {
        requireLiveTest()
        val document = RecipeFetcher().fetch()
        assertNotNull("Published rules should be accessible without authentication", document)
        assertTrue(document!!.recipes.revision > 0)
    }

    @Test
    fun publicReleaseCanBeDiscoveredByTheApp() {
        requireLiveTest()
        val result = ReleaseFetcher().check(0)
        assertTrue("A public signed release should be discoverable", result is ReleaseCheck.Found)
        result as ReleaseCheck.Found
        assertTrue(result.release.downloadUrl.endsWith("/app-release.apk"))
    }

    private fun requireLiveTest() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveGithub") == "1")
    }
}
