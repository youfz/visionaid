package com.you.visionaid.viewmodel

import com.you.visionaid.MainDispatcherRule
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.domain.ImageEnhancer
import com.you.visionaid.domain.RgbaImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraPhotoViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `mode changes always process the original source`() = runTest {
        val enhancer = RecordingEnhancer()
        val viewModel = CameraPhotoViewModel(enhancer, dispatcherRule.dispatcher)
        val source = image(10)

        viewModel.setSource(source, ImageEnhanceMode.BLUE_WHITE)
        advanceUntilIdle()
        viewModel.render(ImageEnhanceMode.YELLOW_BLACK)
        advanceUntilIdle()

        assertEquals(listOf(10, 10), enhancer.inputs)
        assertEquals(12, viewModel.uiState.value.image?.pixels?.first()?.toInt())
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `clear photo returns to live state`() = runTest {
        val enhancer = RecordingEnhancer()
        val viewModel = CameraPhotoViewModel(enhancer, dispatcherRule.dispatcher)

        viewModel.setSource(image(25), ImageEnhanceMode.ORIGINAL)
        advanceUntilIdle()

        assertEquals(listOf(25), enhancer.inputs)
        assertEquals(26, viewModel.uiState.value.image?.pixels?.first()?.toInt())
        viewModel.clearPhoto()
        assertNull(viewModel.uiState.value.image)
    }

    @Test
    fun `processing failure keeps original image and exposes an error`() = runTest {
        val viewModel = CameraPhotoViewModel(
            imageEnhancer = FailingEnhancer(),
            processingDispatcher = dispatcherRule.dispatcher,
        )

        viewModel.setSource(image(42), ImageEnhanceMode.BLUE_WHITE)
        advanceUntilIdle()

        assertEquals(42, viewModel.uiState.value.image?.pixels?.first()?.toInt())
        assertEquals("failure", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `OCR snapshot uses original pixels and is detached from retained source`() = runTest {
        val viewModel = CameraPhotoViewModel(RecordingEnhancer(), dispatcherRule.dispatcher)
        viewModel.setSource(image(30), ImageEnhanceMode.BLUE_WHITE)
        advanceUntilIdle()

        val snapshot = requireNotNull(viewModel.sourceSnapshot())
        assertEquals(30, snapshot.pixels.first().toInt())
        assertEquals(31, viewModel.uiState.value.image?.pixels?.first()?.toInt())

        snapshot.pixels[0] = 99
        assertEquals(30, viewModel.sourceSnapshot()?.pixels?.first()?.toInt())
    }

    private fun image(value: Int) = RgbaImage(
        width = 1,
        height = 1,
        pixels = byteArrayOf(value.toByte(), 0, 0, (-1).toByte()),
    )

    private class RecordingEnhancer : ImageEnhancer {
        val inputs = mutableListOf<Int>()

        override fun enhance(source: RgbaImage, mode: ImageEnhanceMode): RgbaImage {
            inputs += source.pixels.first().toInt()
            return source.copy(
                pixels = source.pixels.copyOf().also { it[0] = (it[0] + inputs.size).toByte() },
            )
        }

        override fun close() = Unit
    }

    private class FailingEnhancer : ImageEnhancer {
        override fun enhance(source: RgbaImage, mode: ImageEnhanceMode): RgbaImage = error("failure")
        override fun close() = Unit
    }
}
