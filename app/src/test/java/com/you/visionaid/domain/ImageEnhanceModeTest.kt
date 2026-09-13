package com.you.visionaid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEnhanceModeTest {
    @Test
    fun `all selectable modes use the enhancement preview`() {
        assertTrue(ImageEnhanceMode.entries.none(ImageEnhanceMode::usesRawCameraStream))
    }

    @Test
    fun `every mode has an opaque readable text palette`() {
        ImageEnhanceMode.entries.forEach { mode ->
            assertEquals(0xFF, mode.backgroundColor ushr 24)
            assertEquals(0xFF, mode.foregroundColor ushr 24)
            assertNotEquals(mode.backgroundColor, mode.foregroundColor)
        }
    }
}
