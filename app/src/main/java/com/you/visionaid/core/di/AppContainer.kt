package com.you.visionaid.core.di

import android.content.Context
import com.you.visionaid.data.enhance.EyeAlgoImageEnhancer
import com.you.visionaid.data.ocr.EyeAlgoOcrEngine
import com.you.visionaid.data.preferences.SharedPreferencesFontPreferences
import com.you.visionaid.domain.FontPreferences
import com.you.visionaid.domain.ImageEnhancer
import com.you.visionaid.domain.OcrEngine

/**
 * 应用级手动依赖容器。
 *
 * 当前集中提供 OCR 引擎。后续接入 PaddleOCR 时，只需把 [FakeOcrEngine] 替换为真实实现，
 * ViewModel 与页面层不需要改变。
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val ocrEngine: OcrEngine by lazy { EyeAlgoOcrEngine(applicationContext) }
    val fontPreferences: FontPreferences by lazy {
        SharedPreferencesFontPreferences(applicationContext)
    }
    fun createImageEnhancer(): ImageEnhancer = EyeAlgoImageEnhancer()
}
