package com.you.visionaid.viewmodel

import com.you.visionaid.MainDispatcherRule
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.OcrEngine
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
    fun `font size stays within accessible range`() {
        val viewModel = ReadingViewModel(FakeEngine("文字"))
        repeat(20) { viewModel.decreaseFont() }
        assertEquals(24, viewModel.uiState.value.fontSizeSp)
        repeat(20) { viewModel.increaseFont() }
        assertEquals(80, viewModel.uiState.value.fontSizeSp)
    }

    @Test
    fun `visual mode and settings update state`() {
        val viewModel = ReadingViewModel(FakeEngine("文字"))
        viewModel.setMode(ImageEnhanceMode.YELLOW_BLACK)
        viewModel.setAutoSpeak(false)
        viewModel.setSaveHistory(false)
        assertEquals(ImageEnhanceMode.YELLOW_BLACK, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.autoSpeak)
        assertFalse(viewModel.uiState.value.saveHistory)
    }

    @Test
    fun `recognize publishes fake text and completes`() = runTest {
        val viewModel = ReadingViewModel(FakeEngine("识别完成"))
        var completed = false
        viewModel.recognize { completed = true }
        runCurrent()
        advanceUntilIdle()
        assertEquals("识别完成", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertTrue(completed)
    }

    private class FakeEngine(private val result: String) : OcrEngine {
        override suspend fun recognize(): String = result
    }
}
