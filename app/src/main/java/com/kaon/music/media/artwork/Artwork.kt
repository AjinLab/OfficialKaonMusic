package com.kaon.music.media.artwork

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

sealed class Artwork {
    data class BitmapSource(val bitmap: Bitmap) : Artwork()
    data class FileSource(val file: File) : Artwork()
    data class UriSource(val uri: Uri) : Artwork()
    data object None : Artwork()
}
