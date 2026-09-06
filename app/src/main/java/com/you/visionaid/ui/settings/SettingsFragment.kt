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
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.FragmentSettingsBinding
import com.you.visionaid.ui.camera.VisualModeBottomSheet
import com.you.visionaid.viewmodel.ReadingViewModel
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
        binding.fontRow.setOnClickListener { viewModel.increaseFont() }
        binding.autoReadSwitch.setOnCheckedChangeListener { _, checked -> viewModel.setAutoSpeak(checked) }
        binding.saveHistorySwitch.setOnCheckedChangeListener { _, checked -> viewModel.setSaveHistory(checked) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    if (binding.autoReadSwitch.isChecked != it.autoSpeak) binding.autoReadSwitch.isChecked = it.autoSpeak
                    if (binding.saveHistorySwitch.isChecked != it.saveHistory) binding.saveHistorySwitch.isChecked = it.saveHistory
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
