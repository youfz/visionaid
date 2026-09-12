package com.you.visionaid.domain

/** Immutable-by-convention RGBA image used at the image-enhancement boundary. */
data class RgbaImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height * BYTES_PER_PIXEL)
    }

    companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
