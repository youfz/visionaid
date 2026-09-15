package com.you.visionaid.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import com.you.visionaid.R
import com.you.visionaid.data.preferences.SharedPreferencesFontPreferences
import com.you.visionaid.databinding.ActivityMainBinding

/** 单 Activity 容器，页面切换统一交给 Navigation Component。 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var systemBarInsets = Insets.NONE
    private var currentDestinationId = 0

    override fun attachBaseContext(newBase: Context) {
        val appFontSize = SharedPreferencesFontPreferences(newBase).appFontSize
        val scaledConfiguration = Configuration(newBase.resources.configuration).apply {
            fontScale *= appFontSize.scaleFactor
        }
        super.attachBaseContext(newBase.createConfigurationContext(scaledConfiguration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.camera_bottom_bar)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        binding.mainNavigation.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentDestinationId = destination.id
            applySystemBarInsets()
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            applySystemBarInsets()
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun applySystemBarInsets() {
        if (!::binding.isInitialized) return
        val showsBottomNavigation = currentDestinationId == R.id.cameraFragment ||
            currentDestinationId == R.id.settingsFragment
        binding.mainNavigation.isVisible = showsBottomNavigation
        binding.mainNavigation.updatePadding(bottom = systemBarInsets.bottom)
        binding.navHostFragment.updatePadding(
            top = if (currentDestinationId == R.id.cameraFragment) 0 else systemBarInsets.top,
            bottom = if (showsBottomNavigation) 0 else systemBarInsets.bottom,
        )
    }
}
