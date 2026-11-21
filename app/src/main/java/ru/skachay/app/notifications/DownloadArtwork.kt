package ru.skachay.app.notifications

import android.app.Notification
import android.graphics.Bitmap

internal fun Notification.Builder.setDownloadArtwork(cover: Bitmap): Notification.Builder {
    val side = minOf(cover.width, cover.height)
    val compact = Bitmap.createBitmap(cover, (cover.width - side) / 2, (cover.height - side) / 2, side, side)
    return setLargeIcon(compact).setStyle(Notification.BigPictureStyle().bigPicture(cover))
}
