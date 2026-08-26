package com.animevost.sdk.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RandomAnimeParserTest {

    private val parser = RandomAnimeParser()

    @Test
    fun `parses random anime fragment`() {
        val anime = parser.parse(
            html = """
                <div class="imgOngoing">
                  <div class="imgOngoingVie">Просмотров: 827381&nbsp;|&nbsp; Комментарий: 104</div>
                  <a href="/tip/tv/1171-plach-asury-asura-cryin-1-13-iz-131.html">
                    <span>Плач Асуры / Asura Cryin</span>
                  </a>
                  <img src="/uploads/posts/2014-12/1419617554_1.jpg" alt="Плач Асуры / Asura Cryin" />
                </div>
            """.trimIndent(),
        )

        requireNotNull(anime)
        assertEquals(1171, anime.id)
        assertEquals("Плач Асуры", anime.title)
        assertEquals("Asura Cryin", anime.originalTitle)
        assertNull(anime.episodeInfo)
        assertEquals("https://animevost.org/tip/tv/1171-plach-asury-asura-cryin-1-13-iz-131.html", anime.url)
        assertEquals("https://animevost.org/uploads/posts/2014-12/1419617554_1.jpg", anime.posterUrl)
        assertEquals(827381, anime.viewCount)
        assertEquals(104, anime.commentCount)
        assertEquals(emptyList(), anime.categories)
    }

    @Test
    fun `returns null when fragment has no anime link`() {
        assertNull(parser.parse("<div></div>"))
    }
}
