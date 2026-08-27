package ru.radiationx.anilibria.provider

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import ru.radiationx.anilibria.provider.impl.*

/** Offline contract fixtures; these do NOT claim live website availability. */
class ProviderAdaptersTest {
    private fun http(reply: (String) -> String): ProviderHttpClient = ProviderHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(reply(chain.request().url.toString()).toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun `AniLiberty full catalog and direct streams`() = runBlocking {
        val item = """{"id":20,"name":{"main":"Наруто","english":"Naruto"},"year":2002,"type":{"value":"TV","description":"ТВ"},"poster":{"src":"/poster.webp"},"genres":[{"name":"Экшен"}],"episodes":[{"id":"uuid","ordinal":1,"hls_1080":"https://cdn.test/1080.m3u8","hls_720":"https://cdn.test/720.m3u8"}]}"""
        val provider = AniLibriaProvider(http { if (it.contains("catalog")) """{"data":[$item]}""" else item })
        assertEquals(AnimeKind.SERIES, provider.browse(1).single().kind)
        assertEquals("https://aniliberty.top/poster.webp", provider.browse(1).single().posterUrl)
        assertEquals("1", provider.getDetails("20").episodes.single().numberLabel)
        assertEquals(listOf(1080, 720), provider.getSources("20", "uuid").single().streams.map { it.quality })
    }
    @Test fun `AniLiberty geo blocked content is not resolved`() = runBlocking {
        val provider = AniLibriaProvider(http { """{"is_blocked_by_geo":true,"episodes":[{"id":"uuid","hls_1080":"https://cdn.test/full.m3u8"}]}""" })
        assertTrue(provider.getSources("20", "uuid").isEmpty())
    }
    @Test fun `AnimeLib keeps season and fractional episode identities`() = runBlocking {
        val api = http { url -> when {
            url.contains("/episodes?") -> """{"data":[{"id":11,"number":"1","season":"2","name":"Episode"},{"id":12,"number":"1.5","season":"2","name":"Special"}]}"""
            url.contains("/anime/") -> """{"data":{"slug_url":"20--naruto","rus_name":"Наруто","name":"Naruto","releaseDate":"2002-10-03","type":{"label":"TV"},"shikimori_href":"https://shikimori.one/animes/20-naruto"}}"""
            else -> """{"data":[]}"""
        } }
        val provider = AnimeLibProvider(api, EmbeddedPlayerResolver(api))
        val details = provider.getDetails("20--naruto")
        assertEquals("20", details.externalIds["shikimori"])
        assertEquals(2, details.episodes.first().season)
        assertTrue(details.episodes.last().special)
        assertEquals("1.5", details.episodes.last().numberLabel)
    }
    @Test fun `AnimeGo movie player is mapped to a movie not a numbered series`() = runBlocking {
        val api = http { url -> when {
            url.contains("/player/") -> """{"data":{"content":"<button data-player='https://cdn.test/film.mp4' data-translation-title='Studio'></button>"}}"""
            else -> """<h1>Film</h1><script type="application/ld+json">{"@type":"Movie","name":"Film","datePublished":"2024-01-01","image":"/film.jpg","genre":["Drama"]}</script>"""
        } }
        val provider = AnimeGoProvider(api, EmbeddedPlayerResolver(api))
        val details = provider.getDetails("https://animego.me/anime/film-123")
        assertEquals(AnimeKind.MOVIE, details.kind)
        assertEquals("movie", details.episodes.single().id)
        assertEquals("Studio", provider.getSources(details.id, "movie").single().title)
    }
    @Test fun `Yummy full catalog is paged locally without dropping older titles`() = runBlocking {
        val data = (1..75).joinToString(",") { """{"anime_id":$it,"title":"Anime $it","year":2024,"type":{"name":"TV"}}""" }
        var requests = 0
        val api = http { requests++; """{"response":{"data":[$data]}}""" }
        val provider = YummyAnimeProvider(api, EmbeddedPlayerResolver(api))
        assertEquals(60, provider.browse(1).size)
        assertEquals(15, provider.browse(2).size)
        assertTrue(provider.browse(3).isEmpty())
        assertEquals(1, requests)
    }
    @Test fun `HDRezka search excludes non anime cartoons`() = runBlocking {
        val html = """<div class="b-content__inline_item"><div class="b-content__inline_item-link"><a href="https://hdrezka-home.tv/animation/a.html">Cartoon</a><div>2024, США</div></div></div>
          <div class="b-content__inline_item"><div class="b-content__inline_item-link"><a href="https://hdrezka-home.tv/animation/b.html">Anime</a><div>2024, Япония</div></div></div>"""
        val provider = HdRezkaProvider(http { html })
        assertEquals(listOf("Anime"), provider.search("test").map { it.title })
    }
    @Test fun `DreamersCast catalog preserves film type and year`() = runBlocking {
        val provider = DreamCastProvider(http { """{"releases":[{"url":"/home/release/1","russian":"Фильм","original":"Film","type":"Movie","dateissue":1998,"image":"//cdn.test/poster.webp","genres":"Драма, Фэнтези"}]}""" })
        val item = provider.browse(1).single()
        assertEquals(AnimeKind.MOVIE, item.kind)
        assertEquals("1998", item.year)
        assertEquals("https://cdn.test/poster.webp", item.posterUrl)
    }
    @Test fun `embedded player never treats arbitrary page adverts as an episode`() = runBlocking {
        val resolver = EmbeddedPlayerResolver(http { """<script>var advertisement='https://cdn.test/ad.mp4';</script>""" })
        assertTrue(resolver.resolve("https://unknown.test/embed", "https://animego.me", "Voice").isEmpty())
    }
    @Test fun `direct qualities and alternatives preserve headers`() {
        val streams = parseMediaList("[1080p]https://cdn.test/a.m3u8 or https://cdn.test/a.mp4,[720p]https://cdn.test/b.mp4", "https://site.test/", "Voice")
        assertEquals(listOf(1080, 1080, 720), streams.map { it.quality })
        assertEquals("https://site.test/", streams.first().headers["Referer"])
        assertNull(mediaStream("https://site.test/player.html", "https://site.test/", "Voice"))
    }
}
