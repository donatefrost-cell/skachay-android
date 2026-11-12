package ru.skachay.app.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import ru.skachay.app.download.DirectDownloader
import ru.skachay.app.network.UrlNetworkPolicy
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class InvalidUpdateException : IOException("Update package or signature does not match")

internal class UpdateDownloader(
    private val context: Context,
    private val downloadAsset: (String, File) -> Unit = { url, target ->
        val client =
            UrlNetworkPolicy.publicHttpsClient(
                callTimeoutSeconds = 60 * 60,
                allowedRedirectHostSuffixes = setOf("github.com", "githubusercontent.com"),
            )
        DirectDownloader(client).download(url, target, connections = 4)
    },
) {
    fun download(release: ReleaseInfo): Uri {
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create update folder")
        val temporary = File(directory, "download.apk")
        val result = File(directory, "update.apk")
        try {
            downloadAsset(release.downloadUrl, temporary)
            if (!ArchiveValidation.matchesSha256(ArchiveValidation.sha256(temporary), release.sha256)) throw InvalidUpdateException()
            if (!validateArchive(temporary, release.versionCode)) throw InvalidUpdateException()
            Files.move(temporary.toPath(), result.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return FileProvider.getUriForFile(context, "${context.packageName}.files", result)
        } finally {
            temporary.delete()
        }
    }

    @Suppress("DEPRECATION")
    internal fun validateArchive(
        file: File,
        expectedVersion: Int,
    ): Boolean {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val installed = manager.getPackageInfo(context.packageName, flags)
        return ArchiveValidation.matches(
            context.packageName,
            archive.packageName,
            expectedVersion,
            ReleaseStore.version(archive),
            signers(installed),
            signers(archive),
        )
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): List<String> {
        val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return values?.map { it.toCharsString() }.orEmpty()
    }
}
