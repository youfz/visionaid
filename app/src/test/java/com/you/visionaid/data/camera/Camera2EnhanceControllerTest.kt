package com.you.visionaid.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import com.you.visionaid.data.camera.Camera2EnhanceController.Companion.CameraCandidate
import com.you.visionaid.data.camera.Camera2EnhanceController.Companion.PreviewDimensions
import com.you.visionaid.data.camera.Camera2EnhanceController.Companion.PreviewScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
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

    @Test
    fun `preview rotation is inverse of jpeg orientation`() {
        assertEquals(
            270,
            Camera2EnhanceController.previewRotation(
                sensorOrientation = 90,
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                displayRotation = Surface.ROTATION_0,
            ),
        )
        assertEquals(
            0,
            Camera2EnhanceController.previewRotation(
                sensorOrientation = 90,
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                displayRotation = Surface.ROTATION_90,
            ),
        )
    }

    @Test
    fun `live rendering removes surface texture clockwise quarter turn`() {
        assertEquals(0, Camera2EnhanceController.liveRenderRotation(270))
        assertEquals(90, Camera2EnhanceController.liveRenderRotation(0))
    }

    @Test
    fun `quarter turn swaps preview dimensions`() {
        assertEquals(
            PreviewDimensions(720, 1280),
            Camera2EnhanceController.orientedPreviewDimensions(1280, 720, 90),
        )
        assertEquals(
            PreviewDimensions(1280, 720),
            Camera2EnhanceController.orientedPreviewDimensions(1280, 720, 180),
        )
    }

    @Test
    fun `center crop removes texture view stretching after quarter turn`() {
        val scale = Camera2EnhanceController.centerCropScale(
            sourceWidth = 1280,
            sourceHeight = 720,
            viewWidth = 1080,
            viewHeight = 1800,
            rotationDegrees = 90,
        )

        assertEquals(PreviewScale(16f / 9f, 0.6f), scale)
    }

    @Test
    fun `frame transform rotates upright coordinates into camera buffer`() {
        assertArrayEquals(
            floatArrayOf(
                0f, 1f, 0f,
                -1f, 0f, 1f,
                0f, 0f, 1f,
            ),
            Camera2EnhanceController.frameTransform(90),
            0f,
        )
    }

    @Test
    fun `continuous autofocus is preferred with safe fallbacks`() {
        assertEquals(
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            Camera2EnhanceController.choosePreviewAfMode(
                intArrayOf(
                    CaptureRequest.CONTROL_AF_MODE_AUTO,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                ),
            ),
        )
        assertEquals(
            CaptureRequest.CONTROL_AF_MODE_AUTO,
            Camera2EnhanceController.choosePreviewAfMode(
                intArrayOf(CaptureRequest.CONTROL_AF_MODE_AUTO),
            ),
        )
        assertEquals(
            CaptureRequest.CONTROL_AF_MODE_OFF,
            Camera2EnhanceController.choosePreviewAfMode(null),
        )
    }

    @Test
    fun `zoom crop shrinks around the sensor center and clamps below one`() {
        assertEquals(
            PreviewDimensions(2000, 1500),
            Camera2EnhanceController.zoomCropDimensions(4000, 3000, 2f),
        )
        assertEquals(
            PreviewDimensions(4000, 3000),
            Camera2EnhanceController.zoomCropDimensions(4000, 3000, 0.5f),
        )
    }

    private fun candidate(id: String, facing: Int) = CameraCandidate(
        id = id,
        lensFacing = facing,
        size = PreviewDimensions(1280, 720),
        fpsRange = null,
    )
}
