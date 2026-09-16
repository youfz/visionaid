package com.you.visionaid.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.you.visionaid.R
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.data.camera.Camera2EnhanceController
import com.you.visionaid.data.enhance.AndroidImageCodec
import com.you.visionaid.data.enhance.EyeAlgoOptionsMapper
import com.you.visionaid.databinding.FragmentCameraBinding
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.viewmodel.CameraPhotoUiState
import com.you.visionaid.viewmodel.CameraPhotoViewModel
import com.you.visionaid.viewmodel.OcrUiError
import com.you.visionaid.viewmodel.ReadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Camera2 reading screen with raw pass-through and EyeAlgo-enhanced preview targets. */
class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var cameraController: Camera2EnhanceController
    private lateinit var imageCodec: AndroidImageCodec
    @Volatile
    private var enhanceSurfaceReady = false
    private var rawSurfaceReady = false
    private var rawSurface: Surface? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceRotationDegrees = 0
    private var mirrorPreview = false
    private var maxZoomRatio = 1f
    private var currentZoomRatio = 1f
    @Volatile
    private var enhancedFrameTransform = Camera2EnhanceController.frameTransform(0)
    private var renderedMode: ImageEnhanceMode? = null
    private var cameraActive = false
    private var sourceLoading = false
    private var ocrRecognizing = false
    private var photoProcessing = false
    private var cameraPermissionRequested = false
    private var cameraPermissionRequestInFlight = false
    private var displayedPhoto: Bitmap? = null
    private var displayedPixels: ByteArray? = null
    private var photoBitmapJob: Job? = null
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionRequestInFlight = false
        if (granted) {
            startPreviewIfReady()
        } else {
            renderCameraFailure(getString(R.string.camera_permission_denied))
        }
    }
    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && _binding != null) loadGalleryImage(uri)
    }
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine, app.appContainer.fontPreferences)
    }
    private val photoViewModel: CameraPhotoViewModel by viewModels {
        val app = requireActivity().application as VisionAidApplication
        CameraPhotoViewModel.Factory(app.appContainer.createImageEnhancer())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        state?.let {
            cameraPermissionRequested = it.getBoolean(STATE_CAMERA_PERMISSION_REQUESTED)
        }
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        imageCodec = AndroidImageCodec(requireContext().contentResolver)
        cameraController = Camera2EnhanceController(
            requireContext().applicationContext,
            object : Camera2EnhanceController.Listener {
                override fun onConfigured(
                    cameraId: String,
                    width: Int,
                    height: Int,
                    sensorOrientation: Int,
                    lensFacing: Int?,
                    maxZoomRatio: Float,
                ) {
                    sourceWidth = width
                    sourceHeight = height
                    sourceRotationDegrees = Camera2EnhanceController.previewRotation(
                        sensorOrientation = sensorOrientation,
                        lensFacing = lensFacing,
                        displayRotation = binding.root.display?.rotation ?: Surface.ROTATION_0,
                    )
                    mirrorPreview = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                    this@CameraFragment.maxZoomRatio = maxZoomRatio.coerceAtLeast(1f)
                    currentZoomRatio = currentZoomRatio.coerceIn(1f, this@CameraFragment.maxZoomRatio)
                    cameraController.setZoomRatio(currentZoomRatio)
                    binding.zoomValue.text = getString(R.string.zoom_format, currentZoomRatio)
                    updatePreviewGeometry()
                }

                override fun onStateChanged(
                    state: Camera2EnhanceController.State,
                    detail: String,
                ) {
                    renderCameraState(state, detail)
                }
            },
        )
        configureEnhancePreview()
        configurePreviewGestures()
//        configureRawPreview()
        binding.enhanceButton.setOnClickListener { VisualModeBottomSheet().show(childFragmentManager, "mode") }
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.retakeButton.setOnClickListener { photoViewModel.clearPhoto() }
        binding.galleryButton.setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        binding.recognizeTextButton.setOnClickListener { recognize() }
        binding.cameraRetryButton.setOnClickListener {
            cameraActive = false
            if (hasCameraPermission()) {
                startPreviewIfReady()
            } else {
                requestCameraPermission()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    ocrRecognizing = it.isRecognizing
                    updateBusyUi()
                    renderPreviewMode(it.mode)
                    it.ocrError?.let(::showOcrError)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                photoViewModel.uiState.collect(::renderPhotoState)
            }
        }
    }

    private fun configureEnhancePreview() {
        binding.cameraPreview.setCompareEnabled(false)
        binding.cameraPreview.setFrameTransformProvider { _, _, _ -> enhancedFrameTransform }
        binding.cameraPreview.setProcessOptionsProvider {
            enhancedOptions(viewModel.uiState.value.mode)
        }
        binding.cameraPreview.setErrorListener { _, message ->
            Log.e(TAG, "EyeAlgo GPU error: ${message.orEmpty()}")
            binding.cameraPreview.post {
                if (!viewModel.uiState.value.mode.usesRawCameraStream) {
                    renderCameraFailure(getString(R.string.camera_failed))
                }
            }
        }
        binding.cameraPreview.setInputSurfaceListener { _, _ ->
            enhanceSurfaceReady = true
            binding.cameraPreview.post(::startPreviewIfReady)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configurePreviewGestures() {
        var tapX = Float.NaN
        var tapY = Float.NaN
        binding.cameraPreview.setOnClickListener {
            if (!canControlPreview()) return@setOnClickListener
            val x = tapX.takeUnless(Float::isNaN) ?: binding.cameraPreview.width / 2f
            val y = tapY.takeUnless(Float::isNaN) ?: binding.cameraPreview.height / 2f
            tapX = Float.NaN
            tapY = Float.NaN
            focusPreviewAt(x, y)
        }
        val scaleDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!canControlPreview()) return false
                    currentZoomRatio = (currentZoomRatio * detector.scaleFactor)
                        .coerceIn(1f, maxZoomRatio)
                    cameraController.setZoomRatio(currentZoomRatio)
                    binding.zoomValue.text = getString(R.string.zoom_format, currentZoomRatio)
                    return true
                }
            },
        )
        val tapDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    if (!canControlPreview()) return false
                    tapX = event.x
                    tapY = event.y
                    return binding.cameraPreview.performClick()
                }
            },
        )
        binding.cameraPreview.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            true
        }
    }

    private fun focusPreviewAt(x: Float, y: Float) {
        val sensorPoint = mapPreviewPointToSensor(
            touchX = x,
            touchY = y,
            viewWidth = binding.cameraPreview.width,
            viewHeight = binding.cameraPreview.height,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            rotationDegrees = Camera2EnhanceController.liveRenderRotation(sourceRotationDegrees),
            mirrored = mirrorPreview,
        )
        cameraController.focusAt(sensorPoint.first, sensorPoint.second)
        showFocusIndicator(x, y)
    }

    private fun canControlPreview(): Boolean = cameraActive &&
        photoViewModel.uiState.value.image == null &&
        !sourceLoading && !photoProcessing && !ocrRecognizing

    private fun showFocusIndicator(x: Float, y: Float) {
        binding.focusIndicator.animate().cancel()
        binding.focusIndicator.apply {
            alpha = 1f
            isVisible = true
            this.x = (x - width / 2f).coerceIn(
                0f,
                (binding.cameraPreview.width - width).coerceAtLeast(0).toFloat(),
            )
            this.y = (y - height / 2f).coerceIn(
                0f,
                (binding.cameraPreview.height - height).coerceAtLeast(0).toFloat(),
            )
            animate()
                .alpha(0f)
                .setStartDelay(FOCUS_INDICATOR_DELAY_MS)
                .setDuration(FOCUS_INDICATOR_FADE_MS)
                .withEndAction { if (_binding != null) isVisible = false }
                .start()
        }
    }

//    private fun configureRawPreview() {
//        binding.rawCameraPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
//            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
//                rawSurfaceReady = true
//                updateRawPreviewTransform(binding.rawCameraPreview)
//                binding.rawCameraPreview.post(::startPreviewIfReady)
//            }
//
//            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
//                updateRawPreviewTransform(binding.rawCameraPreview)
//            }
//
//            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
//                rawSurfaceReady = false
//                if (viewModel.uiState.value.mode.usesRawCameraStream) {
//                    cameraActive = false
//                    cameraController.stop()
//                }
//                rawSurface?.release()
//                rawSurface = null
//                return true
//            }
//
//            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
//        }
//        rawSurfaceReady = binding.rawCameraPreview.isAvailable
//    }

    private fun renderPreviewMode(mode: ImageEnhanceMode) {
        val modeChanged = renderedMode != null && renderedMode != mode
        renderedMode = mode
        binding.enhanceModeLabel.setText(mode.titleResource())
        binding.enhanceButton.contentDescription = getString(mode.titleResource())
//        binding.rawCameraPreview.isVisible = mode.usesRawCameraStream
        binding.cameraPreview.isVisible = !mode.usesRawCameraStream
        if (!mode.usesRawCameraStream) {
            binding.cameraPreview.setProcessOptions(enhancedOptions(mode))
            binding.cameraPreview.requestRender()
        }
        if (modeChanged) {
            restartPreview()
            photoViewModel.render(mode)
        }
    }

    private fun capturePhoto() {
        if (!cameraActive || sourceLoading) {
            Toast.makeText(requireContext(), R.string.capture_failed, Toast.LENGTH_SHORT).show()
            return
        }
        setSourceLoading(true)
        val displayRotation = binding.root.display?.rotation ?: Surface.ROTATION_0
        cameraController.captureStill(displayRotation) { result ->
            if (_binding == null) return@captureStill
            when (result) {
                is Camera2EnhanceController.StillCaptureResult.Success -> {
                    decodeSource(
                        decoder = { imageCodec.decodeJpeg(result.jpegBytes) },
                        errorMessage = R.string.capture_failed,
                    )
                }
                is Camera2EnhanceController.StillCaptureResult.Failure -> {
                    Log.e(TAG, "Still capture failed: ${result.detail}")
                    setSourceLoading(false)
                    Toast.makeText(requireContext(), R.string.capture_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadGalleryImage(uri: android.net.Uri) {
        setSourceLoading(true)
        decodeSource(
            decoder = { imageCodec.decodeUri(uri) },
            errorMessage = R.string.image_load_failed,
        )
    }

    private fun decodeSource(
        decoder: () -> com.you.visionaid.domain.RgbaImage,
        @androidx.annotation.StringRes errorMessage: Int,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { decoder() } }
            if (_binding == null) return@launch
            setSourceLoading(false)
            result.onSuccess {
                photoViewModel.setSource(it, viewModel.uiState.value.mode)
            }.onFailure {
                Log.e(TAG, "Image decode failed", it)
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPhotoState(state: CameraPhotoUiState) {
        if (_binding == null) return
        photoProcessing = state.isProcessing
        updateBusyUi()
        val hasPhoto = state.image != null
        binding.photoPreview.isVisible = hasPhoto
        binding.liveControls.isVisible = !hasPhoto
        binding.captureControl.isVisible = !hasPhoto
        binding.galleryControl.isVisible = !hasPhoto
        binding.photoActions.isVisible = hasPhoto
        binding.alignmentHint.isVisible = !hasPhoto
        binding.zoomValue.isVisible = !hasPhoto
        binding.scanFrame.isVisible = !hasPhoto
        if (hasPhoto) binding.focusIndicator.isVisible = false
        val image = state.image
        if (image == null) {
            clearDisplayedPhoto()
        } else if (displayedPixels !== image.pixels) {
            displayedPixels = image.pixels
            photoBitmapJob?.cancel()
            photoBitmapJob = viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.Default) { imageCodec.toBitmap(image) }
                if (_binding == null || photoViewModel.uiState.value.image?.pixels !== image.pixels) {
                    bitmap.recycle()
                    return@launch
                }
                binding.photoPreview.setImageBitmap(bitmap)
                displayedPhoto?.recycle()
                displayedPhoto = bitmap
            }
        }
        state.errorMessage?.let {
            Log.e(TAG, "Still image processing failed: $it")
            Toast.makeText(requireContext(), R.string.image_process_failed, Toast.LENGTH_SHORT).show()
            photoViewModel.clearError()
        }
    }

    private fun setSourceLoading(loading: Boolean) {
        sourceLoading = loading
        updateBusyUi()
    }

    private fun updateBusyUi() {
        if (_binding == null) return
        val busy = ocrRecognizing || sourceLoading || photoProcessing
        binding.recognizingProgress.isVisible = busy
        binding.captureButton.isEnabled = !busy
        binding.galleryButton.isEnabled = !busy
        binding.enhanceButton.isEnabled = !busy
        binding.retakeButton.isEnabled = !busy
        binding.recognizeTextButton.isEnabled = !busy
    }

    private fun clearDisplayedPhoto() {
        photoBitmapJob?.cancel()
        photoBitmapJob = null
        displayedPixels = null
        binding.photoPreview.setImageDrawable(null)
        displayedPhoto?.recycle()
        displayedPhoto = null
    }

    private fun enhancedOptions(mode: ImageEnhanceMode) = EyeAlgoOptionsMapper.processOptions(
        mode.takeUnless(ImageEnhanceMode::usesRawCameraStream) ?: ImageEnhanceMode.ORIGINAL,
    )

    private fun restartPreview() {
        cameraActive = false
        cameraController.stop()
        startPreviewIfReady()
    }

    private fun startPreviewIfReady() {
        if (!isResumed || cameraActive || _binding == null) return
        val rawMode = viewModel.uiState.value.mode.usesRawCameraStream
        if ((rawMode && !rawSurfaceReady) || (!rawMode && !enhanceSurfaceReady)) return
        if (!hasCameraPermission()) {
            if (!cameraPermissionRequested) {
                requestCameraPermission()
            } else {
                renderCameraFailure(getString(R.string.camera_permission_denied))
            }
            return
        }
        val texture = binding.cameraPreview.inputSurfaceTexture()
        val surface = binding.cameraPreview.inputSurface()
        if (surface == null || texture == null) return
        cameraActive = true
        cameraController.start(surface, texture)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        if (!isResumed || cameraPermissionRequestInFlight) return
        cameraPermissionRequested = true
        cameraPermissionRequestInFlight = true
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun updateRawPreviewTransform(preview: TextureView) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || preview.width <= 0 || preview.height <= 0) return
        val centerX = preview.width / 2f
        val centerY = preview.height / 2f
        val scale = Camera2EnhanceController.centerCropScale(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewWidth = preview.width,
            viewHeight = preview.height,
            rotationDegrees = sourceRotationDegrees,
        )
        val renderRotation = Camera2EnhanceController.liveRenderRotation(sourceRotationDegrees)
        preview.setTransform(
            Matrix().apply {
                setScale(scale.x, scale.y, centerX, centerY)
                postRotate(renderRotation.toFloat(), centerX, centerY)
                if (mirrorPreview) postScale(-1f, 1f, centerX, centerY)
            },
        )
    }

    private fun updatePreviewGeometry() {
        val cameraBinding = _binding ?: return
        val oriented = Camera2EnhanceController.orientedPreviewDimensions(
            sourceWidth,
            sourceHeight,
            sourceRotationDegrees,
        )
        enhancedFrameTransform = Camera2EnhanceController.frameTransform(
            Camera2EnhanceController.liveRenderRotation(sourceRotationDegrees),
        )
        cameraBinding.cameraPreview.setSourceSize(oriented.width, oriented.height)
//        updateRawPreviewTransform(cameraBinding.rawCameraPreview)
        cameraBinding.cameraPreview.requestRender()
    }

    private fun renderCameraState(state: Camera2EnhanceController.State, detail: String) {
        if (_binding == null) return
        when (state) {
            Camera2EnhanceController.State.OPENING -> {
                binding.alignmentHint.setText(R.string.camera_opening)
                binding.cameraRetryButton.isVisible = false
            }

            Camera2EnhanceController.State.RUNNING -> {
                cameraActive = true
                binding.alignmentHint.setText(R.string.camera_running)
                binding.cameraRetryButton.isVisible = false
            }

            Camera2EnhanceController.State.STOPPED -> cameraActive = false
            Camera2EnhanceController.State.DISCONNECTED -> {
                cameraActive = false
                renderCameraFailure(getString(R.string.camera_disconnected))
            }

            Camera2EnhanceController.State.ERROR -> {
                cameraActive = false
                Log.e(TAG, "Camera2 error: $detail")
                renderCameraFailure(getString(R.string.camera_failed))
            }
        }
    }

    private fun renderCameraFailure(message: String) {
        if (_binding == null) return
        binding.alignmentHint.text = message
        binding.cameraRetryButton.isVisible = true
    }

    private fun recognize() {
        if (ocrRecognizing || sourceLoading || photoProcessing) return
        val photo = photoViewModel.sourceSnapshot() ?: return
        recognize(photo)
    }

    private fun recognize(image: com.you.visionaid.domain.RgbaImage) {
        viewModel.recognize(image) {
            if (isAdded) {
                findNavController().navigate(R.id.ocrResultFragment)
            }
        }
    }

    private fun showOcrError(error: OcrUiError) {
        val message = when (error) {
            OcrUiError.NO_TEXT -> R.string.ocr_no_text
            OcrUiError.RECOGNITION_FAILED -> R.string.ocr_failed
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        viewModel.clearOcrError()
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        binding.cameraPreview.onResume()
        enhanceSurfaceReady = binding.cameraPreview.isInputSurfaceReady
//        rawSurfaceReady = binding.rawCameraPreview.isAvailable
        startPreviewIfReady()
    }

    override fun onPause() {
        if (_binding != null) {
            cameraActive = false
            enhanceSurfaceReady = false
            cameraController.stop()
            binding.cameraPreview.onPause()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CAMERA_PERMISSION_REQUESTED, cameraPermissionRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        if (::cameraController.isInitialized) cameraController.close()
        photoBitmapJob?.cancel()
        clearDisplayedPhoto()
        rawSurface?.release()
        rawSurface = null
        binding.cameraPreview.release()
        enhanceSurfaceReady = false
        rawSurfaceReady = false
        cameraActive = false
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val FOCUS_INDICATOR_DELAY_MS = 650L
        private const val FOCUS_INDICATOR_FADE_MS = 250L
        private const val STATE_CAMERA_PERMISSION_REQUESTED = "camera_permission_requested"

        internal fun mapPreviewPointToSensor(
            touchX: Float,
            touchY: Float,
            viewWidth: Int,
            viewHeight: Int,
            sourceWidth: Int,
            sourceHeight: Int,
            rotationDegrees: Int,
            mirrored: Boolean,
        ): Pair<Float, Float> {
            if (viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
                return 0.5f to 0.5f
            }
            val rotated = Camera2EnhanceController.orientedPreviewDimensions(
                sourceWidth,
                sourceHeight,
                rotationDegrees,
            )
            val scale = maxOf(
                viewWidth.toFloat() / rotated.width,
                viewHeight.toFloat() / rotated.height,
            )
            val renderedWidth = rotated.width * scale
            val renderedHeight = rotated.height * scale
            var x = ((touchX + (renderedWidth - viewWidth) / 2f) / renderedWidth)
                .coerceIn(0f, 1f)
            val y = ((touchY + (renderedHeight - viewHeight) / 2f) / renderedHeight)
                .coerceIn(0f, 1f)
            if (mirrored) x = 1f - x
            return when (rotationDegrees.mod(360)) {
                90 -> y to (1f - x)
                180 -> (1f - x) to (1f - y)
                270 -> (1f - y) to x
                else -> x to y
            }
        }
    }
}
