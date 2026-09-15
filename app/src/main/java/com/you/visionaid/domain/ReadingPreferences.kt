package com.you.visionaid.domain

enum class AppFontSize(val scaleFactor: Float) {
    SMALL(0.85f),
    STANDARD(1.0f),
    MEDIUM(1.15f),
    LARGE(1.30f),
}

interface FontPreferences {
    val appFontSize: AppFontSize
    val readingFontSizeSp: Int

    fun setAppFontSize(fontSize: AppFontSize)
    fun setReadingFontSizeSp(fontSizeSp: Int)
}

const val DEFAULT_READING_FONT_SIZE_SP = 48
const val MIN_READING_FONT_SIZE_SP = 24
const val MAX_READING_FONT_SIZE_SP = 96
const val READING_FONT_SIZE_STEP_SP = 2

enum class RecognitionLanguage {
    SIMPLIFIED_CHINESE,
    ENGLISH,
}

enum class SpeechSpeed(val rate: Float) {
    SLOW(0.75f),
    NORMAL(1.0f),
    FAST(1.25f),
}
