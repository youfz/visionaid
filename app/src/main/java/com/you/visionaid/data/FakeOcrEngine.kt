package com.you.visionaid.data

import com.you.visionaid.domain.OcrEngine
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.RgbaImage
import kotlinx.coroutines.delay

/** 第一阶段的 Mock OCR 实现，用短延迟模拟识别过程。 */
class FakeOcrEngine : OcrEngine {
    override suspend fun recognize(image: RgbaImage, language: RecognitionLanguage): String {
        TODO("Not yet implemented")
    }
}
