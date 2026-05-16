package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.customer.CustomerSongType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerSongApiClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `download song posts userId and type with bearer token`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("Content-Disposition", "attachment; filename=hello.mp3")
                .setBody("abc")
        )
        server.start()

        val client = CustomerSongApiClient(
            okHttpClient = OkHttpClient(),
            json = json,
            baseUrl = server.url("/api/").toString()
        )

        val response = client.downloadSong(
            accessToken = "token-123",
            userId = "user-456",
            type = CustomerSongType.HELLO
        )

        val recordedRequest = server.takeRequest()
        assertEquals("/api/v2/device/download", recordedRequest.path)
        assertEquals("Bearer token-123", recordedRequest.getHeader("Authorization"))
        assertEquals("""{"userId":"user-456","type":"hello"}""", recordedRequest.body.readUtf8())
        assertArrayEquals("abc".toByteArray(), response.bytes)
        assertEquals("hello.mp3", response.fileName)
        assertEquals("audio/mpeg", response.contentType)

        server.shutdown()
    }
}
