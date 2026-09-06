package com.you.visionaid.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.you.visionaid.databinding.ActivityMainBinding

/** 单 Activity 容器，页面切换统一交给 Navigation Component。 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
