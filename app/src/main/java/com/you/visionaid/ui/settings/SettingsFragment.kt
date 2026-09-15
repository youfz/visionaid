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
import com.you.visionaid.R
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.FragmentSettingsBinding
import com.you.visionaid.domain.AppFontSize
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.SpeechSpeed
import com.you.visionaid.ui.camera.VisualModeBottomSheet
import com.you.visionaid.ui.camera.titleResource
import com.you.visionaid.viewmodel.ReadingUiState
import com.you.visionaid.viewmodel.ReadingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** 设置页；界面字号与阅读正文字号分别管理，字号偏好在本地持久化。 */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine, app.appContainer.fontPreferences)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.modeRow.setOnClickListener { VisualModeBottomSheet().show(childFragmentManager, "mode") }
        binding.fontRow.setOnClickListener { showFontSizeDialog() }
        binding.languageRow.setOnClickListener { showLanguageDialog() }
        binding.speechSpeedRow.setOnClickListener { showSpeechSpeedDialog() }
        binding.autoReadSwitch.setOnCheckedChangeListener { _, checked -> viewModel.setAutoSpeak(checked) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ReadingUiState) {
        binding.fontSizeValue.setText(state.appFontSize.labelResource())
        binding.modeValue.setText(state.mode.titleResource())
        binding.languageValue.setText(state.recognitionLanguage.labelResource())
        binding.speechSpeedValue.setText(state.speechSpeed.labelResource())
        if (binding.autoReadSwitch.isChecked != state.autoSpeak) {
            binding.autoReadSwitch.isChecked = state.autoSpeak
        }
    }

    private fun showFontSizeDialog() {
        val options = AppFontSize.entries
        showSingleChoice(
            title = R.string.app_font_size,
            labels = options.map { getString(it.labelResource()) }.toTypedArray(),
            selected = viewModel.uiState.value.appFontSize.ordinal,
        ) {
            val selected = options[it]
            if (selected != viewModel.uiState.value.appFontSize) {
                viewModel.setAppFontSize(selected)
                requireActivity().recreate()
            }
        }
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
    private fun AppFontSize.labelResource(): Int = when (this) {
        AppFontSize.SMALL -> R.string.small
        AppFontSize.STANDARD -> R.string.standard
        AppFontSize.MEDIUM -> R.string.medium
        AppFontSize.LARGE -> R.string.large
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
