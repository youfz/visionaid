package com.you.visionaid.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.you.visionaid.R
import com.you.visionaid.databinding.FragmentOnboardingBinding

/** 首次启动引导；完成后将标记保存在本地，后续启动直接进入相机页。 */
class OnboardingFragment : Fragment() {
    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val preferences = requireContext().getSharedPreferences("qingyue", 0)
        if (preferences.getBoolean("onboarding_seen", false)) {
            openCamera()
            return
        }
        binding.startButton.setOnClickListener {
            preferences.edit().putBoolean("onboarding_seen", true).apply()
            openCamera()
        }
    }

    private fun openCamera() {
        findNavController().navigate(
            R.id.cameraFragment,
            null,
            androidx.navigation.navOptions { popUpTo(R.id.onboardingFragment) { inclusive = true } },
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
