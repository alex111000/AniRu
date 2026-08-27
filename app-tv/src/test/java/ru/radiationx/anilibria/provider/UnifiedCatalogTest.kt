package ru.radiationx.anilibria.provider

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UnifiedCatalogTest {
    private fun anime(provider: ProviderId, title: String = "Наруто", year: String = "2002", kind: AnimeKind = AnimeKind.SERIES) =
        ProviderAnime(provider, "123", title, originalTitle = "Naruto", year = year, kind = kind)

    @Test fun `same anime across providers merges`() {
        assertTrue(AnimeIdentity.same(anime(ProviderId.ANIMEVOST), anime(ProviderId.ANILIBRIA)))
    }
    @Test fun `different year is never merged`() {
        assertFalse(AnimeIdentity.same(anime(ProviderId.ANIMEVOST), anime(ProviderId.ANILIBRIA, year = "2007")))
    }
    @Test fun `film and series stay separate`() {
        assertFalse(AnimeIdentity.same(anime(ProviderId.ANIMEVOST), anime(ProviderId.ANILIBRIA, kind = AnimeKind.MOVIE)))
    }
    @Test fun `unknown year does not permit a name-only merge`() {
        assertFalse(AnimeIdentity.same(anime(ProviderId.ANIMEVOST), anime(ProviderId.ANILIBRIA, year = "")))
    }
    @Test fun `strong ids merge alternate names`() {
        val one = anime(ProviderId.ANILIBRIA).copy(externalIds = mapOf("shikimori" to "20"))
        val two = anime(ProviderId.ANILIB).copy(title = "Alternate", originalTitle = "", externalIds = mapOf("shikimori" to "20"))
        assertTrue(AnimeIdentity.same(one, two))
    }
    @Test fun `conflicting strong ids do not merge`() {
        assertFalse(AnimeIdentity.same(anime(ProviderId.ANILIBRIA).copy(externalIds = mapOf("shikimori" to "20")),
            anime(ProviderId.ANILIB).copy(externalIds = mapOf("shikimori" to "21"))))
    }
    @Test fun `default sorting puts recently added old anime first`() {
        val old = UnifiedAnime(listOf(anime(ProviderId.ANILIBRIA, year = "1998").copy(addedAt = 300)), 10)
        val recent = UnifiedAnime(listOf(anime(ProviderId.ANIMEVOST, year = "2026").copy(addedAt = 100)), 20)
        assertEquals(listOf(old, recent), listOf(recent, old).ordered(CatalogOrder.ADDED))
    }
    @Test fun `year breaks equal added-time ties`() {
        val old = UnifiedAnime(listOf(anime(ProviderId.ANILIBRIA, year = "1998")), 10)
        val recent = UnifiedAnime(listOf(anime(ProviderId.ANIMEVOST, year = "2026")), 10)
        assertEquals(listOf(recent, old), listOf(old, recent).ordered(CatalogOrder.ADDED))
    }
    @Test fun `new provider copy does not bump canonical added date`() {
        val item = UnifiedAnime(listOf(anime(ProviderId.ANILIBRIA).copy(addedAt = 100), anime(ProviderId.ANIMEVOST).copy(addedAt = 300)), 400)
        assertEquals(100L, item.addedAt)
    }
    @Test fun `season and special episodes cannot be substituted`() {
        val episode = ProviderEpisode("one", 1, "Episode 1")
        assertFalse(AnimeIdentity.sameEpisode(episode, episode.copy(id = "two", season = 2)))
        assertFalse(AnimeIdentity.sameEpisode(episode, episode.copy(id = "special", special = true)))
        assertTrue(AnimeIdentity.sameEpisode(episode, episode.copy(id = "other-provider-id")))
        assertFalse(AnimeIdentity.sameEpisode(episode, episode.copy(numberLabel = "1.5")))
    }
    @Test fun `type parsing keeps non anime and missing metadata unknown`() {
        assertEquals(AnimeKind.MOVIE, AnimeKind.parse("Полнометражный фильм"))
        assertEquals(AnimeKind.SERIES, AnimeKind.parse("TV"))
        assertEquals(AnimeKind.UNKNOWN, AnimeKind.parse(""))
        assertEquals(AnimeKind.UNKNOWN, AnimeKind.parse("Persona"))
        assertEquals(AnimeKind.UNKNOWN, AnimeKind.parse("Битва"))
    }
    @Test fun `dates accept ISO timezone and reject missing dates`() {
        assertEquals(parseProviderDate("2026-08-27T10:00:00Z"), parseProviderDate("2026-08-27T13:00:00+03:00"))
        assertEquals(0L, parseProviderDate("unknown"))
        assertEquals(parseProviderDate("2026-07-07"), parseProviderDate("7 июль 2026"))
    }
    private fun source(voice: String = "AniLibria", quality: Int = 1080) = ResolvedSource(ProviderId.ANILIBRIA, "1", "ep1",
        ProviderSource("$voice:$quality", voice, "test", listOf(ProviderStream("https://example.test/$quality.m3u8", quality, StreamType.HLS))))
    @Test fun `fast source does not wait for a hung provider`() = runTest {
        var cancelled = false
        val result = boundedSourceRace(listOf(
            suspend { delay(100); listOf(source()) },
            suspend { try { awaitCancellation() } finally { cancelled = true } },
        ), 6000, 450, null)
        assertEquals(550L, testScheduler.currentTime)
        assertEquals(1, result.size)
        assertTrue(cancelled)
    }
    @Test fun `better quality arriving in grace window is included`() = runTest {
        val result = boundedSourceRace(listOf(suspend { delay(100); listOf(source(quality = 720)) },
            suspend { delay(300); listOf(source(quality = 1080)) }), 6000, 450, null)
        assertEquals(1080, result.flatMap { it.source.streams }.maxOf { it.quality })
    }
    @Test fun `saved dubbing is given a chance instead of immediate substitution`() = runTest {
        val result = boundedSourceRace(listOf(suspend { delay(50); listOf(source("Other")) },
            suspend { delay(2000); listOf(source("Preferred")) }), 6000, 450, "Preferred")
        assertTrue(result.any { it.source.title == "Preferred" })
        assertEquals(2000L, testScheduler.currentTime)
    }
    @Test fun `all hung sources stop at deadline`() = runTest {
        val result = boundedSourceRace(listOf(suspend { awaitCancellation() }), 6000, 450, null)
        assertTrue(result.isEmpty())
        assertEquals(6000L, testScheduler.currentTime)
    }
    @Test fun `caller cancellation is not swallowed`() = runTest {
        val job = launch { boundedSourceRace(listOf(suspend { awaitCancellation() }), 6000, 450, null) }
        testScheduler.runCurrent(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
    @Test fun `title details use a fast verified copy and cancel the stalled primary`() = runTest {
        var cancelled = false
        val result = firstAvailable(listOf(
            suspend { try { awaitCancellation() } finally { cancelled = true } },
            suspend { delay(120); "available copy" },
        ), 8_000)
        assertEquals("available copy", result)
        assertEquals(120L, testScheduler.currentTime)
        assertTrue(cancelled)
    }
    @Test fun `failed detail sources do not block the remaining candidates`() = runTest {
        val result = firstAvailable<String>(listOf(
            suspend { throw java.io.IOException("offline") },
            suspend { null },
            suspend { delay(200); "details" },
        ), 8_000, concurrency = 2)
        assertEquals("details", result)
    }
    @Test fun `detail race deadline stops all hung requests`() = runTest {
        assertNull(firstAvailable<String>(listOf(suspend { awaitCancellation() }), 8_000))
        assertEquals(8_000L, testScheduler.currentTime)
    }
    @Test fun `detail race respects caller cancellation`() = runTest {
        val job = launch { firstAvailable<String>(listOf(suspend { awaitCancellation() }), 8_000) }
        testScheduler.runCurrent(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
