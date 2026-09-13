package com.you.visionaid.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
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
import com.you.visionaid.util.TextToSpeechManager
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
    private lateinit var speech: TextToSpeechManager
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
    @Volatile
    private var enhancedFrameTransform = Camera2EnhanceController.frameTransform(0)
    private var renderedMode: ImageEnhanceMode? = null
    private var cameraActive = false
    private var sourceLoading = false
    private var ocrRecognizing = false
    private var photoProcessing = false
    private var displayedPhoto: Bitmap? = null
    private var displayedPixels: ByteArray? = null
    private var photoBitmapJob: Job? = null
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
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
        ReadingViewModel.Factory(app.appContainer.ocrEngine)
    }
    private val photoViewModel: CameraPhotoViewModel by viewModels {
        val app = requireActivity().application as VisionAidApplication
        CameraPhotoViewModel.Factory(app.appContainer.createImageEnhancer())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        speech = TextToSpeechManager(requireContext())
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
                ) {
                    sourceWidth = width
                    sourceHeight = height
                    sourceRotationDegrees = Camera2EnhanceController.previewRotation(
                        sensorOrientation = sensorOrientation,
                        lensFacing = lensFacing,
                        displayRotation = binding.root.display?.rotation ?: Surface.ROTATION_0,
                    )
                    mirrorPreview = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
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
        configureRawPreview()
        binding.enhanceButton.setOnClickListener { VisualModeBottomSheet().show(childFragmentManager, "mode") }
        binding.captureButton.setOnClickListener { onCaptureClicked() }
        binding.settingsButton.setOnClickListener { findNavController().navigate(R.id.settingsFragment) }
        binding.speakButton.setOnClickListener { speakCurrentText() }
        binding.galleryButton.setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        binding.ocrButton.setOnClickListener { recognize() }
        binding.cameraRetryButton.setOnClickListener {
            cameraActive = false
            startPreviewIfReady()
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

    private fun configureRawPreview() {
        binding.rawCameraPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                rawSurfaceReady = true
                updateRawPreviewTransform(binding.rawCameraPreview)
                binding.rawCameraPreview.post(::startPreviewIfReady)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                updateRawPreviewTransform(binding.rawCameraPreview)
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                rawSurfaceReady = false
                if (viewModel.uiState.value.mode.usesRawCameraStream) {
                    cameraActive = false
                    cameraController.stop()
                }
                rawSurface?.release()
                rawSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        rawSurfaceReady = binding.rawCameraPreview.isAvailable
    }

    private fun renderPreviewMode(mode: ImageEnhanceMode) {
        val modeChanged = renderedMode != null && renderedMode != mode
        renderedMode = mode
        binding.rawCameraPreview.isVisible = mode.usesRawCameraStream
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

    private fun onCaptureClicked() {
        if (photoViewModel.uiState.value.image != null) {
            photoViewModel.clearPhoto()
            return
        }
        capturePhoto()
    }

    private fun capturePhoto(onDecoded: ((com.you.visionaid.domain.RgbaImage) -> Unit)? = null) {
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
                        onDecoded = onDecoded,
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
        onDecoded: ((com.you.visionaid.domain.RgbaImage) -> Unit)? = null,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { decoder() } }
            if (_binding == null) return@launch
            setSourceLoading(false)
            result.onSuccess {
                photoViewModel.setSource(it, viewModel.uiState.value.mode)
                onDecoded?.invoke(it)
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
        binding.photoPreview.isVisible = state.image != null
        binding.captureButton.contentDescription = getString(
            if (state.image == null) R.string.capture_photo else R.string.back_to_live_camera,
        )
        if (state.image == null) {
            binding.captureButton.icon = null
        } else {
            binding.captureButton.setIconResource(R.drawable.ic_back)
        }
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
        binding.ocrButton.isEnabled = !busy
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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val texture = if (rawMode) {
            binding.rawCameraPreview.surfaceTexture
        } else {
            binding.cameraPreview.inputSurfaceTexture()
        }
        val surface = if (rawMode) {
            texture?.let { rawTexture ->
                rawSurface?.takeIf { it.isValid } ?: Surface(rawTexture).also { rawSurface = it }
            }
        } else {
            binding.cameraPreview.inputSurface()
        }
        if (surface == null || texture == null) return
        cameraActive = true
        cameraController.start(surface, texture)
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
        updateRawPreviewTransform(cameraBinding.rawCameraPreview)
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
        val photo = photoViewModel.sourceSnapshot()
        if (photo != null) {
            recognize(photo)
        } else {
            capturePhoto(::recognize)
        }
    }

    private fun recognize(image: com.you.visionaid.domain.RgbaImage) {
        viewModel.recognize(image) {
            if (isAdded) {
                if (viewModel.uiState.value.autoSpeak) speakCurrentText()
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

    private fun speakCurrentText() {
        val state = viewModel.uiState.value
        speech.speak(state.text, state.recognitionLanguage, state.speechSpeed)
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        binding.cameraPreview.onResume()
        enhanceSurfaceReady = binding.cameraPreview.isInputSurfaceReady
        rawSurfaceReady = binding.rawCameraPreview.isAvailable
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
        if (::speech.isInitialized) speech.shutdown()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}
