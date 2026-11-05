package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewParserTest {
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
}
