package com.you.visionaid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.ImageEnhancer
import com.you.visionaid.domain.RgbaImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CameraPhotoUiState(
    val image: RgbaImage? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

/** Retains an unprocessed photo and always derives visual-mode results from that source. */
class CameraPhotoViewModel(
    private val imageEnhancer: ImageEnhancer,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraPhotoUiState())
    val uiState: StateFlow<CameraPhotoUiState> = _uiState.asStateFlow()
    private var source: RgbaImage? = null
    private var renderJob: Job? = null
    private var generation = 0L

    fun setSource(image: RgbaImage, mode: ImageEnhanceMode) {
        source = image.copy(pixels = image.pixels.copyOf())
        _uiState.value = CameraPhotoUiState(image = source)
        render(mode)
    }

    fun render(mode: ImageEnhanceMode) {
        val currentSource = source ?: return
        renderJob?.cancel()
        val request = ++generation
        if (mode.usesRawCameraStream) {
            _uiState.value = CameraPhotoUiState(
                image = currentSource.copy(pixels = currentSource.pixels.copyOf()),
            )
            return
        }
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        renderJob = viewModelScope.launch {
            val result = runCatching {
                withContext(processingDispatcher) { imageEnhancer.enhance(currentSource, mode) }
            }
            if (request != generation) return@launch
            _uiState.value = result.fold(
                onSuccess = { CameraPhotoUiState(image = it) },
                onFailure = {
                    CameraPhotoUiState(
                        image = currentSource.copy(pixels = currentSource.pixels.copyOf()),
                        errorMessage = it.message.orEmpty().ifBlank { "Image processing failed" },
                    )
                },
            )
        }
    }

    /** Returns a detached copy of the unprocessed photo for OCR. */
    fun sourceSnapshot(): RgbaImage? = source?.let {
        it.copy(pixels = it.pixels.copyOf())
    }

    fun clearPhoto() {
        renderJob?.cancel()
        generation++
        source = null
        _uiState.value = CameraPhotoUiState()
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        renderJob?.cancel()
        imageEnhancer.close()
        super.onCleared()
    }

    class Factory(private val imageEnhancer: ImageEnhancer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CameraPhotoViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CameraPhotoViewModel(imageEnhancer) as T
        }
    }
}
