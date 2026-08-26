package com.animevost.sdk.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritesParserTest {

    private val parser = FavoritesParser()

    @Test
    fun `parses favorite shortstory items using share id`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="shortstory">
                      <a class="shortstoryShare" id="fav-id-3970"></a>
                      <div class="shortstoryHead">
                        <h2>
                          <a href="/tip/tv/999-wrong-url-id.html">
                            Забывчивая святая дева / Mujikaku Seijo [1-2 из 12+]
                          </a>
                        </h2>
                      </div>
                      <div class="staticInfo">
                        <span class="staticInfoLeftData">7 июль 2026</span>
                        <span class="staticInfoRightSmotr">88220</span>
                        <a href="/tip/tv/999-wrong-url-id.html#comment">43</a>
                      </div>
                      <div class="shortstoryContent">
                        <img class="imgRadius" src="/uploads/posts/2026-06/poster.jpg" />
                      </div>
                      <div class="shortstoryFuter">
                        <span>
                          <a href="/tip/tv/">ТВ</a>
                          <a href="/ongoing/">Онгоинги</a>
                          <a href="/god/2026/">2026</a>
                        </span>
                      </div>
                    </div>
                    <table>
                      <tr>
                        <td class="block_4">
                          <span>1</span>
                          <a href="https://animevost.org/favorites/page/2/">2</a>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(1, page.currentPage)
        assertEquals(2, page.totalPages)

        val favorite = page.items.single()
        assertEquals(3970, favorite.id)
        assertEquals("Забывчивая святая дева", favorite.title)
        assertEquals("Mujikaku Seijo", favorite.originalTitle)
        assertEquals("1-2 из 12+", favorite.episodeInfo)
        assertEquals("https://animevost.org/tip/tv/999-wrong-url-id.html", favorite.url)
        assertEquals("https://animevost.org/uploads/posts/2026-06/poster.jpg", favorite.posterUrl)
        assertEquals(88220, favorite.viewCount)
        assertEquals(43, favorite.commentCount)
        assertEquals(listOf("ТВ", "Онгоинги", "2026"), favorite.categories.map { it.title })
    }

    @Test
    fun `skips shortstory without favorite marker`() {
        val page = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="shortstory">
                      <div class="shortstoryHead">
                        <h2><a href="/tip/tv/1-test.html">Test</a></h2>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(emptyList(), page.items)
    }
}
