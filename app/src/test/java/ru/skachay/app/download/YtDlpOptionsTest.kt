package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit

class YtDlpOptionsTest {
    @Test
    fun appliesConfiguredNetworkRetriesToVideoAndFragments() {
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", retries = 3).buildCommand()
        for (option in listOf("--retries", "--fragment-retries", "--extractor-retries")) {
            val index = args.indexOf(option)
            assertTrue(index >= 0 && args[index + 1] == "3")
        }
    }

    @Test
    fun selectsCompatible720pWithoutEmbeddingOrReencoding() {
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/media.%(ext)s").buildCommand()
        assertTrue(args.contains("--no-playlist"))
        assertTrue(args.contains("--socket-timeout") && args[args.indexOf("--socket-timeout") + 1] == "30")
        assertTrue(args.any { it.contains("height<=720") && it.contains("avc1") })
        assertTrue(args.contains("--concurrent-fragments") && args.contains("4"))
        assertTrue(args.contains("--merge-output-format") && args.contains("mp4"))
        assertFalse(args.contains("--recode-video"))
        assertFalse(args.contains("--embed-thumbnail"))
    }

    @Test
    fun permitsFormatsWithoutHeightMetadataForInstagram() {
        val args = YtDlpOptions.request("https://www.instagram.com/reel/abc/", "/cache/media.%(ext)s").buildCommand()
        val index = args.indexOf("--format")
        assertTrue(index >= 0)
        assertFalse(args[index + 1].contains("height<=720"))
    }

    @Test
    fun bestPresetAllowsCompatibleVideoUpTo1080p() {
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/media.%(ext)s", Quality.BEST).buildCommand()
        assertTrue(args.any { it.contains("height<=1080") && it.contains("avc1") })
        assertFalse(args.any { it.contains("height<=720") })
    }

    @Test
    fun appliesVideoBitrateLimitToAutomaticYoutubeSelection() {
        val args =
            YtDlpOptions
                .request(
                    "https://youtu.be/example",
                    "/cache/media.%(ext)s",
                    videoBitrateLimit = VideoBitrateLimit.K5000,
                ).buildCommand()
        assertTrue(args.any { it.contains("vbr<=5000") })
    }

    @Test
    fun embedsVideoThumbnailOnlyWhenRequested() {
        val args =
            YtDlpOptions
                .request(
                    "https://youtu.be/example",
                    "/cache/media.%(ext)s",
                    embedThumbnail = true,
                ).buildCommand()
        assertTrue(args.contains("--embed-thumbnail"))
    }

    @Test
    fun playlistIsOptInAndCanBeDownloadedAsAudio() {
        val videoArgs =
            YtDlpOptions
                .request(
                    "https://youtube.com/playlist?list=PLabc",
                    "/cache/%(playlist_index)03d.%(ext)s",
                    allowPlaylist = true,
                ).buildCommand()
        assertTrue(videoArgs.contains("--yes-playlist"))
        assertFalse(videoArgs.contains("--no-playlist"))

        val audioArgs =
            YtDlpOptions
                .request(
                    "https://youtube.com/playlist?list=PLabc",
                    "/cache/%(playlist_index)03d.%(ext)s",
                    allowPlaylist = true,
                    playlistAudioOnly = true,
                    audioExport = AudioExport.MP3,
                    audioBitrate = AudioBitrate.K128,
                ).buildCommand()
        assertTrue(audioArgs.contains("bestaudio[ext=m4a]/bestaudio"))
        assertTrue(audioArgs.contains("--extract-audio"))
        assertTrue(audioArgs.indexOf("--audio-format").let { it >= 0 && audioArgs[it + 1] == "mp3" })
        assertTrue(audioArgs.indexOf("--audio-quality").let { it >= 0 && audioArgs[it + 1] == "128K" })
    }

    @Test
    fun videoLinkInsideAPlaylistStillGetsNoPlaylistOption() {
        val args =
            YtDlpOptions
                .request(
                    "https://www.youtube.com/watch?v=video123&list=PLabc",
                    "/cache/video.%(ext)s",
                ).buildCommand()

        assertTrue(args.contains("--no-playlist"))
        assertFalse(args.contains("--yes-playlist"))
    }

    @Test
    fun usesTheExtractedInfoAndSelectedFormatWithoutExtractingTheUrlAgain() {
        val selected = AvailableFormat("137", "mp4", "avc1", null, 1080, 30, null, null)
        val args =
            YtDlpOptions
                .request(
                    "https://youtu.be/example",
                    "/cache/video.%(ext)s",
                    Quality.AUTO,
                    infoJson = "/cache/info.json",
                    selected = selected,
                ).buildCommand()
        assertTrue(args.contains("--load-info-json") && args.contains("/cache/info.json"))
        assertTrue(args.contains("137+bestaudio[ext=m4a]/137+bestaudio"))
        assertFalse(args.contains("https://youtu.be/example"))
    }

    @Test
    fun freshFallbackUsesTheVideoUrlAndKeepsTheSelectedFormat() {
        val selected = AvailableFormat("137", "mp4", "avc1", null, 1080, 30, null, null)
        val args =
            YtDlpOptions
                .request(
                    "https://youtu.be/example",
                    "/cache/video.%(ext)s",
                    selected = selected,
                ).buildCommand()

        assertTrue(args.contains("https://youtu.be/example"))
        assertFalse(args.contains("--load-info-json"))
        assertTrue(args.contains("137+bestaudio[ext=m4a]/137+bestaudio"))
    }

    @Test
    fun preservesAudioOnlyAndVideoWithoutAudioSelections() {
        val video = AvailableFormat("137", "mp4", "avc1", null, 1080, 30, null, null)
        val audio = AvailableFormat("140", "m4a", null, "mp4a", null, null, 128, null)
        val withoutAudio =
            YtDlpOptions
                .request(
                    "https://youtu.be/example",
                    "/cache/out.%(ext)s",
                    selected = video,
                    videoOnly = true,
                ).buildCommand()
        val audioOnly = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", selected = audio).buildCommand()
        assertTrue(withoutAudio.contains("137"))
        assertTrue(audioOnly.contains("140"))
        assertFalse(withoutAudio.any { it.contains("bestaudio") })
        assertFalse(audioOnly.any { it.contains("bestaudio") })
    }

    @Test
    fun remuxesSelectedWebmAudioIntoAnAndroidCompatibleAudioContainer() {
        val audio = AvailableFormat("251", "webm", null, "opus", null, null, 128, null)
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", selected = audio).buildCommand()
        assertTrue(args.contains("--extract-audio"))
        assertTrue(args.indexOf("--audio-format").let { it >= 0 && args[it + 1] == "best" })
    }
}
