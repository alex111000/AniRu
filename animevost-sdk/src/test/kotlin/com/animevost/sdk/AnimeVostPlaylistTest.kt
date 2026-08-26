package com.animevost.sdk

import com.animevost.sdk.http.AnimeVostHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeVostPlaylistTest {

    @Test
    fun parsesVeryLongPlaylistWithoutTruncation(): Unit = runBlocking {
        val json = buildString {
            append('[')
            repeat(1200) { index ->
                if (index > 0) append(',')
                val episode = index + 1
                append("{\"name\":\"$episode серия\",\"hd\":\"https://cdn.example/720/$episode.mp4\"}")
            }
            append(']')
        }
        val client = AnimeVostClient(httpClient = FakeHttpClient(json))

        val playlist = client.getPlaylist(179)

        assertEquals(1200, playlist.size)
        assertEquals(1, playlist.first().number)
        assertEquals(1200, playlist.last().number)
        assertTrue(playlist.last().hdUrl!!.endsWith("/1200.mp4"))
    }

    private class FakeHttpClient(private val response: String) : AnimeVostHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): String = response
        override suspend fun post(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String = response
        override suspend fun postMultipart(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String = response
        override fun getCookie(name: String): String? = null
        override fun clearCookies() = Unit
    }
}
