package com.animevost.sdk.parser

import com.animevost.sdk.model.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleParserTest {

    private val parser = ScheduleParser()

    @Test
    fun `parses anime entries from schedule containers`() {
        val schedule = parser.parse(
            html = """
                <html>
                  <body>
                    <a href="javascript:ShowOrHide('raspisSun')">Воскресенье</a>
                    <div id="raspisSun" class="raspis">
                      <a href="/tip/tv/3918-seihantai-na-kimi-to-boku-2nd-season.html">
                        Ты и я - полные противоположности (второй сезон) ~ (14:30)
                      </a>
                      <a href="/tip/tv/3752-guangyin-zhi-wai.html">
                        За гранью времени ~ (В течение дня)
                      </a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(1, schedule.size)
        assertEquals(Weekday.SUNDAY, schedule.single().weekday)

        val entries = schedule.single().entries
        assertEquals(2, entries.size)

        assertEquals("Ты и я - полные противоположности (второй сезон)", entries[0].title)
        assertEquals("https://animevost.org/tip/tv/3918-seihantai-na-kimi-to-boku-2nd-season.html", entries[0].url)
        assertEquals("14:30", entries[0].timeLabel)
        assertNull(entries[0].posterUrl)

        assertEquals("За гранью времени", entries[1].title)
        assertEquals("https://animevost.org/tip/tv/3752-guangyin-zhi-wai.html", entries[1].url)
        assertEquals("В течение дня", entries[1].timeLabel)
    }

    @Test
    fun `captures schedule poster when markup contains an image`() {
        val schedule = parser.parse(
            html = """
                <html>
                  <body>
                    <div id="raspisMon" class="raspis">
                      <a href="/tip/tv/179-one-piece44.html">
                        <img data-src="/uploads/posts/one-piece.jpg" />
                        Ван Пис ~ (20:00)
                      </a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        val entry = schedule.single().entries.single()
        assertEquals("Ван Пис", entry.title)
        assertEquals("https://animevost.org/uploads/posts/one-piece.jpg", entry.posterUrl)
    }

    @Test
    fun `ignores non anime links inside schedule containers`() {
        val schedule = parser.parse(
            html = """
                <html>
                  <body>
                    <div id="raspisMon" class="raspis">
                      <a href="/">Главная</a>
                      <a href="javascript:void(0)">Показать</a>
                      <a href="/tip/tv/179-one-piece44.html">Ван Пис ~ (20:00)</a>
                      <a href="https://animevost.org/">AnimeVost</a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        val entries = schedule.single().entries
        assertEquals(1, entries.size)
        assertEquals("Ван Пис", entries.single().title)
        assertEquals("https://animevost.org/tip/tv/179-one-piece44.html", entries.single().url)
    }

    @Test
    fun `keeps present days even when they have no anime entries`() {
        val schedule = parser.parse(
            html = """
                <html>
                  <body>
                    <div id="raspisSat" class="raspis"></div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(1, schedule.size)
        assertEquals(Weekday.SATURDAY, schedule.single().weekday)
        assertEquals(emptyList(), schedule.single().entries)
    }

    @Test
    fun `captures highest srcset schedule poster`() {
        val schedule = parser.parse(
            html = """
                <html>
                  <body>
                    <div id="raspisTue" class="raspis">
                      <a href="/tip/tv/5011-srcset-test.html">
                        <img src="/templates/AnimeVost/images/loading.gif"
                             srcset="/uploads/posts/small.jpg 1x, /uploads/posts/large.jpg 2x" />
                        Тест srcset ~ (19:00)
                      </a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(
            "https://animevost.org/uploads/posts/large.jpg",
            schedule.single().entries.single().posterUrl,
        )
    }

}
