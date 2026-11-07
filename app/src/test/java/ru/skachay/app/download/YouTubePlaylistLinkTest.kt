package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistLinkTest {
    @Test
    fun playlistPromptAppliesOnlyToPurePlaylistUrls() {
        assertTrue(YouTubePlaylistLink.isPurePlaylist("https://www.youtube.com/playlist?list=PLabc"))
        assertTrue(YouTubePlaylistLink.isPurePlaylist("https://youtube.com/watch?list=PLabc"))
        assertTrue(YouTubePlaylistLink.isPurePlaylist("https://youtube.com/embed/videoseries?list=PLabc"))
        assertFalse(YouTubePlaylistLink.isPurePlaylist("https://www.youtube.com/watch?v=video123&list=PLabc"))
        assertFalse(YouTubePlaylistLink.isPurePlaylist("https://youtu.be/video123?list=PLabc"))
        assertFalse(YouTubePlaylistLink.isPurePlaylist("https://youtube.com/shorts/video123?list=PLabc"))
        assertFalse(YouTubePlaylistLink.isPurePlaylist("https://youtube.com/watch?v=video123"))
    }
}
