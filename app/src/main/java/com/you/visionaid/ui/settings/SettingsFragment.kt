package com.you.visionaid.ui.settings

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
import com.you.visionaid.databinding.FragmentSettingsBinding
import com.you.visionaid.domain.ReadingFontSize
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.SpeechSpeed
import com.you.visionaid.ui.camera.VisualModeBottomSheet
import com.you.visionaid.viewmodel.ReadingUiState
import com.you.visionaid.viewmodel.ReadingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** 设置页；第一阶段设置保存在共享 ViewModel 中，进程内即时生效。 */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.modeRow.setOnClickListener { VisualModeBottomSheet().show(childFragmentManager, "mode") }
        binding.fontRow.setOnClickListener { showFontSizeDialog() }
        binding.languageRow.setOnClickListener { showLanguageDialog() }
        binding.speechSpeedRow.setOnClickListener { showSpeechSpeedDialog() }
        binding.autoReadSwitch.setOnCheckedChangeListener { _, checked -> viewModel.setAutoSpeak(checked) }
        binding.saveHistorySwitch.setOnCheckedChangeListener { _, checked -> viewModel.setSaveHistory(checked) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ReadingUiState) {
        binding.fontSizeValue.setText(state.fontSize.labelResource())
        binding.languageValue.setText(state.recognitionLanguage.labelResource())
        binding.speechSpeedValue.setText(state.speechSpeed.labelResource())
        if (binding.autoReadSwitch.isChecked != state.autoSpeak) {
            binding.autoReadSwitch.isChecked = state.autoSpeak
        }
        if (binding.saveHistorySwitch.isChecked != state.saveHistory) {
            binding.saveHistorySwitch.isChecked = state.saveHistory
        }
    }

    private fun showFontSizeDialog() {
        val options = ReadingFontSize.entries
        showSingleChoice(
            title = R.string.font_size,
            labels = options.map { getString(it.labelResource()) }.toTypedArray(),
            selected = viewModel.uiState.value.fontSize.ordinal,
        ) { viewModel.setFontSize(options[it]) }
    }

    private fun showLanguageDialog() {
        val options = RecognitionLanguage.entries
        showSingleChoice(
            title = R.string.language,
            labels = options.map { getString(it.labelResource()) }.toTypedArray(),
            selected = viewModel.uiState.value.recognitionLanguage.ordinal,
        ) { viewModel.setRecognitionLanguage(options[it]) }
    }

    private fun showSpeechSpeedDialog() {
        val options = SpeechSpeed.entries
        showSingleChoice(
            title = R.string.speech_speed,
            labels = options.map { getString(it.labelResource()) }.toTypedArray(),
            selected = viewModel.uiState.value.speechSpeed.ordinal,
        ) { viewModel.setSpeechSpeed(options[it]) }
    }

    private fun showSingleChoice(
        @androidx.annotation.StringRes title: Int,
        labels: Array<String>,
        selected: Int,
        onSelected: (Int) -> Unit,
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @androidx.annotation.StringRes
    private fun ReadingFontSize.labelResource(): Int = when (this) {
        ReadingFontSize.SMALL -> R.string.small
        ReadingFontSize.STANDARD -> R.string.standard
        ReadingFontSize.MEDIUM -> R.string.medium
        ReadingFontSize.LARGE -> R.string.large
    }

    @androidx.annotation.StringRes
    private fun RecognitionLanguage.labelResource(): Int = when (this) {
        RecognitionLanguage.SIMPLIFIED_CHINESE -> R.string.simplified_chinese
        RecognitionLanguage.ENGLISH -> R.string.english
    }

    @androidx.annotation.StringRes
    private fun SpeechSpeed.labelResource(): Int = when (this) {
        SpeechSpeed.SLOW -> R.string.slow
        SpeechSpeed.NORMAL -> R.string.normal
        SpeechSpeed.FAST -> R.string.fast
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
