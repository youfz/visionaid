package com.you.visionaid.viewmodel

import com.you.visionaid.MainDispatcherRule
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.OcrEngine
import com.you.visionaid.domain.AppFontSize
import com.you.visionaid.domain.FontPreferences
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
    fun `reading font size changes by two and stays within bounds`() {
        val preferences = FakeFontPreferences()
        val viewModel = ReadingViewModel(FakeEngine("文字"), preferences)
        assertEquals(48, viewModel.uiState.value.readingFontSizeSp)
        viewModel.decreaseReadingFont()
        assertEquals(46, viewModel.uiState.value.readingFontSizeSp)
        viewModel.increaseReadingFont()
        assertEquals(48, viewModel.uiState.value.readingFontSizeSp)
        repeat(50) { viewModel.decreaseReadingFont() }
        assertEquals(24, viewModel.uiState.value.readingFontSizeSp)
        repeat(50) { viewModel.increaseReadingFont() }
        assertEquals(96, viewModel.uiState.value.readingFontSizeSp)
        assertEquals(96, preferences.readingFontSizeSp)
    }

    @Test
    fun `visual mode and settings update state`() {
        val preferences = FakeFontPreferences()
        val viewModel = ReadingViewModel(FakeEngine("文字"), preferences)
        viewModel.setMode(ImageEnhanceMode.YELLOW_BLACK)
        viewModel.setAutoSpeak(false)
        viewModel.setSaveHistory(false)
        viewModel.setAppFontSize(AppFontSize.MEDIUM)
        viewModel.increaseReadingFont()
        viewModel.setRecognitionLanguage(RecognitionLanguage.ENGLISH)
        viewModel.setSpeechSpeed(SpeechSpeed.FAST)
        assertEquals(ImageEnhanceMode.YELLOW_BLACK, viewModel.uiState.value.mode)
        assertEquals(AppFontSize.MEDIUM, viewModel.uiState.value.appFontSize)
        assertEquals(AppFontSize.MEDIUM, preferences.appFontSize)
        assertEquals(50, viewModel.uiState.value.readingFontSizeSp)
        assertEquals(RecognitionLanguage.ENGLISH, viewModel.uiState.value.recognitionLanguage)
        assertEquals(SpeechSpeed.FAST, viewModel.uiState.value.speechSpeed)
        assertFalse(viewModel.uiState.value.autoSpeak)
        assertFalse(viewModel.uiState.value.saveHistory)
    }

    @Test
    fun `font preferences initialize independently`() {
        val preferences = FakeFontPreferences(AppFontSize.LARGE, 72)
        val viewModel = ReadingViewModel(FakeEngine("文字"), preferences)

        assertEquals(AppFontSize.LARGE, viewModel.uiState.value.appFontSize)
        assertEquals(72, viewModel.uiState.value.readingFontSizeSp)

        viewModel.setAppFontSize(AppFontSize.SMALL)
        assertEquals(72, viewModel.uiState.value.readingFontSizeSp)

        viewModel.decreaseReadingFont()
        assertEquals(AppFontSize.SMALL, viewModel.uiState.value.appFontSize)
    }

    @Test
    fun `recognize publishes fake text and completes`() = runTest {
        val viewModel = ReadingViewModel(FakeEngine("识别完成"), FakeFontPreferences())
        var completed = false
        viewModel.recognize(image()) { completed = true }
        runCurrent()
        advanceUntilIdle()
        assertEquals("识别完成", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertTrue(completed)
        assertEquals(1L, viewModel.uiState.value.recognitionVersion)
    }

    @Test
    fun `recognize passes image and selected language to engine`() = runTest {
        val engine = RecordingEngine("recognized")
        val viewModel = ReadingViewModel(engine, FakeFontPreferences())
        val image = image(17)
        viewModel.setRecognitionLanguage(RecognitionLanguage.ENGLISH)

        viewModel.recognize(image) {}
        advanceUntilIdle()

        assertEquals(17, engine.received?.pixels?.first()?.toInt())
        assertEquals(RecognitionLanguage.ENGLISH, engine.language)
    }

    @Test
    fun `empty OCR result exposes no text error without completing`() = runTest {
        val viewModel = ReadingViewModel(FakeEngine(""), FakeFontPreferences())
        var completed = false

        viewModel.recognize(image()) { completed = true }
        advanceUntilIdle()

        assertEquals(OcrUiError.NO_TEXT, viewModel.uiState.value.ocrError)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertFalse(completed)
    }

    @Test
    fun `OCR exception exposes failure and preserves existing text`() = runTest {
        val initialText = ReadingViewModel(
            FakeEngine("unused"),
            FakeFontPreferences(),
        ).uiState.value.text
        val viewModel = ReadingViewModel(FailingEngine(), FakeFontPreferences())

        viewModel.recognize(image()) {}
        advanceUntilIdle()

        assertEquals(initialText, viewModel.uiState.value.text)
        assertEquals(OcrUiError.RECOGNITION_FAILED, viewModel.uiState.value.ocrError)
        assertFalse(viewModel.uiState.value.isRecognizing)
    }

    @Test
    fun `each successful recognition publishes a new result version`() = runTest {
        val viewModel = ReadingViewModel(FakeEngine("recognized"), FakeFontPreferences())

        viewModel.recognize(image()) {}
        advanceUntilIdle()
        viewModel.recognize(image()) {}
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.recognitionVersion)
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

    private class FakeFontPreferences(
        initialAppFontSize: AppFontSize = AppFontSize.STANDARD,
        initialReadingFontSizeSp: Int = 48,
    ) : FontPreferences {
        private var storedAppFontSize = initialAppFontSize
        private var storedReadingFontSizeSp = initialReadingFontSizeSp

        override val appFontSize: AppFontSize get() = storedAppFontSize
        override val readingFontSizeSp: Int get() = storedReadingFontSizeSp

        override fun setAppFontSize(fontSize: AppFontSize) {
            storedAppFontSize = fontSize
        }

        override fun setReadingFontSizeSp(fontSizeSp: Int) {
            storedReadingFontSizeSp = fontSizeSp
        }
    }

    private fun image(value: Int = 1) = RgbaImage(
        width = 1,
        height = 1,
        pixels = byteArrayOf(value.toByte(), 0, 0, (-1).toByte()),
    )
}
