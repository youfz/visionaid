package com.you.visionaid.data.enhance

import com.you.visionaid.domain.RgbaImage

/** Converts Android ARGB packed colors to and from the SDK's byte-wise RGBA layout. */
object RgbaPixels {
    fun fromArgb(width: Int, height: Int, colors: IntArray): RgbaImage {
        require(colors.size == width * height)
        val rgba = ByteArray(colors.size * RgbaImage.BYTES_PER_PIXEL)
        colors.forEachIndexed { index, color ->
            val offset = index * RgbaImage.BYTES_PER_PIXEL
            rgba[offset] = (color ushr 16).toByte()
            rgba[offset + 1] = (color ushr 8).toByte()
            rgba[offset + 2] = color.toByte()
            rgba[offset + 3] = (color ushr 24).toByte()
        }
        return RgbaImage(width, height, rgba)
    }

    fun toArgb(image: RgbaImage): IntArray = IntArray(image.width * image.height) { index ->
        val offset = index * RgbaImage.BYTES_PER_PIXEL
        val red = image.pixels[offset].toInt() and 0xFF
        val green = image.pixels[offset + 1].toInt() and 0xFF
        val blue = image.pixels[offset + 2].toInt() and 0xFF
        val alpha = image.pixels[offset + 3].toInt() and 0xFF
        alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }
}
