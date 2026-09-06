package com.you.visionaid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 在单元测试期间把 Dispatchers.Main 替换为可控制的测试调度器。
 *
 * Android ViewModel 默认依赖主线程，而本地 JVM 测试没有 Android 主线程。
 * 该规则同时让测试可以通过 advanceUntilIdle 精确推进协程，避免依赖真实时间。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    /** 每个测试开始前安装测试调度器。 */
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    /** 每个测试结束后恢复 Main 调度器，避免影响其他测试类。 */
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
