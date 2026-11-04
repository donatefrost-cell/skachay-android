package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLRequest

object YtDlpOptions {
    fun request(url: String, output: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--newline")
        addOption("--concurrent-fragments", 4)
        addOption("--format", "bv[height<=720][vcodec^=avc1]+ba[acodec^=mp4a]/b[height<=720][vcodec^=avc1]/b[height<=720]")
        addOption("--merge-output-format", "mp4")
        addOption("--output", output)
    }
}
