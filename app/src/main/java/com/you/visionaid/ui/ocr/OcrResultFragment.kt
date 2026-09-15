package com.you.visionaid.ui.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.you.visionaid.R
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.FragmentOcrResultBinding
import com.you.visionaid.util.TextToSpeechManager
import com.you.visionaid.util.setReadingTextSizeSp
import com.you.visionaid.viewmodel.ReadingViewModel
import kotlinx.coroutines.launch

/** OCR 文字重排页，支持大字调节、复制与全文朗读。 */
class OcrResultFragment : Fragment() {
    private var _binding: FragmentOcrResultBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var speech: TextToSpeechManager
    private var lastAutoSpokenVersion = -1L
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine, app.appContainer.fontPreferences)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentOcrResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        speech = TextToSpeechManager(requireContext())
        lastAutoSpokenVersion = state?.getLong(KEY_AUTO_SPOKEN_VERSION, -1L) ?: -1L
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.recognizeAgainButton.setOnClickListener { findNavController().navigateUp() }
        binding.decreaseButton.setOnClickListener { viewModel.decreaseReadingFont() }
        binding.increaseButton.setOnClickListener { viewModel.increaseReadingFont() }
        binding.readAllButton.setOnClickListener {
            val readingState = viewModel.uiState.value
            speech.speak(
                readingState.text,
                readingState.recognitionLanguage,
                readingState.speechSpeed,
            )
        }
        binding.copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.recognition_result), viewModel.uiState.value.text))
            Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    binding.resultText.text = it.text
                    binding.resultText.setReadingTextSizeSp(it.readingFontSizeSp)
                    binding.fontSizeValue.text = it.readingFontSizeSp.toString()
                    if (
                        it.autoSpeak &&
                        it.recognitionVersion > 0L &&
                        it.recognitionVersion != lastAutoSpokenVersion
                    ) {
                        lastAutoSpokenVersion = it.recognitionVersion
                        speech.speak(it.text, it.recognitionLanguage, it.speechSpeed)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(KEY_AUTO_SPOKEN_VERSION, lastAutoSpokenVersion)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        if (::speech.isInitialized) speech.shutdown()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_AUTO_SPOKEN_VERSION = "auto_spoken_version"
    }
}
