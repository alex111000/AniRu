package com.animevost.sdk

import com.animevost.sdk.config.AnimeVostConfig
import com.animevost.sdk.error.AnimeVostServerException
import com.animevost.sdk.error.AnimeVostValidationException
import com.animevost.sdk.http.AnimeVostHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnimeVostRatingClientTest {

    @Test
    fun `voteAnime calls rating endpoint and parses result`(): Unit = runBlocking {
        val httpClient = FakeRatingHttpClient(
            response = """
                {
                  "success": true,
                  "rating": "&lt;div class=\"rating\"&gt;&lt;ul class=\"unit-rating\"&gt;&lt;li class=\"current-rating\" style=\"width:80%;\"&gt;80&lt;/li&gt;&lt;/ul&gt;&lt;/div&gt;",
                  "votenum": "17"
                }
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.voteAnime(newsId = 3970, rating = 4)

        assertEquals(
            listOf("https://example.test/animevost/engine/ajax/rating.php?go_rate=4&news_id=3970&skin=AnimeVostNext5"),
            httpClient.requestedUrls,
        )
        assertEquals(3970, result.newsId)
        assertEquals(4, result.submittedRating)
        assertEquals(4.0, result.rating)
        assertEquals(17, result.voteCount)
        assertEquals(true, result.success)
    }

    @Test
    fun `voteAnime rejects rating outside site range before request`(): Unit = runBlocking {
        val httpClient = FakeRatingHttpClient(response = "{}")
        val client = AnimeVostClient(httpClient = httpClient)

        assertFailsWith<AnimeVostValidationException> {
            client.voteAnime(newsId = 3970, rating = 6)
        }
        assertEquals(emptyList(), httpClient.requestedUrls)
    }

    @Test
    fun `voteAnime maps server rejection to sdk exception`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeRatingHttpClient(response = "Hacking attempt!"),
        )

        assertFailsWith<AnimeVostServerException> {
            client.voteAnime(newsId = 3970, rating = 5)
        }
    }

    @Test
    fun `voteAnime maps json failure to sdk exception`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeRatingHttpClient(
                response = """{"success":false,"error":"Вы уже голосовали"}""",
            ),
        )

        assertFailsWith<AnimeVostServerException> {
            client.voteAnime(newsId = 3970, rating = 5)
        }
    }

    private class FakeRatingHttpClient(
        private val response: String,
    ) : AnimeVostHttpClient {
        val requestedUrls = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): String {
            requestedUrls += url
            return response
        }

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

        override fun getCookie(name: String): String? =
            null

        override fun clearCookies() = Unit
    }
}
