package com.you.visionaid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.OcrEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingUiState(
    val text: String = "春晓\n[唐] 孟浩然\n\n春眠不觉晓，\n处处闻啼鸟。\n夜来风雨声，\n花落知多少。",
    val fontSizeSp: Int = 48,
    val mode: ImageEnhanceMode = ImageEnhanceMode.BLUE_WHITE,
    val isRecognizing: Boolean = false,
    val autoSpeak: Boolean = true,
    val saveHistory: Boolean = true,
)

/** 跨页面共享阅读文字、字号、视觉模式与设置状态。 */
class ReadingViewModel(private val ocrEngine: OcrEngine) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    fun setMode(mode: ImageEnhanceMode) = _uiState.update { it.copy(mode = mode) }

    fun decreaseFont() = _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp - 4).coerceAtLeast(24)) }

    fun increaseFont() = _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp + 4).coerceAtMost(80)) }

    fun setAutoSpeak(enabled: Boolean) = _uiState.update { it.copy(autoSpeak = enabled) }

    fun setSaveHistory(enabled: Boolean) = _uiState.update { it.copy(saveHistory = enabled) }

    fun recognize(onComplete: () -> Unit) {
        if (_uiState.value.isRecognizing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRecognizing = true) }
            val result = ocrEngine.recognize()
            _uiState.update { it.copy(text = result, isRecognizing = false) }
            onComplete()
        }
    }

    class Factory(private val engine: OcrEngine) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReadingViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReadingViewModel(engine) as T
        }
    }
}
