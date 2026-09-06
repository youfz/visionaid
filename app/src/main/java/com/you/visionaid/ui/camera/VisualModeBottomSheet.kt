package com.you.visionaid.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.SheetVisualModeBinding
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.viewmodel.ReadingViewModel

/** 视觉模式选择弹窗，选择后立即更新跨页面共享状态。 */
class VisualModeBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetVisualModeBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: ReadingViewModel by activityViewModels {
        val app = requireActivity().application as VisionAidApplication
        ReadingViewModel.Factory(app.appContainer.ocrEngine)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = SheetVisualModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        when (viewModel.uiState.value.mode) {
            ImageEnhanceMode.ORIGINAL -> binding.modeOriginal.isChecked = true
            ImageEnhanceMode.BLACK_WHITE -> binding.modeBlack.isChecked = true
            ImageEnhanceMode.BLUE_WHITE -> binding.modeBlue.isChecked = true
            ImageEnhanceMode.YELLOW_BLACK -> binding.modeYellow.isChecked = true
        }
        binding.modeOriginal.setOnClickListener { choose(ImageEnhanceMode.ORIGINAL) }
        binding.modeBlack.setOnClickListener { choose(ImageEnhanceMode.BLACK_WHITE) }
        binding.modeBlue.setOnClickListener { choose(ImageEnhanceMode.BLUE_WHITE) }
        binding.modeYellow.setOnClickListener { choose(ImageEnhanceMode.YELLOW_BLACK) }
    }

    private fun choose(mode: ImageEnhanceMode) {
        viewModel.setMode(mode)
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
