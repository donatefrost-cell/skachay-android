package ru.skachay.app.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadRuntimeInstrumentedTest {
    @Test
    fun fileNameTemplateWorksWithAndroidRegex() {
        assertEquals(
            "Пример_YouTube",
            FileNameTemplate.resolve("{title}_{source}", "Пример", "https://youtu.be/example", "YouTube"),
        )
    }

    @Test
    fun knownSizeDownloadChecksSpaceOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = byteArrayOf(1, 2, 3, 4)
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val range = chain.request().header("Range") != null
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (range) 206 else 200)
                        .message("OK")
                        .apply { if (range) header("Content-Range", "bytes 0-0/4") }
                        .body((if (range) bytes.copyOf(1) else bytes).toResponseBody())
                        .build()
                }.build()
        val output = File(context.cacheDir, "space-check-test.bin")
        try {
            DirectDownloader(client).download("https://example.com/file", output, connections = 1)
            assertArrayEquals(bytes, output.readBytes())
        } finally {
            output.delete()
        }
    }

    @Test
    fun ffmpegStartsAndMergesVideoWithAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        YtDlpRuntime.initialize(context)
        val folder = File(context.cacheDir, "ffmpeg-runtime-test").apply { mkdirs() }
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

        fun run(vararg args: String) {
            val log = File(folder, "ffmpeg.log")
            val process =
                ProcessBuilder(listOf(ffmpeg.absolutePath, "-nostdin", "-y") + args)
                    .redirectErrorStream(true)
                    .redirectOutput(log)
                    .apply { environment()["LD_LIBRARY_PATH"] = FfmpegRuntime.libraryPath(context) }
                    .start()
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val output = log.takeIf(File::exists)?.readText().orEmpty()
            assertTrue("FFmpeg timed out: $output", finished)
            assertTrue("FFmpeg failed: $output", process.exitValue() == 0)
        }
        try {
            val video = File(folder, "video.mp4")
            val audio = File(folder, "audio.m4a")
            val output = File(folder, "merged.mp4")
            run("-f", "lavfi", "-i", "color=c=black:s=64x64:r=10", "-t", "0.3", "-c:v", "mpeg4", video.absolutePath)
            run("-f", "lavfi", "-i", "sine=frequency=1000", "-t", "0.3", "-c:a", "aac", audio.absolutePath)
            YouTubeDirectTransfer.merge(context, video, audio, output)
            assertTrue(output.isFile && output.length() > 0)
        } finally {
            folder.deleteRecursively()
        }
    }
}
