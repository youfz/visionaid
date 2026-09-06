package com.you.visionaid.core.di

import com.you.visionaid.BuildConfig
import com.you.visionaid.core.network.NetworkModule
import com.you.visionaid.data.FakeOcrEngine
import com.you.visionaid.domain.OcrEngine
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * 应用级手动依赖容器。
 *
 * 当前集中提供 OCR 引擎。后续接入 PaddleOCR 时，只需把 [FakeOcrEngine] 替换为真实实现，
 * ViewModel 与页面层不需要改变。
 */
class AppContainer(
    baseUrl: String = BuildConfig.API_BASE_URL,
) {
    val ocrEngine: OcrEngine by lazy { FakeOcrEngine() }
    val httpClient: OkHttpClient by lazy { NetworkModule.createHttpClient(BuildConfig.DEBUG) }
    val retrofit: Retrofit by lazy { NetworkModule.createRetrofit(baseUrl, httpClient) }

    inline fun <reified T> createApi(): T = retrofit.create(T::class.java)
}
