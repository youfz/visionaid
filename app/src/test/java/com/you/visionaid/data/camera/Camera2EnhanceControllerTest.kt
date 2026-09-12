package com.you.visionaid.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import com.you.visionaid.data.camera.Camera2EnhanceController.Companion.CameraCandidate
import com.you.visionaid.data.camera.Camera2EnhanceController.Companion.PreviewDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class Camera2EnhanceControllerTest {
    @Test
    fun `exact 1280 by 720 preview is preferred`() {
        val selected = Camera2EnhanceController.choosePreviewDimensions(
            listOf(
                PreviewDimensions(640, 480),
                PreviewDimensions(1920, 1080),
                PreviewDimensions(1280, 720),
            ),
        )

        assertEquals(PreviewDimensions(1280, 720), selected)
    }

    @Test
    fun `closest aspect ratio is preferred when exact size is absent`() {
        val selected = Camera2EnhanceController.choosePreviewDimensions(
            listOf(
                PreviewDimensions(1024, 768),
                PreviewDimensions(1920, 1080),
                PreviewDimensions(640, 480),
            ),
        )

        assertEquals(PreviewDimensions(1920, 1080), selected)
    }

    @Test
    fun `external then back then first camera is selected`() {
        val front = candidate("front", CameraCharacteristics.LENS_FACING_FRONT)
        val back = candidate("back", CameraCharacteristics.LENS_FACING_BACK)
        val external = candidate("external", CameraCharacteristics.LENS_FACING_EXTERNAL)

        assertEquals(
            external,
            Camera2EnhanceController.selectPreferredCandidate(listOf(front, back, external)),
        )
        assertEquals(
            back,
            Camera2EnhanceController.selectPreferredCandidate(listOf(front, back)),
        )
        assertEquals(
            front,
            Camera2EnhanceController.selectPreferredCandidate(listOf(front)),
        )
    }

    @Test
    fun `still image favors preview aspect and bounded resolution`() {
        val selected = Camera2EnhanceController.chooseStillDimensions(
            sizes = listOf(
                PreviewDimensions(4000, 3000),
                PreviewDimensions(1920, 1080),
                PreviewDimensions(1600, 1200),
            ),
            previewSize = PreviewDimensions(1280, 720),
        )

        assertEquals(PreviewDimensions(1920, 1080), selected)
    }

    @Test
    fun `jpeg orientation follows sensor lens and display rotation`() {
        assertEquals(
            90,
            Camera2EnhanceController.jpegOrientation(
                sensorOrientation = 90,
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                displayRotation = Surface.ROTATION_0,
            ),
        )
        assertEquals(
            0,
            Camera2EnhanceController.jpegOrientation(
                sensorOrientation = 90,
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                displayRotation = Surface.ROTATION_90,
            ),
        )
        assertEquals(
            0,
            Camera2EnhanceController.jpegOrientation(
                sensorOrientation = 270,
                lensFacing = CameraCharacteristics.LENS_FACING_FRONT,
                displayRotation = Surface.ROTATION_90,
            ),
        )
    }

    private fun candidate(id: String, facing: Int) = CameraCandidate(
        id = id,
        lensFacing = facing,
        size = PreviewDimensions(1280, 720),
        fpsRange = null,
    )
}
