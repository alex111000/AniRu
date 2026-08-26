package com.animevost.sdk.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoSourceParserTest {

    private val parser = VideoSourceParser()

    @Test
    fun `parses player frame sources with quality and download urls`() {
        val sources = parser.parse(
            """
                <div id="dow">
                  <a class="butt" download="invoice" href="https://download.test/100443228.mp4?d=1">480p (SD)</a>
                  <a class="butt" download="invoice" href="https://download.test/720/100443228.mp4?d=1">720p (HD)</a>
                </div>
                <script>
                  var player = new Playerjs({
                    "file":"[SD (480p)]https://std.roomfish.ru/100443228.mp4?md5=token&time=1 or https://ram.roomfish.ru/100443228.mp4,[HD (720р)]https://hd.roomfish.ru/720/100443228.mp4?md5=token&time=1,[FHD (1080p)]https://fhd.roomfish.ru/1080/100443228.mp4?md5=token&time=1",
                    "default_quality":"SD (480p)"
                  });
                </script>
            """.trimIndent(),
        )

        assertEquals(3, sources.size)
        assertEquals("SD (480p)", sources[0].quality)
        assertEquals("https://std.roomfish.ru/100443228.mp4?md5=token&time=1", sources[0].url)
        assertEquals("https://download.test/100443228.mp4?d=1", sources[0].downloadUrl)
        assertEquals("std.roomfish.ru", sources[0].host)

        assertEquals("HD (720p)", sources[1].quality)
        assertEquals("https://hd.roomfish.ru/720/100443228.mp4?md5=token&time=1", sources[1].url)
        assertEquals("https://download.test/720/100443228.mp4?d=1", sources[1].downloadUrl)

        assertEquals("FHD (1080p)", sources[2].quality)
        assertNull(sources[2].downloadUrl)
    }

    @Test
    fun `falls back to download links when player file is missing`() {
        val sources = parser.parse(
            """
                <a class="butt" download="invoice" href="https://download.test/100443228.mp4?d=1">480p (SD)</a>
                <a class="butt" download="invoice" href="https://download.test/720/100443228.mp4?d=1">720p (HD)</a>
            """.trimIndent(),
        )

        assertEquals(2, sources.size)
        assertEquals("480p (SD)", sources[0].quality)
        assertEquals("https://download.test/100443228.mp4", sources[0].url)
        assertEquals("https://download.test/100443228.mp4?d=1", sources[0].downloadUrl)
        assertEquals("download.test", sources[0].host)
    }

    @Test
    fun `keeps plain getlink response support`() {
        val sources = parser.parse(
            """
                https://std.roomfish.ru/100443228.mp4?md5=token&time=1783448470 or
                https://ram.roomfish.ru/100443228.mp4?md5=token&time=1783448470&ip=127.0.0.1
            """.trimIndent(),
        )

        assertEquals(2, sources.size)
        assertEquals(listOf("default", "default"), sources.map { it.quality })
        assertEquals("https://std.roomfish.ru/100443228.mp4?md5=token&time=1783448470", sources[0].url)
        assertNull(sources[0].downloadUrl)
    }

    @Test
    fun `ignores malformed and empty entries`() {
        val sources = parser.parse("broken or  or https://std.roomfish.ru/1.mp4")

        assertEquals(listOf("https://std.roomfish.ru/1.mp4"), sources.map { it.url })
    }
}
