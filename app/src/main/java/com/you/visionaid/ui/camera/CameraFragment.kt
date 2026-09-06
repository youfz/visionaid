package com.you.visionaid.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.you.visionaid.R
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.FragmentCameraBinding
import com.you.visionaid.util.TextToSpeechManager
import com.you.visionaid.viewmodel.ReadingViewModel
import kotlinx.coroutines.launch

/** 相机阅读主页；当前预览为占位区域，交互接口为 CameraX 接入预留。 */
class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var speech: TextToSpeechManager
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        speech = TextToSpeechManager(requireContext())
        binding.enhanceButton.setOnClickListener { VisualModeBottomSheet().show(childFragmentManager, "mode") }
        binding.captureButton.setOnClickListener { findNavController().navigate(R.id.enhancedReaderFragment) }
        binding.settingsButton.setOnClickListener { findNavController().navigate(R.id.settingsFragment) }
        binding.speakButton.setOnClickListener { speech.speak(viewModel.uiState.value.text) }
        binding.galleryButton.setOnClickListener {
            Toast.makeText(requireContext(), "相册导入将在 CameraX 阶段接入", Toast.LENGTH_SHORT).show()
        }
        binding.ocrButton.setOnClickListener { recognize() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { binding.recognizingProgress.isVisible = it.isRecognizing }
            }
        }
    }

    private fun recognize() {
        viewModel.recognize {
            if (isAdded) {
                if (viewModel.uiState.value.autoSpeak) speech.speak(viewModel.uiState.value.text)
                findNavController().navigate(R.id.ocrResultFragment)
            }
        }
    }

    override fun onDestroyView() {
        if (::speech.isInitialized) speech.shutdown()
        _binding = null
        super.onDestroyView()
    }
}
