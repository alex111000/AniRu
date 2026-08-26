package com.animevost.sdk

import com.animevost.sdk.config.AnimeVostConfig
import com.animevost.sdk.error.AnimeVostAuthException
import com.animevost.sdk.http.AnimeVostHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimeVostFavoritesClientTest {

    @Test
    fun `getFavorites fetches favorites page and parses items`(): Unit = runBlocking {
        val httpClient = FakeFavoritesHttpClient(
            response = favoritesHtml(),
            cookies = mapOf("dle_user_id" to "42"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val page = client.getFavorites(page = 2)

        assertEquals(listOf("https://example.test/animevost/favorites/page/2/"), httpClient.requestedUrls)
        assertEquals(3970, page.items.single().id)
        assertEquals("Забывчивая святая дева", page.items.single().title)
    }

    @Test
    fun `getFavorites requires auth session`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeFavoritesHttpClient(response = favoritesHtml()),
        )

        assertFailsWith<AnimeVostAuthException> {
            client.getFavorites()
        }
    }

    @Test
    fun `addFavorite calls add endpoint`(): Unit = runBlocking {
        val httpClient = FakeFavoritesHttpClient(
            response = "<html>ok</html>",
            cookies = mapOf("dle_user_id" to "42"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.addFavorite(3970)

        assertEquals(
            listOf("https://example.test/animevost/index.php?do=favorites&doaction=add&id=3970"),
            httpClient.requestedUrls,
        )
        assertEquals(3970, result.newsId)
        assertTrue(result.isFavorite)
    }

    @Test
    fun `removeFavorite calls delete endpoint`(): Unit = runBlocking {
        val httpClient = FakeFavoritesHttpClient(
            response = "<html>ok</html>",
            cookies = mapOf("dle_user_id" to "42"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.removeFavorite(3970)

        assertEquals(
            listOf("https://example.test/animevost/index.php?do=favorites&doaction=del&id=3970"),
            httpClient.requestedUrls,
        )
        assertEquals(3970, result.newsId)
        assertFalse(result.isFavorite)
    }

    @Test
    fun `favorite actions reject invalid ids`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeFavoritesHttpClient(
                response = "<html>ok</html>",
                cookies = mapOf("dle_user_id" to "42"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            client.addFavorite(0)
        }
        assertFailsWith<IllegalArgumentException> {
            client.removeFavorite(-1)
        }
    }

    private fun favoritesHtml(): String =
        """
            <html>
              <body>
                <div class="shortstory">
                  <a class="shortstoryShare" id="fav-id-3970"></a>
                  <div class="shortstoryHead">
                    <h2><a href="/tip/tv/3970-test.html">Забывчивая святая дева / Mujikaku Seijo [1 серия]</a></h2>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

    private class FakeFavoritesHttpClient(
        private val response: String,
        private val cookies: Map<String, String> = emptyMap(),
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
            cookies[name]

        override fun clearCookies() = Unit
    }
}
