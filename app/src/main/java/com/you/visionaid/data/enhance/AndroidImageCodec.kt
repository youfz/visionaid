package com.you.visionaid.data.enhance

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.you.visionaid.domain.RgbaImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.roundToInt

/** Decodes bounded, correctly oriented images and bridges Bitmap with RGBA pixels. */
class AndroidImageCodec(private val contentResolver: ContentResolver) {
    fun decodeJpeg(bytes: ByteArray): RgbaImage = decode(
        openStream = { ByteArrayInputStream(bytes) },
    )

    fun decodeUri(uri: Uri): RgbaImage = decode(
        openStream = { contentResolver.openInputStream(uri) },
    )

    fun toBitmap(image: RgbaImage): Bitmap = Bitmap.createBitmap(
        RgbaPixels.toArgb(image),
        image.width,
        image.height,
        Bitmap.Config.ARGB_8888,
    )

    private fun decode(openStream: () -> InputStream?): RgbaImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = openStream() ?: error("Unable to open image")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unable to decode image")
        val orientation = runCatching {
            openStream()?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val oriented = applyOrientation(decoded, orientation)
        val bounded = scaleToLimit(oriented)
        val outputWidth = bounded.width
        val outputHeight = bounded.height
        val colors = IntArray(bounded.width * bounded.height)
        bounded.getPixels(colors, 0, bounded.width, 0, 0, bounded.width, bounded.height)
        if (bounded !== oriented) bounded.recycle()
        if (oriented !== decoded) oriented.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        return RgbaPixels.fromArgb(outputWidth, outputHeight, colors)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToLimit(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_LONG_EDGE) return bitmap
        val scale = MAX_LONG_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(),
            true,
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_LONG_EDGE * 2) sample *= 2
        return sample
    }

    companion object {
        private const val MAX_LONG_EDGE = 2048
    }
}
