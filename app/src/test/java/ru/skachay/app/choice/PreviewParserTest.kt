package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewParserTest {
    @Test
    fun offersWebmAudioFormats() {
        val json = """{"title":"Видео","formats":[{"format_id":"251","ext":"webm","vcodec":"none","acodec":"opus","abr":128}]}"""
        val formats = requireNotNull(PreviewParser.parse(json)).formats
        assertEquals(1, formats.size)
        assertEquals("251", formats.single().id)
        assertEquals("opus", formats.single().audioCodec)
    }

    @Test
    fun describesRealVideoAndAudioFormatsAndRejectsUnsafeIds() {
        val json = """{"title":"Пример","uploader":"Автор","duration":75,"thumbnail":"https://i.ytimg.com/preview.jpg","formats":[
          {"format_id":"137","ext":"mp4","vcodec":"avc1.640028","acodec":"none","height":1080,"fps":30,"filesize":1234567},
          {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","abr":128,"language":"ru","format_note":"Russian, DRC"},
          {"format_id":"bad+selector","ext":"mp4","vcodec":"avc1","height":720},
          {"format_id":"storyboard","ext":"mhtml","vcodec":"none","acodec":"none"}
        ]}"""
        val preview = requireNotNull(PreviewParser.parse(json))
        assertEquals("Пример", preview.title)
        assertEquals("Автор", preview.author)
        assertEquals(75, preview.durationSeconds)
        assertEquals(listOf("137", "140"), preview.formats.map { it.id })
        assertFalse(preview.formats.first().hasAudio)
        assertEquals("ru", preview.formats.last().language)
        org.junit.Assert.assertTrue(preview.formats.last().isVolumeBalanced)
    }

    @Test
    fun rejectsMalformedOrOversizedMetadata() {
        assertNull(PreviewParser.parse("not-json"))
        assertNull(PreviewParser.parse("x".repeat(PreviewParser.MAX_JSON_CHARS + 1)))
        val multibyteCaption = "é".repeat(PreviewParser.MAX_JSON_CHARS / 2 + 1)
        assertNull(PreviewParser.parse("""{"automatic_captions":{"ru":"$multibyteCaption"},"formats":[]}"""))
    }

    @Test
    fun ignoresLargeUnrelatedCaptionData() {
        val captions = "x".repeat(1_000_000)
        val json =
            """
            {
              "automatic_captions": {"unused": "$captions"},
              "title": "Видео",
              "formats": [{"format_id": "18", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a"}]
            }
            """.trimIndent()
        val preview = requireNotNull(PreviewParser.parse(json))
        assertEquals("Видео", preview.title)
        assertEquals("18", preview.formats.single().id)
    }

    @Test
    fun preservesSafeDirectStreamUrlsAndExtractorHeaders() {
        val json =
            """
            {
              "title": "Видео",
              "formats": [{
                "format_id": "137",
                "ext": "mp4",
                "vcodec": "avc1",
                "acodec": "none",
                "url": "https://rr1.googlevideo.com/video?sig=abc",
                "http_headers": {"Referer": "https://www.youtube.com/", "User-Agent": "Skachay test"}
              }]
            }
            """.trimIndent()
        val format = requireNotNull(PreviewParser.parse(json)).formats.single()
        assertEquals("https://rr1.googlevideo.com/video?sig=abc", format.directUrl)
        assertEquals("https://www.youtube.com/", format.httpHeaders["Referer"])
        assertEquals("Skachay test", format.httpHeaders["User-Agent"])
    }

    @Test
    fun ignoresUnsafeDirectStreamUrlsAndHeaders() {
        val json =
            """
            {
              "title": "Видео",
              "formats": [{
                "format_id": "137",
                "ext": "mp4",
                "vcodec": "avc1",
                "url": "http://cdn.example/video",
                "http_headers": {"Authorization": "secret"}
              }]
            }
            """.trimIndent()
        val format = requireNotNull(PreviewParser.parse(json)).formats.single()
        assertNull(format.directUrl)
        assertEquals(emptyMap<String, String>(), format.httpHeaders)
    }

    @Test
    fun keepsOnlyNonSensitiveExtractorHeaders() {
        val json =
            """
            {
              "title": "Видео",
              "formats": [{
                "format_id": "137",
                "ext": "mp4",
                "vcodec": "avc1",
                "url": "https://rr1.googlevideo.com/video",
                "http_headers": {
                  "User-Agent": "Skachay test",
                  "Referer": "https://www.youtube.com/",
                  "Cookie": "session=secret",
                  "Authorization": "Bearer secret",
                  "Host": "evil.test"
                }
              }]
            }
            """.trimIndent()
        val headers = requireNotNull(PreviewParser.parse(json)).formats.single().httpHeaders
        assertEquals(mapOf("User-Agent" to "Skachay test", "Referer" to "https://www.youtube.com/"), headers)
    }

    @Test
    fun rejectsPrivateAddressesAndNonstandardPortsForRemoteMediaUrls() {
        val json = """{"title":"Видео","thumbnail":"https://127.0.0.1/private.jpg","formats":[
          {"format_id":"private","ext":"mp4","vcodec":"avc1","url":"https://192.168.1.5/video"},
          {"format_id":"port","ext":"mp4","vcodec":"avc1","url":"https://cdn.example:8443/video"}
        ]}"""
        val preview = requireNotNull(PreviewParser.parse(json))
        assertNull(preview.thumbnailUrl)
        assertEquals(2, preview.formats.size)
        assertTrue(preview.formats.all { it.directUrl == null })
    }
}
