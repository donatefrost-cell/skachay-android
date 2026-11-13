package ru.skachay.app.download

import android.content.Context
import java.io.File
import java.io.IOException

internal object FfmpegRuntime {
    private fun packages(context: Context) = File(context.noBackupFilesDir, "youtubedl-android/packages")

    fun prepare(context: Context) {
        val source = File(context.applicationInfo.nativeLibraryDir, "libc++_shared.so")
        val target = File(packages(context), "ffmpeg/usr/lib/libc++_shared.so")
        if (!source.isFile || !target.parentFile.isDirectory) throw IOException("FFmpeg runtime libraries are missing")
        if (!target.isFile || target.length() != source.length()) source.copyTo(target, overwrite = true)
    }

    fun libraryPath(context: Context): String =
        listOf(
            File(packages(context), "python/usr/lib").absolutePath,
            File(packages(context), "ffmpeg/usr/lib").absolutePath,
            File(packages(context), "aria2c/usr/lib").absolutePath,
            context.applicationInfo.nativeLibraryDir,
        ).joinToString(":")
}
