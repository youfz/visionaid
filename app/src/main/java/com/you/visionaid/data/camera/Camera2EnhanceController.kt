package com.you.visionaid.data.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
        fun onConfigured(cameraId: String, width: Int, height: Int)
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
            notifyConfigured(candidate.id, candidate.size.width, candidate.size.height)
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
                candidate.fpsRange?.let {
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                }
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
            )
        }
        return selectPreferredCandidate(candidates)
            ?: throw IllegalStateException("No Camera2 SurfaceTexture output is available")
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
        finishCapture(StillCaptureResult.Failure("Camera stopped before capture completed"))
        cameraHandler = null
        cameraThread?.quitSafely()
        cameraThread = null
        if (notify) notify(State.STOPPED)
    }

    private fun notifyConfigured(cameraId: String, width: Int, height: Int) {
        mainHandler.post { listener.onConfigured(cameraId, width, height) }
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

        internal data class CameraCandidate(
            val id: String,
            val lensFacing: Int?,
            val size: PreviewDimensions,
            val stillSize: PreviewDimensions = size,
            val sensorOrientation: Int = 0,
            val fpsRange: Range<Int>?,
        )

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

        private const val MAX_CAPTURE_IMAGES = 2
        private const val MAX_STILL_LONG_EDGE = 2048
        private const val CAPTURE_TIMEOUT_MS = 4_000L
    }
}
