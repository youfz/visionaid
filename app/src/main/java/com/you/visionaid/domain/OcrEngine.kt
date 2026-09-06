package com.you.visionaid.domain

/** OCR 能力边界；输入将在接入 CameraX 后替换为真实图像数据。 */
interface OcrEngine {
    suspend fun recognize(): String
}
