package com.you.visionaid.viewmodel

import com.you.visionaid.MainDispatcherRule
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.OcrEngine
import com.you.visionaid.domain.ReadingFontSize
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.RgbaImage
import com.you.visionaid.domain.SpeechSpeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `font size moves through four fixed levels`() {
        val viewModel = ReadingViewModel(FakeEngine("文字"))
        assertEquals(ReadingFontSize.STANDARD, viewModel.uiState.value.fontSize)
        repeat(20) { viewModel.decreaseFont() }
        assertEquals(ReadingFontSize.SMALL, viewModel.uiState.value.fontSize)
        assertEquals(36, viewModel.uiState.value.fontSizeSp)
        repeat(20) { viewModel.increaseFont() }
        assertEquals(ReadingFontSize.LARGE, viewModel.uiState.value.fontSize)
        assertEquals(72, viewModel.uiState.value.fontSizeSp)
    }

    @Test
    fun `visual mode and settings update state`() {
        val viewModel = ReadingViewModel(FakeEngine("文字"))
        viewModel.setMode(ImageEnhanceMode.YELLOW_BLACK)
        viewModel.setAutoSpeak(false)
        viewModel.setSaveHistory(false)
        viewModel.setFontSize(ReadingFontSize.MEDIUM)
        viewModel.setRecognitionLanguage(RecognitionLanguage.ENGLISH)
        viewModel.setSpeechSpeed(SpeechSpeed.FAST)
        assertEquals(ImageEnhanceMode.YELLOW_BLACK, viewModel.uiState.value.mode)
        assertEquals(ReadingFontSize.MEDIUM, viewModel.uiState.value.fontSize)
        assertEquals(RecognitionLanguage.ENGLISH, viewModel.uiState.value.recognitionLanguage)
        assertEquals(SpeechSpeed.FAST, viewModel.uiState.value.speechSpeed)
        assertFalse(viewModel.uiState.value.autoSpeak)
        assertFalse(viewModel.uiState.value.saveHistory)
    }

    @Test
    fun `recognize publishes fake text and completes`() = runTest {
        val viewModel = ReadingViewModel(FakeEngine("识别完成"))
        var completed = false
        viewModel.recognize(image()) { completed = true }
        runCurrent()
        advanceUntilIdle()
        assertEquals("识别完成", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertTrue(completed)
    }

    @Test
    fun `recognize passes image and selected language to engine`() = runTest {
        val engine = RecordingEngine("recognized")
        val viewModel = ReadingViewModel(engine)
        val image = image(17)
        viewModel.setRecognitionLanguage(RecognitionLanguage.ENGLISH)

        viewModel.recognize(image) {}
        advanceUntilIdle()

        assertEquals(17, engine.received?.pixels?.first()?.toInt())
        assertEquals(RecognitionLanguage.ENGLISH, engine.language)
    }

    @Test
    fun `empty OCR result exposes no text error without completing`() = runTest {
        val viewModel = ReadingViewModel(FakeEngine(""))
        var completed = false

        viewModel.recognize(image()) { completed = true }
        advanceUntilIdle()

        assertEquals(OcrUiError.NO_TEXT, viewModel.uiState.value.ocrError)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertFalse(completed)
    }

    @Test
    fun `OCR exception exposes failure and preserves existing text`() = runTest {
        val initialText = ReadingViewModel(FakeEngine("unused")).uiState.value.text
        val viewModel = ReadingViewModel(FailingEngine())

        viewModel.recognize(image()) {}
        advanceUntilIdle()

        assertEquals(initialText, viewModel.uiState.value.text)
        assertEquals(OcrUiError.RECOGNITION_FAILED, viewModel.uiState.value.ocrError)
        assertFalse(viewModel.uiState.value.isRecognizing)
    }

    private class FakeEngine(private val result: String) : OcrEngine {
        override suspend fun recognize(image: RgbaImage, language: RecognitionLanguage): String = result
    }

    private class RecordingEngine(private val result: String) : OcrEngine {
        var received: RgbaImage? = null
        var language: RecognitionLanguage? = null

        override suspend fun recognize(image: RgbaImage, language: RecognitionLanguage): String {
            received = image
            this.language = language
            return result
        }
    }

    private class FailingEngine : OcrEngine {
        override suspend fun recognize(image: RgbaImage, language: RecognitionLanguage): String = error("failure")
    }

    private fun image(value: Int = 1) = RgbaImage(
        width = 1,
        height = 1,
        pixels = byteArrayOf(value.toByte(), 0, 0, (-1).toByte()),
    )
}
