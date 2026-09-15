package com.you.visionaid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.you.visionaid.domain.AppFontSize
import com.you.visionaid.domain.DEFAULT_READING_FONT_SIZE_SP
import com.you.visionaid.domain.FontPreferences
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.MAX_READING_FONT_SIZE_SP
import com.you.visionaid.domain.MIN_READING_FONT_SIZE_SP
import com.you.visionaid.domain.OcrEngine
import com.you.visionaid.domain.READING_FONT_SIZE_STEP_SP
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.RgbaImage
import com.you.visionaid.domain.SpeechSpeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingUiState(
    val text: String = "春晓\n[唐] 孟浩然\n\n春眠不觉晓，\n处处闻啼鸟。\n夜来风雨声，\n花落知多少。",
    val appFontSize: AppFontSize = AppFontSize.STANDARD,
    val readingFontSizeSp: Int = DEFAULT_READING_FONT_SIZE_SP,
    val mode: ImageEnhanceMode = ImageEnhanceMode.ORIGINAL,
    val recognitionLanguage: RecognitionLanguage = RecognitionLanguage.SIMPLIFIED_CHINESE,
    val speechSpeed: SpeechSpeed = SpeechSpeed.NORMAL,
    val isRecognizing: Boolean = false,
    val ocrError: OcrUiError? = null,
    val recognitionVersion: Long = 0L,
    val autoSpeak: Boolean = false,
    val saveHistory: Boolean = true,
)

enum class OcrUiError {
    NO_TEXT,
    RECOGNITION_FAILED,
}

/** 跨页面共享阅读文字、独立字号、视觉模式与设置状态。 */
class ReadingViewModel(
    private val ocrEngine: OcrEngine,
    private val fontPreferences: FontPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ReadingUiState(
            appFontSize = fontPreferences.appFontSize,
            readingFontSizeSp = fontPreferences.readingFontSizeSp,
        ),
    )
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    fun setMode(mode: ImageEnhanceMode) = _uiState.update { it.copy(mode = mode) }

    fun decreaseReadingFont() = _uiState.update {
        val updated = (it.readingFontSizeSp - READING_FONT_SIZE_STEP_SP)
            .coerceAtLeast(MIN_READING_FONT_SIZE_SP)
        fontPreferences.setReadingFontSizeSp(updated)
        it.copy(readingFontSizeSp = updated)
    }

    fun increaseReadingFont() = _uiState.update {
        val updated = (it.readingFontSizeSp + READING_FONT_SIZE_STEP_SP)
            .coerceAtMost(MAX_READING_FONT_SIZE_SP)
        fontPreferences.setReadingFontSizeSp(updated)
        it.copy(readingFontSizeSp = updated)
    }

    fun setAppFontSize(fontSize: AppFontSize) = _uiState.update {
        fontPreferences.setAppFontSize(fontSize)
        it.copy(appFontSize = fontSize)
    }

    fun setRecognitionLanguage(language: RecognitionLanguage) =
        _uiState.update { it.copy(recognitionLanguage = language) }

    fun setSpeechSpeed(speed: SpeechSpeed) = _uiState.update { it.copy(speechSpeed = speed) }

    fun setAutoSpeak(enabled: Boolean) = _uiState.update { it.copy(autoSpeak = enabled) }

    fun setSaveHistory(enabled: Boolean) = _uiState.update { it.copy(saveHistory = enabled) }

    fun recognize(image: RgbaImage, onComplete: () -> Unit) {
        if (_uiState.value.isRecognizing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRecognizing = true, ocrError = null) }
            try {
                val result = ocrEngine.recognize(image, _uiState.value.recognitionLanguage)
                if (result.isBlank()) {
                    _uiState.update { it.copy(isRecognizing = false, ocrError = OcrUiError.NO_TEXT) }
                } else {
                    _uiState.update {
                        it.copy(
                            text = result,
                            isRecognizing = false,
                            recognitionVersion = it.recognitionVersion + 1L,
                        )
                    }
                    onComplete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(isRecognizing = false, ocrError = OcrUiError.RECOGNITION_FAILED)
                }
            }
        }
    }

    fun clearOcrError() = _uiState.update { it.copy(ocrError = null) }

    class Factory(
        private val engine: OcrEngine,
        private val fontPreferences: FontPreferences,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReadingViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReadingViewModel(engine, fontPreferences) as T
        }
    }

}
