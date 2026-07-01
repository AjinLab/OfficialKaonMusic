package com.kaon.music.media.artwork

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface

object PlaceholderGenerator {
    
    fun generate(title: String, album: String, artist: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Curated premium gradients (start color, end color)
        val gradients = listOf(
            Pair(0xFF4158D0.toInt(), 0xFFC850C0.toInt()), // Blue/Magenta
            Pair(0xFF00DBDE.toInt(), 0xFFFC00FF.toInt()), // Cyan/Pink
            Pair(0xFFFBAB7E.toInt(), 0xFFF7CE68.toInt()), // Peach/Yellow
            Pair(0xFF85FFBD.toInt(), 0xFFFFFB7D.toInt()), // Mint/Yellow
            Pair(0xFF21D4FD.toInt(), 0xFFB721FF.toInt()), // Sky Blue/Purple
            Pair(0xFF09203F.toInt(), 0xFF537895.toInt()), // Midnight Blue/Slate
            Pair(0xFFFA709F.toInt(), 0xFFFEE140.toInt()), // Pink/Yellow Sunset
            Pair(0xFF30CFD0.toInt(), 0xFF330867.toInt()), // Turquoise/Deep Purple
            Pair(0xFFA1C4FD.toInt(), 0xFFC2E9FB.toInt()), // Cloud/Sky Blue
            Pair(0xFFD4FC79.toInt(), 0xFF96E6A1.toInt())  // Lime/Green
        )

        // Hash text (preferring album name) to select a stable gradient
        val textToHash = if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true) && !album.equals("<unknown>", ignoreCase = true)) album else title
        val hash = textToHash.hashCode()
        val gradient = gradients[Math.abs(hash) % gradients.size]

        // Draw background gradient
        val paint = Paint().apply { isAntiAlias = true }
        val shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            gradient.first, gradient.second,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Draw subtle glowing circles for mesh-like depth
        paint.shader = null
        paint.style = Paint.Style.FILL

        // Glow 1 (top right, soft white glow)
        paint.color = Color.argb(40, 255, 255, 255)
        canvas.drawCircle(size * 0.75f, size * 0.25f, size * 0.4f, paint)

        // Glow 2 (bottom left, soft dark overlay)
        paint.color = Color.argb(30, 0, 0, 0)
        canvas.drawCircle(size * 0.25f, size * 0.75f, size * 0.35f, paint)

        // Draw stylized Monogram letter
        val letter = if (textToHash.isNotBlank()) textToHash.first().uppercaseChar().toString() else "K"
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 180f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(16f, 0f, 4f, Color.argb(80, 0, 0, 0))
        }

        val fontMetrics = textPaint.fontMetrics
        val yOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(letter, size / 2f, size / 2f - yOffset - 20f, textPaint)

        // Draw Album/Song name at the bottom
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(220, 255, 255, 255)
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(160, 255, 255, 255)
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val displayTitle = if (title.equals("<unknown>", ignoreCase = true)) "Unknown Title" else title
        val displayAlbum = if (album.equals("<unknown>", ignoreCase = true)) "Unknown Album" else album

        val maxTextWidth = size * 0.85f
        val ellipTitle = ellipsizeText(displayTitle, titlePaint, maxTextWidth)
        val ellipAlbum = ellipsizeText(displayAlbum, subtitlePaint, maxTextWidth)

        canvas.drawText(ellipTitle, size / 2f, size - 80f, titlePaint)
        canvas.drawText(ellipAlbum, size / 2f, size - 45f, subtitlePaint)

        return bitmap
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        val width = paint.measureText(text)
        if (width <= maxWidth) return text
        var len = text.length
        while (len > 0 && paint.measureText(text.substring(0, len) + "...") > maxWidth) {
            len--
        }
        return if (len > 0) text.substring(0, len) + "..." else "..."
    }
}
