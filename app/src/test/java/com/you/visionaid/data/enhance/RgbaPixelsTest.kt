package com.you.visionaid.data.enhance

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbaPixelsTest {
    @Test
    fun `ARGB colors round trip through RGBA byte order`() {
        val colors = intArrayOf(
            0xFF123456.toInt(),
            0x8011AAEE.toInt(),
        )

        val image = RgbaPixels.fromArgb(width = 2, height = 1, colors = colors)

        assertEquals(
            listOf(0x12, 0x34, 0x56, 0xFF, 0x11, 0xAA, 0xEE, 0x80),
            image.pixels.map { it.toInt() and 0xFF },
        )
        assertArrayEquals(colors, RgbaPixels.toArgb(image))
    }
}
