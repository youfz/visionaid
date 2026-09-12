package com.you.visionaid.data.ocr

import android.content.Context
import android.util.Log
import com.rzxz.eyealgo.AlgoResult
import com.rzxz.eyealgo.EyeOcrSdk
import com.rzxz.eyealgo.ImageFrame
import com.rzxz.eyealgo.InstallOptions
import com.rzxz.eyealgo.OcrOptions
import com.rzxz.eyealgo.PixelFormat
import com.you.visionaid.domain.OcrEngine
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.RgbaImage
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Process-wide EyeAlgo OCR 0.4.9 adapter. Model installation and inference stay off the UI thread. */
class EyeAlgoOcrEngine(
    context: Context,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OcrEngine {
    private val applicationContext = context.applicationContext
    private val installRoot = File(applicationContext.filesDir, INSTALL_DIRECTORY)
    private val mutex = Mutex()
    private var sdk: EyeOcrSdk? = null

    override suspend fun recognize(
        image: RgbaImage,
        language: RecognitionLanguage,
    ): String = withContext(inferenceDispatcher) {
        mutex.withLock {
            val engine = sdk ?: createSdk().also { sdk = it }
            val rgbaBuffer = ByteBuffer.allocateDirect(image.pixels.size).apply {
                put(image.pixels)
                flip()
            }
            val frame = ImageFrame.builder(PixelFormat.RGBA_8888, image.width, image.height)
                .rgba(rgbaBuffer)
                .rotation(0)
                .timestampNs(System.nanoTime())
                .build()
            val recognized = engine.recognizeText(frame, options(language))
                .dataOrThrow("OCR recognition")
            Log.i(
                TAG,
                "OCR completed: ${recognized.blocks().size} blocks in ${recognized.latencyMs()} ms",
            )
            recognized.fullText().orEmpty()
        }
    }

    private fun createSdk(): EyeOcrSdk {
        EyeOcrSdk.install(
            applicationContext,
            InstallOptions(installRoot, false),
        ).dataOrThrow("OCR model installation")
        return EyeOcrSdk.create(installRoot, THREAD_COUNT).dataOrThrow("OCR initialization")
    }

    private fun options(language: RecognitionLanguage): OcrOptions = OcrOptions(
        when (language) {
            RecognitionLanguage.SIMPLIFIED_CHINESE ->
                OcrOptions.LANGUAGE_CHINESE or OcrOptions.LANGUAGE_DIGITS or
                    OcrOptions.LANGUAGE_COMMON_SYMBOLS
            RecognitionLanguage.ENGLISH ->
                OcrOptions.LANGUAGE_ENGLISH or OcrOptions.LANGUAGE_DIGITS or
                    OcrOptions.LANGUAGE_COMMON_SYMBOLS
        },
        OcrOptions.SCENE_GENERAL_PRINTED,
        OcrOptions.ORIENTATION_MODE_ON_DEMAND,
        false,
        128,
        false,
        OcrOptions.TRIGGER_CAPTURE,
        960,
        0,
        0,
        0,
        0,
    ).withCropMode(OcrOptions.CROP_MODE_QUAD)

    private fun <T> AlgoResult<T>.dataOrThrow(operation: String): T {
        if (isOk()) {
            return data() ?: throw EyeAlgoOcrException("$operation returned no data")
        }
        val detail = "$operation failed: code=${code()} error=${error()} message=${message()}"
        Log.e(TAG, detail)
        throw EyeAlgoOcrException(detail)
    }

    companion object {
        private const val TAG = "EyeAlgoOcrEngine"
        private const val INSTALL_DIRECTORY = "eye_algo"
        private const val THREAD_COUNT = 4

    }
}

class EyeAlgoOcrException(message: String) : IllegalStateException(message)
