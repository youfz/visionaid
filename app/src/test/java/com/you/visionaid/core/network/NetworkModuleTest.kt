package com.you.visionaid.core.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkModuleTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `client adds common request headers`() {
        server.enqueue(MockResponse().setBody("{}"))
        val client = NetworkModule.createHttpClient(enableLogging = false)

        client.newCall(okhttp3.Request.Builder().url(server.url("health")).build()).execute().close()

        val request = server.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("VisionAid-Android/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `retrofit rejects insecure base URL`() {
        NetworkModule.createRetrofit(
            baseUrl = "http://example.com/",
            client = NetworkModule.createHttpClient(enableLogging = false),
        )
    }
}
