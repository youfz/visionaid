plugins {
    // Android 应用与 Kotlin 基础插件。
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.you.visionaid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.you.visionaid"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val apiBaseUrl = providers.gradleProperty("VISIONAID_API_BASE_URL")
            .orElse("https://api.example.com/")
            .get()
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.trimEnd('/')}/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        // 为每个 XML 布局生成类型安全的 Binding 类，替代 findViewById。
        viewBinding = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "lib/arm64-v8a/libc++_shared.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // EyeAlgo R13 客户测试版：Core 0.4.11 与 Enhance 0.4.7 必须配套使用。
    implementation(files("libs/eye-algo-core-0.4.11-release.aar"))
    implementation(files("libs/eye-algo-enhance-0.4.7-release.aar"))
    implementation(files("libs/eye-algo-ocr-0.4.9-release.aar"))

    // AndroidX 生命周期、Activity 与列表 UI。
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.material)

    // 网络请求与 JSON 反序列化。
 /*   implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)*/

    // 本地 JVM 测试、协程调度测试和可控 HTTP 测试服务。
    testImplementation(libs.junit)
/*    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)*/
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
