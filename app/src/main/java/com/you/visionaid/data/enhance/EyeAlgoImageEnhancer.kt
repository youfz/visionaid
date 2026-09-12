package com.you.visionaid.data.enhance

import com.rzxz.eyealgo.EyeEnhanceSdk
import com.rzxz.eyealgo.ImageFrame
import com.rzxz.eyealgo.PixelFormat
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.ImageEnhancer
import com.you.visionaid.domain.RgbaImage
import java.nio.ByteBuffer

/** CPU-backed EyeAlgo processing for captured and imported still images. */
class EyeAlgoImageEnhancer : ImageEnhancer {
    private val sdkDelegate = lazy {
        val result = EyeEnhanceSdk.create(
            Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREAD_COUNT),
        )
        check(result.isOk) { result.message().orEmpty().ifBlank { "EyeAlgo initialization failed" } }
        requireNotNull(result.data())
    }
    private val sdk: EyeEnhanceSdk by sdkDelegate

    override fun enhance(source: RgbaImage, mode: ImageEnhanceMode): RgbaImage {
        if (mode.usesRawCameraStream) return source.copy(pixels = source.pixels.copyOf())
        val inputBuffer = directBuffer(source.pixels)
        val outputBuffer = ByteBuffer.allocateDirect(source.pixels.size)
        val input = rgbaFrame(source, inputBuffer)
        val output = rgbaFrame(source, outputBuffer)
        val result = sdk.processFrameTo(input, output, EyeAlgoOptionsMapper.processOptions(mode))
        check(result.isOk) { result.message().orEmpty().ifBlank { "EyeAlgo image processing failed" } }
        outputBuffer.rewind()
        return source.copy(pixels = ByteArray(source.pixels.size).also(outputBuffer::get))
    }

    private fun rgbaFrame(image: RgbaImage, buffer: ByteBuffer): ImageFrame = ImageFrame.builder(
        PixelFormat.RGBA_8888,
        image.width,
        image.height,
    ).rgba(buffer).build()

    private fun directBuffer(bytes: ByteArray): ByteBuffer = ByteBuffer.allocateDirect(bytes.size).apply {
        put(bytes)
        rewind()
    }

    override fun close() {
        if (sdkDelegate.isInitialized()) sdk.close()
    }

    companion object {
        private const val MAX_THREAD_COUNT = 4
    }
}
