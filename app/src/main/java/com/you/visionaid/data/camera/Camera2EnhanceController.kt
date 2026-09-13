package com.you.visionaid.data.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.hardware.camera2.params.StreamConfigurationMap
import android.hardware.camera2.params.MeteringRectangle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Range
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Owns Camera2 and forwards preview frames to the selected raw or EyeAlgo surface. */
class Camera2EnhanceController(
    context: Context,
    private val listener: Listener,
) : AutoCloseable {
    interface Listener {
        fun onConfigured(
            cameraId: String,
            width: Int,
            height: Int,
            sensorOrientation: Int,
            lensFacing: Int?,
            maxZoomRatio: Float,
        )
        fun onStateChanged(state: State, detail: String = "")
    }

    enum class State {
        OPENING,
        RUNNING,
        STOPPED,
        DISCONNECTED,
        ERROR,
    }

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var activeCandidate: CameraCandidate? = null
    private var pendingCapture: ((StillCaptureResult) -> Unit)? = null
    private var captureTimeout: Runnable? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    @Volatile
    private var zoomRatio = MIN_ZOOM_RATIO

    sealed interface StillCaptureResult {
        data class Success(val jpegBytes: ByteArray) : StillCaptureResult
        data class Failure(val detail: String) : StillCaptureResult
    }

    @SuppressLint("MissingPermission")
    fun start(surface: Surface, surfaceTexture: SurfaceTexture) {
        stop(notify = false)
        if (!surface.isValid) {
            notify(State.ERROR, "Camera preview surface is invalid")
            return
        }
        val manager = cameraManager
        if (manager == null) {
            notify(State.ERROR, "Camera service is unavailable")
            return
        }
        val token = generation.incrementAndGet()
        val thread = HandlerThread("visionaid-camera2").also { it.start() }
        val handler = Handler(thread.looper)
        cameraThread = thread
        cameraHandler = handler
        previewSurface = surface

        try {
            val candidate = selectCamera(manager)
            val reader = ImageReader.newInstance(
                candidate.stillSize.width,
                candidate.stillSize.height,
                ImageFormat.JPEG,
                MAX_CAPTURE_IMAGES,
            ).also { imageReader ->
                imageReader.setOnImageAvailableListener(::onImageAvailable, handler)
            }
            surfaceTexture.setDefaultBufferSize(candidate.size.width, candidate.size.height)
            activeCandidate = candidate
            imageReader = reader
            notifyConfigured(candidate)
            notify(State.OPENING, "cameraId=${candidate.id} ${candidate.size.width}x${candidate.size.height}")
            manager.openCamera(
                candidate.id,
                cameraCallback(token, candidate, handler),
                handler,
            )
        } catch (error: CameraAccessException) {
            notify(State.ERROR, error.message.orEmpty())
            stop(notify = false)
        } catch (error: IllegalStateException) {
            notify(State.ERROR, error.message.orEmpty())
            stop(notify = false)
        } catch (error: SecurityException) {
            notify(State.ERROR, error.message.orEmpty())
            stop(notify = false)
        }
    }

    private fun cameraCallback(
        token: Long,
        candidate: CameraCandidate,
        handler: Handler,
    ) = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (!isCurrent(token, handler)) {
                camera.close()
                return
            }
            cameraDevice = camera
            createSession(token, candidate, camera, handler)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            if (cameraDevice === camera) cameraDevice = null
            if (isCurrent(token, handler)) {
                notify(State.DISCONNECTED, "cameraId=${camera.id}")
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            if (cameraDevice === camera) cameraDevice = null
            if (isCurrent(token, handler)) {
                notify(State.ERROR, "cameraId=${camera.id} error=$error")
            }
        }
    }

    @Suppress("DEPRECATION") // The legacy overload is required by the app's API 26 minimum.
    private fun createSession(
        token: Long,
        candidate: CameraCandidate,
        camera: CameraDevice,
        handler: Handler,
    ) {
        val surface = previewSurface
        if (!isCurrent(token, handler) || surface == null || !surface.isValid) {
            camera.close()
            return
        }
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    candidate.continuousAfMode,
                )
                candidate.fpsRange?.let {
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                }
                applyZoom(this, candidate)
            }
            val captureSurface = imageReader?.surface
            if (captureSurface == null || !captureSurface.isValid) {
                notify(State.ERROR, "Still capture surface is unavailable")
                return
            }
            camera.createCaptureSession(
                listOf(surface, captureSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!isCurrent(token, handler) || cameraDevice !== camera) {
                            session.close()
                            return
                        }
                        captureSession = session
                        previewRequestBuilder = request
                        try {
                            session.setRepeatingRequest(request.build(), null, handler)
                            notify(
                                State.RUNNING,
                                "cameraId=${candidate.id} ${candidate.size.width}x${candidate.size.height}",
                            )
                        } catch (error: CameraAccessException) {
                            notify(State.ERROR, error.message.orEmpty())
                        } catch (error: IllegalStateException) {
                            notify(State.ERROR, error.message.orEmpty())
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        if (isCurrent(token, handler)) {
                            notify(State.ERROR, "Camera preview configuration failed")
                        }
                    }
                },
                handler,
            )
        } catch (error: CameraAccessException) {
            notify(State.ERROR, error.message.orEmpty())
        } catch (error: IllegalStateException) {
            notify(State.ERROR, error.message.orEmpty())
        }
    }

    private fun selectCamera(manager: CameraManager): CameraCandidate {
        val candidates = manager.cameraIdList.mapNotNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val streamMap = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return@mapNotNull null
            val sizes = streamMap
                .getOutputSizes(SurfaceTexture::class.java)
                ?.map { PreviewDimensions(it.width, it.height) }
                .orEmpty()
            val stillSizes = streamMap
                .getOutputSizes(ImageFormat.JPEG)
                ?.map { PreviewDimensions(it.width, it.height) }
                .orEmpty()
            if (sizes.isEmpty() || stillSizes.isEmpty()) return@mapNotNull null
            val dimensions = choosePreviewDimensions(sizes)
            val ranges = characteristics
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            CameraCandidate(
                id = id,
                lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING),
                size = dimensions,
                stillSize = chooseStillDimensions(stillSizes, dimensions),
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                fpsRange = chooseThirtyFpsRange(ranges),
                activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                maxDigitalZoom = characteristics
                    .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    ?.coerceAtLeast(MIN_ZOOM_RATIO) ?: MIN_ZOOM_RATIO,
                maxAfRegions = characteristics
                    .get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0,
                maxAeRegions = characteristics
                    .get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0,
                continuousAfMode = choosePreviewAfMode(
                    characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                ),
            )
        }
        return selectPreferredCandidate(candidates)
            ?: throw IllegalStateException("No Camera2 SurfaceTexture output is available")
    }

    /** Updates digital zoom for both the repeating preview and subsequent still captures. */
    fun setZoomRatio(ratio: Float) {
        zoomRatio = ratio.coerceAtLeast(MIN_ZOOM_RATIO)
        val handler = cameraHandler ?: return
        handler.post {
            val candidate = activeCandidate ?: return@post
            val session = captureSession ?: return@post
            val request = previewRequestBuilder ?: return@post
            zoomRatio = zoomRatio.coerceIn(MIN_ZOOM_RATIO, candidate.maxDigitalZoom)
            applyZoom(request, candidate)
            try {
                session.setRepeatingRequest(request.build(), null, handler)
            } catch (_: CameraAccessException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    /** Focuses and meters at a point expressed in normalized preview coordinates. */
    fun focusAt(normalizedX: Float, normalizedY: Float) {
        val handler = cameraHandler ?: return
        handler.post {
            val candidate = activeCandidate ?: return@post
            val session = captureSession ?: return@post
            val request = previewRequestBuilder ?: return@post
            val activeArray = candidate.activeArray ?: return@post
            val supportsAf = candidate.maxAfRegions > 0 &&
                candidate.continuousAfMode != CaptureRequest.CONTROL_AF_MODE_OFF
            val supportsAe = candidate.maxAeRegions > 0
            if (!supportsAf && !supportsAe) return@post

            val crop = zoomCrop(activeArray, zoomRatio.coerceAtMost(candidate.maxDigitalZoom))
            val metering = meteringRectangle(
                crop = crop,
                normalizedX = normalizedX,
                normalizedY = normalizedY,
            )
            try {
                if (supportsAf) {
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                    session.capture(request.build(), null, handler)
                    request.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
                    request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                }
                if (supportsAe) {
                    request.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(metering))
                }
                session.capture(request.build(), null, handler)
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                session.setRepeatingRequest(request.build(), null, handler)
            } catch (_: CameraAccessException) {
            } catch (_: IllegalArgumentException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    @Synchronized
    fun captureStill(displayRotation: Int, callback: (StillCaptureResult) -> Unit) {
        val camera = cameraDevice
        val session = captureSession
        val reader = imageReader
        val candidate = activeCandidate
        val handler = cameraHandler
        if (camera == null || session == null || reader == null || candidate == null || handler == null) {
            mainHandler.post { callback(StillCaptureResult.Failure("Camera is not ready")) }
            return
        }
        if (pendingCapture != null) {
            mainHandler.post { callback(StillCaptureResult.Failure("A capture is already in progress")) }
            return
        }
        pendingCapture = callback
        captureTimeout = Runnable {
            finishCapture(StillCaptureResult.Failure("Still capture timed out"))
        }.also { handler.postDelayed(it, CAPTURE_TIMEOUT_MS) }
        submitCapture(
            camera = camera,
            session = session,
            reader = reader,
            candidate = candidate,
            handler = handler,
            displayRotation = displayRotation,
            template = CameraDevice.TEMPLATE_STILL_CAPTURE,
            allowPreviewFallback = true,
        )
    }

    private fun submitCapture(
        camera: CameraDevice,
        session: CameraCaptureSession,
        reader: ImageReader,
        candidate: CameraCandidate,
        handler: Handler,
        displayRotation: Int,
        template: Int,
        allowPreviewFallback: Boolean,
    ) {
        try {
            val request = camera.createCaptureRequest(template).apply {
                addTarget(reader.surface)
                previewSurface?.takeIf { it.isValid }?.let(::addTarget)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, candidate.continuousAfMode)
                applyZoom(this, candidate)
                set(
                    CaptureRequest.JPEG_ORIENTATION,
                    jpegOrientation(candidate.sensorOrientation, candidate.lensFacing, displayRotation),
                )
            }.build()
            session.capture(
                request,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) = Unit

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure,
                    ) {
                        if (failure.wasImageCaptured() || !hasPendingCapture()) return
                        if (allowPreviewFallback) {
                            submitCapture(
                                camera = camera,
                                session = session,
                                reader = reader,
                                candidate = candidate,
                                handler = handler,
                                displayRotation = displayRotation,
                                template = CameraDevice.TEMPLATE_PREVIEW,
                                allowPreviewFallback = false,
                            )
                        } else {
                            finishCapture(
                                StillCaptureResult.Failure(
                                    "Still capture failed: reason=${failure.reason} frame=${failure.frameNumber}",
                                ),
                            )
                        }
                    }

                },
                handler,
            )
        } catch (error: CameraAccessException) {
            handleCaptureSubmissionFailure(
                error = error,
                camera = camera,
                session = session,
                reader = reader,
                candidate = candidate,
                handler = handler,
                displayRotation = displayRotation,
                allowPreviewFallback = allowPreviewFallback,
            )
        } catch (error: IllegalStateException) {
            handleCaptureSubmissionFailure(
                error = error,
                camera = camera,
                session = session,
                reader = reader,
                candidate = candidate,
                handler = handler,
                displayRotation = displayRotation,
                allowPreviewFallback = allowPreviewFallback,
            )
        } catch (error: IllegalArgumentException) {
            handleCaptureSubmissionFailure(
                error = error,
                camera = camera,
                session = session,
                reader = reader,
                candidate = candidate,
                handler = handler,
                displayRotation = displayRotation,
                allowPreviewFallback = allowPreviewFallback,
            )
        }
    }

    private fun handleCaptureSubmissionFailure(
        error: Exception,
        camera: CameraDevice,
        session: CameraCaptureSession,
        reader: ImageReader,
        candidate: CameraCandidate,
        handler: Handler,
        displayRotation: Int,
        allowPreviewFallback: Boolean,
    ) {
        if (!hasPendingCapture()) return
        if (allowPreviewFallback) {
            submitCapture(
                camera = camera,
                session = session,
                reader = reader,
                candidate = candidate,
                handler = handler,
                displayRotation = displayRotation,
                template = CameraDevice.TEMPLATE_PREVIEW,
                allowPreviewFallback = false,
            )
        } else {
            finishCapture(StillCaptureResult.Failure(error.message.orEmpty()))
        }
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val buffer = image.planes.firstOrNull()?.buffer
            if (buffer == null) {
                finishCapture(StillCaptureResult.Failure("Captured image has no data"))
                return
            }
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            finishCapture(StillCaptureResult.Success(bytes))
        } finally {
            image.close()
        }
    }

    @Synchronized
    private fun hasPendingCapture(): Boolean = pendingCapture != null

    @Synchronized
    private fun finishCapture(result: StillCaptureResult) {
        val callback = pendingCapture ?: return
        pendingCapture = null
        captureTimeout?.let { cameraHandler?.removeCallbacks(it) }
        captureTimeout = null
        mainHandler.post { callback(result) }
    }

    private fun isCurrent(token: Long, handler: Handler): Boolean =
        generation.get() == token && cameraHandler === handler

    @Synchronized
    fun stop() {
        stop(notify = false)
    }

    @Synchronized
    private fun stop(notify: Boolean) {
        generation.incrementAndGet()
        captureSession?.let { session ->
            try {
                session.stopRepeating()
            } catch (_: CameraAccessException) {
            } catch (_: IllegalStateException) {
            }
            session.close()
        }
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        previewSurface = null
        imageReader?.close()
        imageReader = null
        activeCandidate = null
        previewRequestBuilder = null
        finishCapture(StillCaptureResult.Failure("Camera stopped before capture completed"))
        cameraHandler = null
        cameraThread?.quitSafely()
        cameraThread = null
        if (notify) notify(State.STOPPED)
    }

    private fun notifyConfigured(candidate: CameraCandidate) {
        mainHandler.post {
            listener.onConfigured(
                cameraId = candidate.id,
                width = candidate.size.width,
                height = candidate.size.height,
                sensorOrientation = candidate.sensorOrientation,
                lensFacing = candidate.lensFacing,
                maxZoomRatio = candidate.maxDigitalZoom,
            )
        }
    }

    private fun notify(state: State, detail: String = "") {
        mainHandler.post { listener.onStateChanged(state, detail) }
    }

    override fun close() {
        stop(notify = false)
    }

    companion object {
        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720

        internal data class PreviewDimensions(val width: Int, val height: Int)

        internal data class PreviewScale(val x: Float, val y: Float)

        internal data class CameraCandidate(
            val id: String,
            val lensFacing: Int?,
            val size: PreviewDimensions,
            val stillSize: PreviewDimensions = size,
            val sensorOrientation: Int = 0,
            val fpsRange: Range<Int>?,
            val activeArray: Rect? = null,
            val maxDigitalZoom: Float = MIN_ZOOM_RATIO,
            val maxAfRegions: Int = 0,
            val maxAeRegions: Int = 0,
            val continuousAfMode: Int = CaptureRequest.CONTROL_AF_MODE_OFF,
        )

        internal fun choosePreviewAfMode(modes: IntArray?): Int = when {
            modes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true ->
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            modes?.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) == true ->
                CaptureRequest.CONTROL_AF_MODE_AUTO
            else -> CaptureRequest.CONTROL_AF_MODE_OFF
        }

        internal fun zoomCrop(activeArray: Rect, ratio: Float): Rect {
            val dimensions = zoomCropDimensions(activeArray.width(), activeArray.height(), ratio)
            val width = dimensions.width
            val height = dimensions.height
            val left = activeArray.centerX() - width / 2
            val top = activeArray.centerY() - height / 2
            return Rect(left, top, left + width, top + height)
        }

        internal fun zoomCropDimensions(width: Int, height: Int, ratio: Float): PreviewDimensions {
            val safeRatio = ratio.coerceAtLeast(MIN_ZOOM_RATIO)
            return PreviewDimensions(
                width = (width / safeRatio).toInt().coerceAtLeast(1),
                height = (height / safeRatio).toInt().coerceAtLeast(1),
            )
        }

        internal fun meteringRectangle(
            crop: Rect,
            normalizedX: Float,
            normalizedY: Float,
        ): MeteringRectangle {
            val x = normalizedX.coerceIn(0f, 1f)
            val y = normalizedY.coerceIn(0f, 1f)
            val centerX = crop.left + (crop.width() * x).toInt()
            val centerY = crop.top + (crop.height() * y).toInt()
            val halfWidth = (crop.width() * METERING_REGION_FRACTION / 2f).toInt().coerceAtLeast(1)
            val halfHeight = (crop.height() * METERING_REGION_FRACTION / 2f).toInt().coerceAtLeast(1)
            val left = (centerX - halfWidth).coerceIn(crop.left, crop.right - 1)
            val top = (centerY - halfHeight).coerceIn(crop.top, crop.bottom - 1)
            val right = (centerX + halfWidth).coerceIn(left + 1, crop.right)
            val bottom = (centerY + halfHeight).coerceIn(top + 1, crop.bottom)
            return MeteringRectangle(Rect(left, top, right, bottom), MeteringRectangle.METERING_WEIGHT_MAX)
        }

        internal fun choosePreviewDimensions(
            sizes: List<PreviewDimensions>,
            targetWidth: Int = TARGET_WIDTH,
            targetHeight: Int = TARGET_HEIGHT,
        ): PreviewDimensions {
            require(sizes.isNotEmpty())
            sizes.firstOrNull { it.width == targetWidth && it.height == targetHeight }?.let {
                return it
            }
            val targetAspect = targetWidth.toDouble() / targetHeight
            val targetPixels = targetWidth.toLong() * targetHeight
            return sizes.minBy { size ->
                val aspect = size.width.toDouble() / size.height
                val aspectPenalty = abs(aspect - targetAspect) * 100_000_000.0
                aspectPenalty + abs(size.width.toLong() * size.height - targetPixels)
            }
        }

        internal fun selectPreferredCandidate(candidates: List<CameraCandidate>): CameraCandidate? =
            candidates.firstOrNull {
                it.lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
            } ?: candidates.firstOrNull {
                it.lensFacing == CameraCharacteristics.LENS_FACING_BACK
            } ?: candidates.firstOrNull()

        internal fun chooseThirtyFpsRange(ranges: Array<Range<Int>>?): Range<Int>? = ranges
            ?.filter { it.lower <= 30 && it.upper >= 30 }
            ?.maxByOrNull { it.lower }

        internal fun chooseStillDimensions(
            sizes: List<PreviewDimensions>,
            previewSize: PreviewDimensions,
            maxLongEdge: Int = MAX_STILL_LONG_EDGE,
        ): PreviewDimensions {
            require(sizes.isNotEmpty())
            val bounded = sizes.filter { maxOf(it.width, it.height) <= maxLongEdge }
                .ifEmpty { listOf(sizes.minBy { maxOf(it.width, it.height) }) }
            val targetAspect = previewSize.width.toDouble() / previewSize.height
            return bounded.minBy { size ->
                val aspectPenalty = abs(size.width.toDouble() / size.height - targetAspect) * 1_000_000_000.0
                aspectPenalty - size.width.toLong() * size.height
            }
        }

        internal fun jpegOrientation(sensorOrientation: Int, lensFacing: Int?, displayRotation: Int): Int {
            val displayDegrees = when (displayRotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            return if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + displayDegrees) % 360
            } else {
                (sensorOrientation - displayDegrees + 360) % 360
            }
        }

        /** Texture coordinates rotate in the opposite direction from JPEG metadata. */
        internal fun previewRotation(sensorOrientation: Int, lensFacing: Int?, displayRotation: Int): Int =
            (360 - jpegOrientation(sensorOrientation, lensFacing, displayRotation)).mod(360)

        /** Compensates the clockwise quarter-turn applied by the live SurfaceTexture pipeline. */
        internal fun liveRenderRotation(previewRotation: Int): Int = (previewRotation + 90).mod(360)

        internal fun orientedPreviewDimensions(
            width: Int,
            height: Int,
            rotationDegrees: Int,
        ): PreviewDimensions = if (rotationDegrees % 180 == 0) {
            PreviewDimensions(width, height)
        } else {
            PreviewDimensions(height, width)
        }

        internal fun centerCropScale(
            sourceWidth: Int,
            sourceHeight: Int,
            viewWidth: Int,
            viewHeight: Int,
            rotationDegrees: Int,
        ): PreviewScale {
            require(sourceWidth > 0 && sourceHeight > 0 && viewWidth > 0 && viewHeight > 0)
            val oriented = orientedPreviewDimensions(sourceWidth, sourceHeight, rotationDegrees)
            val uniformScale = maxOf(
                viewWidth.toFloat() / oriented.width,
                viewHeight.toFloat() / oriented.height,
            )
            return PreviewScale(
                x = uniformScale * sourceWidth / viewWidth,
                y = uniformScale * sourceHeight / viewHeight,
            )
        }

        /** Maps upright output coordinates back into the camera buffer for GPU sampling. */
        internal fun frameTransform(rotationDegrees: Int): FloatArray = when (rotationDegrees.mod(360)) {
            90 -> floatArrayOf(
                0f, 1f, 0f,
                -1f, 0f, 1f,
                0f, 0f, 1f,
            )
            180 -> floatArrayOf(
                -1f, 0f, 1f,
                0f, -1f, 1f,
                0f, 0f, 1f,
            )
            270 -> floatArrayOf(
                0f, -1f, 1f,
                1f, 0f, 0f,
                0f, 0f, 1f,
            )
            else -> floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            )
        }

        private const val MAX_CAPTURE_IMAGES = 2
        private const val MAX_STILL_LONG_EDGE = 2048
        private const val CAPTURE_TIMEOUT_MS = 4_000L
        private const val MIN_ZOOM_RATIO = 1f
        private const val METERING_REGION_FRACTION = 0.12f
    }

    private fun applyZoom(builder: CaptureRequest.Builder, candidate: CameraCandidate) {
        val activeArray = candidate.activeArray ?: return
        val appliedRatio = zoomRatio.coerceIn(MIN_ZOOM_RATIO, candidate.maxDigitalZoom)
        builder.set(CaptureRequest.SCALER_CROP_REGION, zoomCrop(activeArray, appliedRatio))
    }
}
