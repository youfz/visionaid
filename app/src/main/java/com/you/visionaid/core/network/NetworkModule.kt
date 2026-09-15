/*
package com.you.visionaid.core.network

import com.you.visionaid.BuildConfig
import com.you.visionaid.MyBuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

*/
/** Creates the shared HTTP stack used by remote data sources. *//*

object NetworkModule {
    private const val TIMEOUT_SECONDS = 30L

    fun createHttpClient(
        enableLogging: Boolean = MyBuildConfig.DEBUG,
    ): OkHttpClient {
        val commonHeaders = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "VisionAid-Android/${MyBuildConfig.VERSION_NAME}")
                .build()
            chain.proceed(request)
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (enableLogging) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(commonHeaders)
            .addInterceptor(logging)
            .build()
    }

    fun createRetrofit(
        baseUrl: String,
        client: OkHttpClient,
    ): Retrofit {
        require(baseUrl.startsWith("https://")) {
            "API base URL must use HTTPS"
        }
        require(baseUrl.endsWith('/')) {
            "API base URL must end with /"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
*/
