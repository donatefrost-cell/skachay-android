package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLRequest
import ru.skachay.app.settings.Quality

object YtDlpOptions {
    fun request(url: String, output: String, quality: Quality = Quality.AUTO): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--newline")
        addOption("--concurrent-fragments", 4)
        val format = if (SourceType.isYouTube(url)) {
            val height = if (quality == Quality.BEST) 1080 else 720
            "bv[height<=$height][vcodec^=avc1]+ba[acodec^=mp4a]/b[height<=$height][vcodec^=avc1]/b[height<=$height]"
        } else {
            "bv[vcodec^=avc1]+ba[acodec^=mp4a]/b[ext=mp4]/b"
        }
        addOption("--format", format)
        addOption("--merge-output-format", "mp4")
        addOption("--output", output)
    }
}
