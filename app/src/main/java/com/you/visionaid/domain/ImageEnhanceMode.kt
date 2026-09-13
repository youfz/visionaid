package com.you.visionaid.domain

/** EyeAlgo 图像增强与文字重排共用的显示模式。 */
enum class ImageEnhanceMode(
    val backgroundColor: Int,
    val foregroundColor: Int,
    val usesRawCameraStream: Boolean = false,
) {
    ORIGINAL(0xFFFFFFFF.toInt(), 0xFF0B2E59.toInt()),
    BLACK_WHITE(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    WHITE_BLACK(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
    BLACK_GREEN(0xFF000000.toInt(), 0xFF4CAF50.toInt()),
    GREEN_BLACK(0xFF4CAF50.toInt(), 0xFF000000.toInt()),
    BLACK_YELLOW(0xFF000000.toInt(), 0xFFFFD54F.toInt()),
    YELLOW_BLACK(0xFFFFD54F.toInt(), 0xFF000000.toInt()),
    BLUE_RED(0xFF0B2E59.toInt(), 0xFFF44336.toInt()),
    RED_BLUE(0xFFF44336.toInt(), 0xFF0B2E59.toInt()),
    BLUE_WHITE(0xFF0B2E59.toInt(), 0xFFFFFFFF.toInt()),
    WHITE_BLUE(0xFFFFFFFF.toInt(), 0xFF0B2E59.toInt()),
    BLUE_YELLOW(0xFF0B2E59.toInt(), 0xFFFFD54F.toInt()),
    YELLOW_BLUE(0xFFFFD54F.toInt(), 0xFF0B2E59.toInt()),
    GREEN_FILTER(0xFF163A2A.toInt(), 0xFFFFFFFF.toInt()),
    GRAYSCALE(0xFFE0E0E0.toInt(), 0xFF212121.toInt()),
}
