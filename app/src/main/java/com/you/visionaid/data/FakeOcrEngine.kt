package com.you.visionaid.data

import com.you.visionaid.domain.OcrEngine
import kotlinx.coroutines.delay

/** 第一阶段的 Mock OCR 实现，用短延迟模拟识别过程。 */
class FakeOcrEngine : OcrEngine {
    override suspend fun recognize(): String {
        delay(450)
        return "春晓\n[唐] 孟浩然\n\n春眠不觉晓，\n处处闻啼鸟。\n夜来风雨声，\n花落知多少。"
    }
}
