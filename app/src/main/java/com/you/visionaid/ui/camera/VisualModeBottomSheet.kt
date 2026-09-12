package com.you.visionaid.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.you.visionaid.R
import com.you.visionaid.VisionAidApplication
import com.you.visionaid.databinding.ItemVisualModeBinding
import com.you.visionaid.databinding.SheetVisualModeBinding
import com.you.visionaid.domain.ImageEnhanceMode
import com.you.visionaid.viewmodel.ReadingViewModel

/** Displays raw camera pass-through and every mode exposed by EyeAlgo Enhance. */
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
        binding.modeList.layoutManager = LinearLayoutManager(requireContext())
        binding.modeList.adapter = VisualModeAdapter(viewModel.uiState.value.mode) { mode ->
            viewModel.setMode(mode)
            dismiss()
        }
    }

    override fun onDestroyView() {
        binding.modeList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

private class VisualModeAdapter(
    private val selectedMode: ImageEnhanceMode,
    private val onSelected: (ImageEnhanceMode) -> Unit,
) : RecyclerView.Adapter<VisualModeAdapter.ModeViewHolder>() {
    private val modes = ImageEnhanceMode.entries

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModeViewHolder {
        val binding = ItemVisualModeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ModeViewHolder(binding, onSelected)
    }

    override fun onBindViewHolder(holder: ModeViewHolder, position: Int) {
        holder.bind(modes[position], modes[position] == selectedMode)
    }

    override fun getItemCount(): Int = modes.size

    class ModeViewHolder(
        private val binding: ItemVisualModeBinding,
        private val onSelected: (ImageEnhanceMode) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(mode: ImageEnhanceMode, selected: Boolean) {
            val context = binding.root.context
            binding.modeTitle.setText(mode.titleResource())
            binding.modeDescription.setText(mode.descriptionResource())
            binding.modePreview.setBackgroundColor(mode.backgroundColor)
            binding.modePreview.setTextColor(mode.foregroundColor)
            binding.modeSelected.isChecked = selected
            binding.modeCard.strokeColor = ContextCompat.getColor(
                context,
                if (selected) R.color.primary_blue else R.color.border,
            )
            binding.modeCard.strokeWidth = if (selected) 2.dp(context) else 1.dp(context)
            binding.modeCard.contentDescription = context.getString(mode.titleResource())
            binding.modeCard.setOnClickListener { onSelected(mode) }
        }
    }
}

@StringRes
private fun ImageEnhanceMode.titleResource(): Int = when (this) {
    ImageEnhanceMode.RAW_VIDEO -> R.string.raw_video
    ImageEnhanceMode.ORIGINAL -> R.string.original_enhance
    ImageEnhanceMode.GRAYSCALE -> R.string.grayscale_diagnostic
    ImageEnhanceMode.BLACK_WHITE -> R.string.black_white
    ImageEnhanceMode.WHITE_BLACK -> R.string.white_black
    ImageEnhanceMode.BLACK_GREEN -> R.string.black_green
    ImageEnhanceMode.GREEN_BLACK -> R.string.green_black
    ImageEnhanceMode.BLACK_YELLOW -> R.string.black_yellow
    ImageEnhanceMode.YELLOW_BLACK -> R.string.yellow_black
    ImageEnhanceMode.BLUE_RED -> R.string.blue_red
    ImageEnhanceMode.RED_BLUE -> R.string.red_blue
    ImageEnhanceMode.BLUE_WHITE -> R.string.blue_white
    ImageEnhanceMode.WHITE_BLUE -> R.string.white_blue
    ImageEnhanceMode.BLUE_YELLOW -> R.string.blue_yellow
    ImageEnhanceMode.YELLOW_BLUE -> R.string.yellow_blue
    ImageEnhanceMode.GREEN_FILTER -> R.string.green_filter
}

@StringRes
private fun ImageEnhanceMode.descriptionResource(): Int = when (this) {
    ImageEnhanceMode.RAW_VIDEO -> R.string.raw_video_desc
    ImageEnhanceMode.ORIGINAL -> R.string.original_desc
    ImageEnhanceMode.GRAYSCALE -> R.string.grayscale_diagnostic_desc
    ImageEnhanceMode.GREEN_FILTER -> R.string.green_filter_desc
    else -> R.string.high_contrast_desc
}

private fun Int.dp(context: android.content.Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
