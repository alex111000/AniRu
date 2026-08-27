package ru.radiationx.anilibria.provider.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ru.radiationx.anilibria.provider.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class AnimeGoProvider @Inject constructor(private val http: ProviderHttpClient, private val players: EmbeddedPlayerResolver) : AnimeProvider {
    override val id = ProviderId.ANIMEGO
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(browse = true, multipleVoices = true)
    private val moviePlayers = ConcurrentHashMap<String, String>()
    private val next = ConcurrentHashMap<Int, String>()
    override suspend fun search(query: String) = cards(http.get("$SITE/search/anime?q=${encodeValue(query)}"))
    override suspend fun browse(page: Int): List<ProviderAnime> {
        val url = if (page == 1) "$SITE/anime" else next[page] ?: return emptyList()
        val doc = Jsoup.parse(http.get(url), SITE)
        doc.select("a[href]").firstOrNull { it.text().trim() == (page + 1).toString() || it.attr("rel") == "next" }
            ?.absUrl("href")?.takeIf { it.startsWith(SITE) }?.let { next[page + 1] = it }
        return cards(doc.outerHtml())
    }
    private fun cards(html: String): List<ProviderAnime> = Jsoup.parse(html, SITE)
        .select(".ani-grid__item, .animes-list-item").mapNotNull { node ->
            val anchor = node.selectFirst(".ani-grid__item-title a, .h5 a, a[href*=/anime/]") ?: return@mapNotNull null
            val image = node.selectFirst("img")
            ProviderAnime(id, anchor.absUrl("href"), anchor.text().ifBlank { image?.attr("alt").orEmpty() },
                posterUrl = image?.absUrl("src").orEmpty(),
                year = Regex("(?:19|20)\\d{2}").find(node.text())?.value.orEmpty(),
                kind = AnimeKind.parse(node.text()),
                genres = node.select("a[href*=genre]").map { it.text() })
        }.distinctBy { it.id }
    override suspend fun catalogMetadata(item: ProviderAnime): ProviderAnime {
        val doc = Jsoup.parse(http.get(item.id), SITE)
        val ld = doc.select("script[type=application/ld+json]").firstNotNullOfOrNull { runCatching { JSONObject(it.data()) }.getOrNull() } ?: return item
        return item.copy(originalTitle = ld.optString("alternateName"), year = ld.optString("datePublished").take(4),
            kind = when { ld.optString("@type").contains("Movie", true) -> AnimeKind.MOVIE
                ld.optString("@type").contains("Series", true) -> AnimeKind.SERIES
                else -> AnimeKind.parse(doc.select(".entity__info, .anime-info").text()) },
            genres = ld.optJSONArray("genre")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty())
    }
    private suspend fun playerDoc(path: String): Document {
        val raw = http.get("$SITE$path", mapOf("Referer" to SITE, "X-Requested-With" to "XMLHttpRequest"))
        val body = if (raw.trimStart().startsWith("{")) JSONObject(raw).optJSONObject("data")?.optString("content").orEmpty() else raw
        return Jsoup.parse(body, SITE)
    }
    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        require(animeId.startsWith("$SITE/anime/"))
        val doc = Jsoup.parse(http.get(animeId), SITE)
        val ld = doc.select("script[type=application/ld+json]").firstNotNullOfOrNull { runCatching { JSONObject(it.data()) }.getOrNull() } ?: JSONObject()
        val key = Regex("-(\\d+)(?:/)?$").find(animeId)?.groupValues?.get(1) ?: throw java.io.IOException("Не найден ID AnimeGo")
        val player = playerDoc("/player/$key")
        val kind = if (ld.optString("@type").contains("Movie", true)) AnimeKind.MOVIE else AnimeKind.parse(doc.select(".entity__info, .anime-info").text()).let {
            if (it == AnimeKind.UNKNOWN && ld.optInt("numberOfEpisodes") > 0) AnimeKind.SERIES else it
        }
        var episodes = player.select(".player-video-bar__item[data-episode]").map { ep ->
            val number = ep.attr("data-episode-number")
            ProviderEpisode(ep.attr("data-episode"), number.toIntOrNull(), ep.attr("data-episode-title").ifBlank { "Серия $number" },
                special = ep.attr("data-episode-type").let { it.isNotBlank() && it != "1" }, numberLabel = number)
        }.distinctBy { it.id }
        if (episodes.isEmpty() && kind == AnimeKind.MOVIE && player.select("button[data-player]").isNotEmpty()) {
            moviePlayers[animeId] = player.outerHtml()
            episodes = listOf(ProviderEpisode("movie", 1, "Смотреть фильм"))
        }
        return ProviderAnimeDetails(id, animeId, ld.optString("name").ifBlank { doc.select("h1").text() },
            ld.optString("alternateName"), ld.optString("description"), absolute(SITE, ld.optString("image")),
            ld.optString("datePublished").take(4), displayName,
            ld.optJSONArray("genre")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty(),
            episodes, kind, ld.optJSONObject("aggregateRating")?.optDouble("ratingValue")?.takeIf { it.isFinite() })
    }
    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> = supervisorScope {
        val doc = if (episodeId == "movie") {
            if (!moviePlayers.containsKey(animeId)) getDetails(animeId)
            Jsoup.parse(moviePlayers[animeId].orEmpty(), SITE)
        } else playerDoc("/player/videos/${encodeValue(episodeId)}")
        val voices = doc.select("button[data-translation]").associate { it.attr("data-translation") to it.text() }
        val limit = Semaphore(3)
        doc.select("button[data-player]").map { button -> async { limit.withPermit {
            try {
                val voice = button.attr("data-translation-title").ifBlank { voices[button.attr("data-ptranslation")].orEmpty() }.ifBlank { "Не указана" }
                val url = absolute(SITE, button.attr("data-player"))
                val streams = withTimeoutOrNull(5_000) { players.resolve(url, SITE, voice) }.orEmpty()
                if (streams.isEmpty()) null else ProviderSource(url, voice, button.attr("data-provider-title"), streams)
            } catch (error: Exception) { currentCoroutineContext().ensureActive(); null }
        } } }.awaitAll().filterNotNull()
    }
    override suspend fun isAvailable() = runCatching { browse(1).isNotEmpty() }.getOrDefault(false)
    private companion object { const val SITE = "https://animego.me" }
}
