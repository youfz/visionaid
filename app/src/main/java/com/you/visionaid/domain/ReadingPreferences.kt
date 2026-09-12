package com.you.visionaid.domain

enum class ReadingFontSize(val textSizeSp: Int) {
    SMALL(36),
    STANDARD(48),
    MEDIUM(60),
    LARGE(72),
}

enum class RecognitionLanguage {
    SIMPLIFIED_CHINESE,
    ENGLISH,
}

enum class SpeechSpeed(val rate: Float) {
    SLOW(0.75f),
    NORMAL(1.0f),
    FAST(1.25f),
}
