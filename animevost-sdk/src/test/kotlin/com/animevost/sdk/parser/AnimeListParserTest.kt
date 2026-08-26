package com.animevost.sdk.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimeListParserTest {

    private val parser = AnimeListParser()

    @Test
    fun `parses shortstory anime preview`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div id="dle-content">
                      <div class="shortstory">
                        <div class="shortstoryHead">
                          <h2>
                            <a href="/tip/tv/3970-mujikaku-seijo-wa-kyou-mo-muishiki-ni-chikara-wo-tare-nagasu.html">
                              Забывчивая святая дева неосознанно изливает свою силу / Mujikaku Seijo wa Kyou mo Muishiki ni Chikara wo Tare Nagasu [1-2 из 12+]
                            </a>
                          </h2>
                        </div>
                        <div class="staticInfo">
                          <div class="staticInfoLeft">
                            <span class="staticInfoLeftData">7 июль 2026</span>
                          </div>
                          <div class="staticInfoRight">
                            <span class="staticInfoRightSmotr">88220</span>
                            <span><a href="/tip/tv/3970-mujikaku-seijo-wa-kyou-mo-muishiki-ni-chikara-wo-tare-nagasu.html#comment">42</a></span>
                          </div>
                        </div>
                        <div class="shortstoryContent">
                          <a href="/tip/tv/3970-mujikaku-seijo-wa-kyou-mo-muishiki-ni-chikara-wo-tare-nagasu.html">
                            <img src="/uploads/posts/2026-06/1782839996_1.jpg" />
                          </a>
                          <div class="rating">
                            <li class="current-rating" style="width:80%;">80</li>
                          </div>
                          <span id="vote-num-id-3970">130</span>
                        </div>
                        <div class="shortstoryFuter">
                          <form action="#">
                            <a href="/tip/tv/3970-mujikaku-seijo-wa-kyou-mo-muishiki-ni-chikara-wo-tare-nagasu.html">Смотреть</a>
                          </form>
                          <span><strong>Категории:</strong>
                            <i>
                              <a href="/tip/tv/">ТВ</a>
                              <a href="/ongoing/">Онгоинги</a>
                              <a href="/god/2026/">2026</a>
                            </i>
                          </span>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(1, page.items.size)
        assertEquals(1, page.currentPage)
        assertEquals(1, page.totalPages)

        val anime = page.items.single()
        assertEquals(3970, anime.id)
        assertEquals("Забывчивая святая дева неосознанно изливает свою силу", anime.title)
        assertEquals("Mujikaku Seijo wa Kyou mo Muishiki ni Chikara wo Tare Nagasu", anime.originalTitle)
        assertEquals("1-2 из 12+", anime.episodeInfo)
        assertEquals(
            "https://animevost.org/tip/tv/3970-mujikaku-seijo-wa-kyou-mo-muishiki-ni-chikara-wo-tare-nagasu.html",
            anime.url,
        )
        assertEquals("https://animevost.org/uploads/posts/2026-06/1782839996_1.jpg", anime.posterUrl)
        assertEquals("7 июль 2026", anime.publishedDate)
        assertEquals(88220, anime.viewCount)
        assertEquals(42, anime.commentCount)
        assertEquals(4.0, anime.rating)
        assertEquals(130, anime.voteCount)
        assertEquals(listOf("ТВ", "Онгоинги", "2026"), anime.categories.map { it.title })
        assertEquals("https://animevost.org/tip/tv/", anime.categories.first().url)
    }

    @Test
    fun `falls back to heading links when shortstory wrapper changes`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <section class="release-card">
                      <h2>
                        <a href="/tip/tv/4123-fallback-title.html">
                          Тестовый релиз / Test Release [1-4 из 12]
                        </a>
                      </h2>
                      <div class="staticInfo">
                        <span class="staticInfoLeftData">25 август 2026</span>
                        <span class="staticInfoRightSmotr">12345</span>
                      </div>
                      <div class="shortstoryContent">
                        <img src="/uploads/posts/test.jpg" />
                      </div>
                    </section>
                  </body>
                </html>
            """.trimIndent(),
        )

        val anime = page.items.single()
        assertEquals(4123, anime.id)
        assertEquals("Тестовый релиз", anime.title)
        assertEquals("Test Release", anime.originalTitle)
        assertEquals("1-4 из 12", anime.episodeInfo)
        assertEquals("https://animevost.org/uploads/posts/test.jpg", anime.posterUrl)
        assertEquals(12345, anime.viewCount)
    }

    @Test
    fun `handles titles without original title or counters`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="shortstory">
                      <div class="shortstoryHead">
                        <h2><a href="/tip/ova/12-test.html">Только русское название</a></h2>
                      </div>
                      <div class="staticInfo">
                        <div class="staticInfoRight"><span></span></div>
                      </div>
                      <div class="shortstoryContent"></div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        val anime = page.items.single()
        assertEquals(12, anime.id)
        assertEquals("Только русское название", anime.title)
        assertNull(anime.originalTitle)
        assertNull(anime.episodeInfo)
        assertNull(anime.posterUrl)
        assertNull(anime.publishedDate)
        assertNull(anime.viewCount)
        assertNull(anime.commentCount)
        assertNull(anime.rating)
        assertNull(anime.voteCount)
    }

    @Test
    fun `parses active and total pagination`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="shortstory">
                      <div class="shortstoryHead"><h2><a href="/tip/tv/1-a.html">A</a></h2></div>
                    </div>
                    <div class="navigation">
                      <a href="/page/1/">1</a>
                      <span class="dle_active">2</span>
                      <a href="/page/3/">3</a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(2, page.currentPage)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun `parses live block pagination`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="shortstory">
                      <div class="shortstoryHead"><h2><a href="/tip/tv/1-a.html">A</a></h2></div>
                    </div>
                    <table>
                      <tr>
                        <td class="block_4">
                          <a href="https://animevost.org/">1</a>
                          <span>2</span>
                          <a href="https://animevost.org/page/3/">3</a>
                          <span class="nav_ext">...</span>
                          <a href="https://animevost.org/page/356/">356</a>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(2, page.currentPage)
        assertEquals(356, page.totalPages)
    }

    @Test
    fun `uses lazy poster attributes instead of placeholder src`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="shortstory">
                      <div class="shortstoryHead">
                        <h2><a href="/tip/tv/5000-lazy-poster.html">Ленивый постер / Lazy Poster</a></h2>
                      </div>
                      <div class="shortstoryContent">
                        <img src="/templates/AnimeVost/images/loading.gif"
                             data-src="//cdn.animevost.org/uploads/posts/lazy-poster.jpg" />
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(
            "https://cdn.animevost.org/uploads/posts/lazy-poster.jpg",
            page.items.single().posterUrl,
        )
    }

}
