package com.animevost.sdk

import com.animevost.sdk.config.AnimeVostConfig
import com.animevost.sdk.http.AnimeVostHttpClient
import com.animevost.sdk.model.CatalogFilter
import com.animevost.sdk.model.CatalogSort
import com.animevost.sdk.model.Weekday
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AnimeVostClientTest {

    @Test
    fun `getSchedule fetches base page and parses schedule`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(
            response = """
                <html>
                  <body>
                    <div id="raspisMon" class="raspis">
                      <a href="/tip/tv/3966-saikyou-degarashi-ouji-no-anyaku-teii-arasoi.html">
                        Тайная битва за престол сильнейшего принца-дуралея ~ (17:30)
                      </a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val schedule = client.getSchedule()

        assertEquals(listOf("https://example.test/animevost/"), httpClient.requestedUrls)
        assertEquals(1, httpClient.requestedHeaders.size)
        assertEquals(Weekday.MONDAY, schedule.single().weekday)
        assertEquals(
            "https://example.test/tip/tv/3966-saikyou-degarashi-ouji-no-anyaku-teii-arasoi.html",
            schedule.single().entries.single().url,
        )
    }

    @Test
    fun `getAnimeList fetches default date catalog directly`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val page = client.getAnimeList()

        assertEquals(emptyList(), httpClient.postedUrls)
        assertEquals(listOf("https://example.test/animevost/"), httpClient.requestedUrls)
        assertEquals("https://example.test/animevost/", httpClient.requestedHeaders.single()["Referer"])
        assertEquals(3970, page.items.single().id)
        assertEquals("Забывчивая святая дева", page.items.single().title)
    }

    @Test
    fun `getAnimeList fetches requested default page directly`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        client.getAnimeList(page = 2)

        assertEquals(emptyList(), httpClient.postedUrls)
        assertEquals(listOf("https://example.test/animevost/page/2/"), httpClient.requestedUrls)
    }

    @Test
    fun `getAnimeList fetches default category page directly`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        client.getAnimeList(
            page = 2,
            filter = CatalogFilter(path = "tip/tv/"),
        )

        assertEquals(emptyList(), httpClient.postedUrls)
        assertEquals(listOf("https://example.test/animevost/tip/tv/page/2/"), httpClient.requestedUrls)
    }

    @Test
    fun `getAnimeList supports custom sort and direction`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        client.getAnimeList(
            filter = CatalogFilter(
                sortBy = CatalogSort.RATING,
                sortAscending = true,
            ),
        )

        assertEquals(
            mapOf(
                "dlenewssortby" to "rating",
                "dledirection" to "asc",
                "set_new_sort" to "dle_sort_main",
                "set_direction_sort" to "dle_direction_main",
            ),
            httpClient.postedForms.single(),
        )
        assertEquals(listOf("https://example.test/animevost/"), httpClient.requestedUrls)
        assertEquals("https://example.test/animevost/", httpClient.postedHeaders.single()["Referer"])
    }

    @Test
    fun `getAnimeList treats slash path as main catalog`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        client.getAnimeList(filter = CatalogFilter(path = "/"))

        assertEquals(emptyList(), httpClient.postedUrls)
        assertEquals(listOf("https://example.test/animevost/"), httpClient.requestedUrls)
    }

    @Test
    fun `getAnimeList rejects pages below one`(): Unit = runBlocking {
        val client = AnimeVostClient(httpClient = FakeHttpClient(response = animeListHtml()))

        assertFailsWith<IllegalArgumentException> {
            client.getAnimeList(page = 0)
        }
    }

    @Test
    fun `getAnimeDetails fetches detail page and parses response`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeDetailsHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val details = client.getAnimeDetails("https://example.test/tip/tv/3970-test.html")

        assertEquals(listOf("https://example.test/tip/tv/3970-test.html"), httpClient.requestedUrls)
        assertEquals(3970, details.id)
        assertEquals("Забывчивая святая дева", details.title)
        assertEquals("1 серия", details.episodes.single().name)
    }

    @Test
    fun `getAnimeDetails rejects blank url`(): Unit = runBlocking {
        val client = AnimeVostClient(httpClient = FakeHttpClient(response = animeDetailsHtml()))

        assertFailsWith<IllegalArgumentException> {
            client.getAnimeDetails(" ")
        }
    }

    @Test
    fun `getAnimeDetails rejects external url`(): Unit = runBlocking {
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/"),
            httpClient = FakeHttpClient(response = animeDetailsHtml()),
        )

        assertFailsWith<IllegalArgumentException> {
            client.getAnimeDetails("https://attacker.test/private")
        }
    }

    @Test
    fun `getVideoSources fetches player frame and parses response`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(
            response = """
                <script>
                  var player = new Playerjs({
                    "file":"[SD (480p)]https://std.roomfish.ru/100443228.mp4"
                  });
                </script>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val sources = client.getVideoSources("100443228")

        assertEquals(listOf("https://example.test/animevost/frame5.php?play=100443228&old=1"), httpClient.requestedUrls)
        assertEquals(listOf("SD (480p)"), sources.map { it.quality })
        assertEquals(listOf("std.roomfish.ru"), sources.map { it.host })
    }

    @Test
    fun `getVideoSources rejects blank video id`(): Unit = runBlocking {
        val client = AnimeVostClient(httpClient = FakeHttpClient(response = ""))

        assertFailsWith<IllegalArgumentException> {
            client.getVideoSources(" ")
        }
    }

    @Test
    fun `searchAnime posts search form and parses results`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val page = client.searchAnime("bleach")

        assertEquals(listOf("https://example.test/animevost/index.php?do=search"), httpClient.postedUrls)
        assertEquals(
            mapOf("subaction" to "search", "story" to "bleach"),
            httpClient.postedForms.single(),
        )
        assertEquals(3970, page.items.single().id)
        assertEquals(1, page.currentPage)
    }

    @Test
    fun `searchAnime sends result offset for requested page`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(response = animeListHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val page = client.searchAnime(query = "bleach", page = 2)

        assertEquals("11", httpClient.postedForms.single()["result_from"])
        assertEquals(2, page.currentPage)
    }

    @Test
    fun `searchAnime rejects blank query and pages below one`(): Unit = runBlocking {
        val client = AnimeVostClient(httpClient = FakeHttpClient(response = animeListHtml()))

        assertFailsWith<IllegalArgumentException> {
            client.searchAnime(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            client.searchAnime("bleach", page = 0)
        }
    }

    @Test
    fun `getNavigation fetches base page and parses menu`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(
            response = """
                <html>
                  <body>
                    <div class="menu">
                      <ul id="topnav">
                        <li><a href="/zhanr/">Жанр</a>
                          <div class="sar"><a href="/zhanr/romantika/">Романтика</a></div>
                        </li>
                        <li><a href="/tip/">Категории</a>
                          <span class="sar"><a href="/tip/tv/">ТВ</a></span>
                        </li>
                        <li><a href="/god/">Год</a>
                          <span class="sar"><a href="/god/2026/">2026</a></span>
                        </li>
                        <li><a href="/ongoing/">Онгоинги</a></li>
                      </ul>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val navigation = client.getNavigation()

        assertEquals(listOf("https://example.test/animevost/"), httpClient.requestedUrls)
        assertEquals("Романтика", navigation.genres.single().title)
        assertEquals("ТВ", navigation.types.single().title)
        assertEquals("2026", navigation.years.single().title)
        assertEquals("Онгоинги", navigation.sections.single().title)
    }

    @Test
    fun `getRandomAnime fetches random endpoint and parses preview`(): Unit = runBlocking {
        val httpClient = FakeHttpClient(
            response = """
                <div class="imgOngoing">
                  <div class="imgOngoingVie">Просмотров: 827381&nbsp;|&nbsp; Комментарий: 104</div>
                  <a href="/tip/tv/1171-test.html"><span>Плач Асуры / Asura Cryin</span></a>
                  <img src="/uploads/posts/2014-12/1419617554_1.jpg" />
                </div>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val anime = client.getRandomAnime()

        assertEquals(listOf("https://example.test/animevost/get_random_post.php"), httpClient.requestedUrls)
        assertNotNull(anime)
        assertEquals(1171, anime.id)
        assertEquals("Плач Асуры", anime.title)
    }

    private fun animeListHtml(): String =
        """
            <html>
              <body>
                <div class="shortstory">
                  <div class="shortstoryHead">
                    <h2>
                      <a href="/tip/tv/3970-test.html">
                        Забывчивая святая дева / Mujikaku Seijo [1-2 из 12+]
                      </a>
                    </h2>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

    private fun animeDetailsHtml(): String =
        """
            <html>
              <body>
                <div class="shortstory">
                  <div class="shortstoryHead">
                    <h1>Забывчивая святая дева / Mujikaku Seijo [1 серия]</h1>
                  </div>
                  <script>var data = {"1 серия":"100443228",};</script>
                </div>
              </body>
            </html>
        """.trimIndent()

    private class FakeHttpClient(
        private val response: String,
    ) : AnimeVostHttpClient {
        val requestedUrls = mutableListOf<String>()
        val requestedHeaders = mutableListOf<Map<String, String>>()
        val postedUrls = mutableListOf<String>()
        val postedForms = mutableListOf<Map<String, String>>()
        val postedHeaders = mutableListOf<Map<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>): String {
            requestedUrls += url
            requestedHeaders += headers
            return response
        }

        override suspend fun post(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String {
            postedUrls += url
            postedForms += form
            postedHeaders += headers
            return response
        }

        override suspend fun postMultipart(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String = response

        override fun getCookie(name: String): String? = null

        override fun clearCookies() = Unit
    }
}
