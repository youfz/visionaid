package com.you.visionaid

import android.app.Application
import com.you.visionaid.core.di.AppContainer

/**
 * 应用级入口。
 *
 * 这里持有应用级的手动依赖容器，使 Retrofit 和 Repository 在整个进程中复用，
 * 同时避免页面直接了解网络层对象的创建细节。
 */
class VisionAidApplication : Application() {

    /** 应用级依赖容器，在 Application 创建时初始化。 */
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
