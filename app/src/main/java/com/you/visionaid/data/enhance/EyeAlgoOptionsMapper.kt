package com.you.visionaid.data.enhance

import com.rzxz.eyealgo.ProcessOptions
import com.you.visionaid.domain.ImageEnhanceMode

/** Keeps the vendor SDK constants out of the app's UI and state layers. */
object EyeAlgoOptionsMapper {
    fun sdkMode(mode: ImageEnhanceMode): Int {
        require(!mode.usesRawCameraStream) { "Raw video must bypass EyeAlgo processing" }
        return when (mode) {
            ImageEnhanceMode.ORIGINAL -> ProcessOptions.MODE_FULL_COLOR
            ImageEnhanceMode.GRAYSCALE -> ProcessOptions.MODE_GRAYSCALE
            ImageEnhanceMode.BLACK_WHITE -> ProcessOptions.MODE_WHITE_ON_BLACK
            ImageEnhanceMode.WHITE_BLACK -> ProcessOptions.MODE_BLACK_ON_WHITE
            ImageEnhanceMode.BLACK_GREEN -> ProcessOptions.MODE_GREEN_ON_BLACK
            ImageEnhanceMode.GREEN_BLACK -> ProcessOptions.MODE_BLACK_ON_GREEN
            ImageEnhanceMode.BLACK_YELLOW -> ProcessOptions.MODE_YELLOW_ON_BLACK
            ImageEnhanceMode.YELLOW_BLACK -> ProcessOptions.MODE_BLACK_ON_YELLOW
            ImageEnhanceMode.BLUE_RED -> ProcessOptions.MODE_RED_ON_BLUE
            ImageEnhanceMode.RED_BLUE -> ProcessOptions.MODE_BLUE_ON_RED
            ImageEnhanceMode.BLUE_WHITE -> ProcessOptions.MODE_WHITE_ON_BLUE
            ImageEnhanceMode.WHITE_BLUE -> ProcessOptions.MODE_BLUE_ON_WHITE
            ImageEnhanceMode.BLUE_YELLOW -> ProcessOptions.MODE_YELLOW_ON_BLUE
            ImageEnhanceMode.YELLOW_BLUE -> ProcessOptions.MODE_BLUE_ON_YELLOW
            ImageEnhanceMode.GREEN_FILTER -> ProcessOptions.MODE_GREEN_FILTER
        }
    }

    fun processOptions(mode: ImageEnhanceMode): ProcessOptions = ProcessOptions(
        sdkMode(mode),
        0f,
        1f,
        1f,
        0f,
        0f,
        1f,
        0f,
        ProcessOptions.DEFAULT_DENOISE,
        ProcessOptions.DEFAULT_READING_THRESHOLD,
        ProcessOptions.DEFAULT_STROKE_FILL,
        ProcessOptions.DEFAULT_EDGE_THRESHOLD,
    )
}
