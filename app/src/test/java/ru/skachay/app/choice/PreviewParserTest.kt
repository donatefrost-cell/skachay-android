package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewParserTest {
    @Test
    fun offersWebmAudioFormats() {
        val json = """{"title":"Видео","formats":[{"format_id":"251","ext":"webm","vcodec":"none","acodec":"opus","abr":128}]}"""
        val formats = PreviewParser.parse(json)!!.formats
        assertEquals(1, formats.size)
        assertEquals("251", formats.single().id)
        assertEquals("opus", formats.single().audioCodec)
    }

    @Test
    fun describesRealVideoAndAudioFormatsAndRejectsUnsafeIds() {
        val json = """{"title":"Пример","uploader":"Автор","duration":75,"thumbnail":"https://img.example/preview.jpg","formats":[
          {"format_id":"137","ext":"mp4","vcodec":"avc1.640028","acodec":"none","height":1080,"fps":30,"filesize":1234567},
          {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","abr":128,"language":"ru","format_note":"Russian, DRC"},
          {"format_id":"bad+selector","ext":"mp4","vcodec":"avc1","height":720},
          {"format_id":"storyboard","ext":"mhtml","vcodec":"none","acodec":"none"}
        ]}"""
        val preview = PreviewParser.parse(json)!!
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
    }

    @Test
    fun ignoresLargeUnrelatedCaptionData() {
        val captions = "x".repeat(1_000_000)
        val json = """{"automatic_captions":{"unused":"$captions"},"title":"Видео","formats":[{"format_id":"18","ext":"mp4","vcodec":"avc1","acodec":"mp4a"}]}"""
        val preview = PreviewParser.parse(json)!!
        assertEquals("Видео", preview.title)
        assertEquals("18", preview.formats.single().id)
    }

    @Test
    fun preservesSafeDirectStreamUrlsAndExtractorHeaders() {
        val json = """{"title":"Видео","formats":[{"format_id":"137","ext":"mp4","vcodec":"avc1","acodec":"none","url":"https://cdn.example/video?sig=abc","http_headers":{"Referer":"https://www.youtube.com/","User-Agent":"Skachay test"}}]}"""
        val format = PreviewParser.parse(json)!!.formats.single()
        assertEquals("https://cdn.example/video?sig=abc", format.directUrl)
        assertEquals("https://www.youtube.com/", format.httpHeaders["Referer"])
        assertEquals("Skachay test", format.httpHeaders["User-Agent"])
    }

    @Test
    fun ignoresUnsafeDirectStreamUrlsAndHeaders() {
        val json = """{"title":"Видео","formats":[{"format_id":"137","ext":"mp4","vcodec":"avc1","url":"http://cdn.example/video","http_headers":{"Authorization":"secret"}}]}"""
        val format = PreviewParser.parse(json)!!.formats.single()
        assertNull(format.directUrl)
        assertEquals(emptyMap<String, String>(), format.httpHeaders)
    }
}
