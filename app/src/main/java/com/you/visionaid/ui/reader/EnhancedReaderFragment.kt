package com.you.visionaid.ui.reader

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.you.visionaid.R
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.FragmentEnhancedReaderBinding
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.ui.camera.VisualModeBottomSheet
import com.you.visionaid.util.TextToSpeechManager
import com.you.visionaid.viewmodel.ReadingUiState
import com.you.visionaid.viewmodel.ReadingViewModel
import kotlinx.coroutines.launch

/** 大字增强阅读页，实时响应字号与四种视觉模式。 */
class EnhancedReaderFragment : Fragment() {
    private var _binding: FragmentEnhancedReaderBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var speech: TextToSpeechManager
    private var isSpeaking = false
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentEnhancedReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        speech = TextToSpeechManager(requireContext())
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.changeModeButton.setOnClickListener { VisualModeBottomSheet().show(childFragmentManager, "mode") }
        binding.decreaseButton.setOnClickListener { viewModel.decreaseFont() }
        binding.increaseButton.setOnClickListener { viewModel.increaseFont() }
        binding.readerSpeakButton.setOnClickListener {
            if (isSpeaking) {
                speech.pause()
                binding.readerSpeakButton.setText(R.string.continue_reading)
            } else {
                // Android TTS 没有原生续读位置接口，基础版本从当前文本开头继续。
                speech.speak(viewModel.uiState.value.text)
                binding.readerSpeakButton.setText(R.string.pause)
            }
            isSpeaking = !isSpeaking
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ReadingUiState) {
        binding.readingText.text = state.text
        binding.readingText.textSize = state.fontSizeSp.toFloat()
        val (background, foreground) = when (state.mode) {
            ImageEnhanceMode.ORIGINAL -> Color.WHITE to Color.rgb(11, 46, 89)
            ImageEnhanceMode.BLACK_WHITE -> Color.BLACK to Color.WHITE
            ImageEnhanceMode.BLUE_WHITE -> Color.rgb(11, 46, 89) to Color.WHITE
            ImageEnhanceMode.YELLOW_BLACK -> Color.rgb(255, 213, 79) to Color.BLACK
        }
        binding.readerRoot.setBackgroundColor(background)
        binding.readingText.setTextColor(foreground)
        binding.readerTitle.setTextColor(foreground)
        binding.backButton.setTextColor(foreground)
        binding.changeModeButton.setTextColor(foreground)
        binding.changeModeButton.strokeColor = ColorStateList.valueOf(foreground)
    }

    override fun onDestroyView() {
        if (::speech.isInitialized) speech.shutdown()
        _binding = null
        super.onDestroyView()
    }
}
