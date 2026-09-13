package com.you.visionaid.data.enhance

import com.rzxz.eyealgo.ProcessOptions
import com.you.visionaid.domain.ImageEnhanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EyeAlgoOptionsMapperTest {
    @Test
    fun `all enhancement modes map to the matching EyeAlgo mode`() {
        val expected = listOf(
            ProcessOptions.MODE_FULL_COLOR,
            ProcessOptions.MODE_GRAYSCALE,
            ProcessOptions.MODE_WHITE_ON_BLACK,
            ProcessOptions.MODE_BLACK_ON_WHITE,
            ProcessOptions.MODE_GREEN_ON_BLACK,
            ProcessOptions.MODE_BLACK_ON_GREEN,
            ProcessOptions.MODE_YELLOW_ON_BLACK,
            ProcessOptions.MODE_BLACK_ON_YELLOW,
            ProcessOptions.MODE_RED_ON_BLUE,
            ProcessOptions.MODE_BLUE_ON_RED,
            ProcessOptions.MODE_WHITE_ON_BLUE,
            ProcessOptions.MODE_BLUE_ON_WHITE,
            ProcessOptions.MODE_YELLOW_ON_BLUE,
            ProcessOptions.MODE_BLUE_ON_YELLOW,
            ProcessOptions.MODE_GREEN_FILTER,
        )

        val enhancementModes = ImageEnhanceMode.entries.filterNot { it.usesRawCameraStream }

        assertEquals(15, enhancementModes.size)
        assertEquals(expected, enhancementModes.map(EyeAlgoOptionsMapper::sdkMode))
    }

    @Test
    fun `raw video cannot be mapped to an EyeAlgo effect`() {
        assertThrows(IllegalArgumentException::class.java) {
            EyeAlgoOptionsMapper.sdkMode(ImageEnhanceMode.BLUE_WHITE)
        }
    }

    @Test
    fun `process options use neutral enhancement values`() {
        val options = EyeAlgoOptionsMapper.processOptions(ImageEnhanceMode.BLUE_WHITE)

        assertEquals(ProcessOptions.MODE_WHITE_ON_BLUE, options.mode())
        assertEquals(0f, options.brightness())
        assertEquals(1f, options.contrast())
        assertEquals(1f, options.saturation())
        assertEquals(0f, options.sharpen())
        assertEquals(0f, options.edgeEnhance())
        assertEquals(1f, options.zoom())
        assertEquals(0f, options.blueFilter())
    }
}
