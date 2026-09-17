package com.you.visionaid.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFragmentTest {
    @Test
    fun `camera permission is requested before it has been denied`() {
        assertEquals(
            CameraPermissionAction.REQUEST,
            cameraPermissionAction(
                wasRequested = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `camera permission can be requested again while rationale is available`() {
        assertEquals(
            CameraPermissionAction.REQUEST,
            cameraPermissionAction(
                wasRequested = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `permanently denied camera permission opens app settings`() {
        assertEquals(
            CameraPermissionAction.OPEN_SETTINGS,
            cameraPermissionAction(
                wasRequested = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `preview center maps to sensor center for every rotation`() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val point = CameraFragment.mapPreviewPointToSensor(
                touchX = 540f,
                touchY = 900f,
                viewWidth = 1080,
                viewHeight = 1800,
                sourceWidth = 1280,
                sourceHeight = 720,
                rotationDegrees = rotation,
                mirrored = false,
            )

            assertEquals(0.5f, point.first, 0.001f)
            assertEquals(0.5f, point.second, 0.001f)
        }
    }

    @Test
    fun `mirrored preview reverses horizontal focus coordinate`() {
        val normal = mapQuarterPoint(mirrored = false)
        val mirrored = mapQuarterPoint(mirrored = true)

        assertEquals(1f - normal.first, mirrored.first, 0.001f)
        assertEquals(normal.second, mirrored.second, 0.001f)
    }

    private fun mapQuarterPoint(mirrored: Boolean) = CameraFragment.mapPreviewPointToSensor(
        touchX = 270f,
        touchY = 900f,
        viewWidth = 1080,
        viewHeight = 1800,
        sourceWidth = 720,
        sourceHeight = 1280,
        rotationDegrees = 0,
        mirrored = mirrored,
    )
}
