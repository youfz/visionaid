package com.you.visionaid.core.di

import com.you.visionaid.data.FakeOcrEngine
import com.you.visionaid.domain.OcrEngine

/**
 * 应用级手动依赖容器。
 *
 * 当前集中提供 OCR 引擎。后续接入 PaddleOCR 时，只需把 [FakeOcrEngine] 替换为真实实现，
 * ViewModel 与页面层不需要改变。
 */
class AppContainer {
    val ocrEngine: OcrEngine by lazy { FakeOcrEngine() }
}
