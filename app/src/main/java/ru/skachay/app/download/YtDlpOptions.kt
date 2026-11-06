package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLRequest
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.settings.Quality

internal object YtDlpOptions {
    fun request(
        url: String,
        output: String,
        quality: Quality = Quality.AUTO,
        infoJson: String? = null,
        selected: AvailableFormat? = null,
        videoOnly: Boolean = false,
        retries: Int = 2,
        audioExport: AudioExport = AudioExport.SOURCE,
        audioBitrate: AudioBitrate = AudioBitrate.K192,
        subtitleLanguage: String? = null,
        subtitleAutomatic: Boolean = false,
        embedSubtitles: Boolean = false,
    ): YoutubeDLRequest = YoutubeDLRequest(if (infoJson == null) listOf(url) else emptyList()).apply {
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--newline")
        addOption("--concurrent-fragments", 4)
        addRetries(this, retries)
        infoJson?.let { addOption("--load-info-json", it) }
        val format = if (selected != null) {
            require(Regex("[A-Za-z0-9._-]{1,80}").matches(selected.id))
            if (!selected.hasVideo || selected.hasAudio || videoOnly) selected.id
            else "${selected.id}+bestaudio[ext=m4a]/${selected.id}+bestaudio"
        } else if (SourceType.isYouTube(url)) {
            if (quality == Quality.MAX) "bestvideo+bestaudio/best"
            else {
                val height = quality.maximumHeight ?: 1080
                "bv[height<=$height][vcodec^=avc1]+ba[acodec^=mp4a]/b[height<=$height][vcodec^=avc1]/b[height<=$height]"
            }
        } else {
            "bv[vcodec^=avc1]+ba[acodec^=mp4a]/b[ext=mp4]/b"
        }
        addOption("--format", format)
        val audioOnly = selected != null && !selected.hasVideo && selected.hasAudio
        val outputAudioFormat = audioExport.ytDlpFormat
            ?.takeIf { audioOnly }
            ?: if (audioOnly && selected!!.extension == "webm") "best" else null
        if (outputAudioFormat != null) {
            addOption("--extract-audio")
            addOption("--audio-format", outputAudioFormat)
            if (audioExport != AudioExport.SOURCE) addOption("--audio-quality", "${audioBitrate.kilobits}K")
        }
        if (subtitleLanguage != null && (selected == null || selected.hasVideo)) {
            require(Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*").matches(subtitleLanguage))
            addOption(if (subtitleAutomatic) "--write-auto-subs" else "--write-subs")
            addOption("--sub-langs", subtitleLanguage)
            addOption("--sub-format", "srt/best")
            addOption("--convert-subs", "srt")
            if (embedSubtitles) addOption("--embed-subs")
        }
        addOption("--merge-output-format", "mp4")
        addOption("--output", output)
    }

    fun addRetries(request: YoutubeDLRequest, retries: Int) {
        require(retries in 0..5)
        request.addOption("--retries", retries)
        request.addOption("--fragment-retries", retries)
        request.addOption("--extractor-retries", retries)
    }
}
