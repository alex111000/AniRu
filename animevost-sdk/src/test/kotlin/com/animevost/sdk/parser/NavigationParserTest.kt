package com.animevost.sdk.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationParserTest {

    private val parser = NavigationParser()

    @Test
    fun `parses catalog navigation links from top menu`() {
        val navigation = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="menu">
                      <ul id="topnav">
                        <li><a href="/">Главная</a></li>
                        <li><a href="/zhanr/">Жанр</a>
                          <div class="sar">
                            <span id="blok_1">
                              <a href="/zhanr/boyevyye-iskusstva/">Боевые искусства</a>
                              <a href="/zhanr/romantika/">Романтика</a>
                            </span>
                          </div>
                        </li>
                        <li><a href="/tip/">Категории</a>
                          <span class="sar">
                            <span id="blok_4">
                              <a href="/tip/tv/">ТВ</a>
                              <a href="/tip/ova/">OVA</a>
                            </span>
                          </span>
                        </li>
                        <li><a href="/god/">Год</a>
                          <span class="sar">
                            <span id="blok_19">
                              <a href="/god/2024/">2024</a>
                              <a href="/god/2026/">2026</a>
                            </span>
                          </span>
                        </li>
                        <li><a href="/ongoing/">Онгоинги</a></li>
                        <li><a href="/preview/">Анонсы</a></li>
                        <li><a href="/index.php?do=feedback">Контакты</a></li>
                      </ul>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(listOf("Боевые искусства", "Романтика"), navigation.genres.map { it.title })
        assertEquals("https://animevost.org/zhanr/boyevyye-iskusstva/", navigation.genres.first().url)
        assertEquals("zhanr/boyevyye-iskusstva/", navigation.genres.first().path)

        assertEquals(listOf("ТВ", "OVA"), navigation.types.map { it.title })
        assertEquals(listOf("2026", "2024"), navigation.years.map { it.title })
        assertEquals(listOf("Онгоинги", "Анонсы"), navigation.sections.map { it.title })
        assertEquals("ongoing/", navigation.sections.first().path)
    }

    @Test
    fun `returns empty navigation when menu is missing`() {
        val navigation = parser.parse("<html></html>")

        assertEquals(emptyList(), navigation.genres)
        assertEquals(emptyList(), navigation.types)
        assertEquals(emptyList(), navigation.years)
        assertEquals(emptyList(), navigation.sections)
    }
}
