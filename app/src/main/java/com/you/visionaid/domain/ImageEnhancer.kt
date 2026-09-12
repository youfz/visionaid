package com.you.visionaid.domain

/** Applies one visual mode to an unprocessed source image. */
interface ImageEnhancer : AutoCloseable {
    fun enhance(source: RgbaImage, mode: ImageEnhanceMode): RgbaImage
}
