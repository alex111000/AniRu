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

/** Anime genre only. Authentication/challenge pages are not bypassed. */
class HdRezkaProvider @Inject constructor(private val http: ProviderHttpClient) : AnimeProvider {
    override val id = ProviderId.HDREZKA
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(browse = true, multipleVoices = true)
    private data class Page(val doc: Document, val id: String, val translator: String, val movie: Boolean)
    private val pages = ConcurrentHashMap<String, Pair<Long, Page>>()
    private val next = ConcurrentHashMap<Int, String>()
    override suspend fun browse(page: Int): List<ProviderAnime> {
        val url = if (page == 1) "$SITE/?filter=last&genre=82" else next[page] ?: return emptyList()
        val doc = Jsoup.parse(http.get(url), SITE)
        doc.select("a[href]").firstOrNull { it.text().trim() == (page + 1).toString() && it.attr("href").contains("page") }
            ?.absUrl("href")?.takeIf { it.startsWith(SITE) }?.let { next[page + 1] = it }
        return cards(doc, animeGenre = true)
    }
    override suspend fun search(query: String): List<ProviderAnime> = cards(Jsoup.parse(http.get(
        "$SITE/search/?do=search&subaction=search&q=${encodeValue(query)}"), SITE), false)
    private fun cards(doc: Document, animeGenre: Boolean): List<ProviderAnime> = doc.select(".b-content__inline_item").mapNotNull { node ->
        val anchor = node.selectFirst(".b-content__inline_item-link a") ?: return@mapNotNull null
        val url = anchor.absUrl("href")
        if (!url.contains("/animation/")) return@mapNotNull null
        // /animation/ alone also contains non-anime cartoons. Search must prove anime/Japan.
        if (!animeGenre && !Regex("аниме|Япония", RegexOption.IGNORE_CASE).containsMatchIn(node.text())) return@mapNotNull null
        val kind = when {
            node.select(".cat.series, .cat.tv").isNotEmpty() || node.select("span.info").text().contains("сезон") -> AnimeKind.SERIES
            node.select(".cat.film, .cat.movie").isNotEmpty() -> AnimeKind.MOVIE
            else -> AnimeKind.UNKNOWN
        }
        ProviderAnime(id, url, anchor.text(), posterUrl = node.selectFirst("img")?.absUrl("src").orEmpty(),
            year = Regex("(?:19|20)\\d{2}").find(node.select(".b-content__inline_item-link").text())?.value.orEmpty(),
            kind = kind)
    }
    private suspend fun page(animeId: String): Page {
        pages[animeId]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second }
        require(animeId.startsWith("$SITE/animation/"))
        val doc = Jsoup.parse(http.get(animeId), SITE)
        val info = doc.select(".b-post__info").text()
        require(Regex("аниме|Япония", RegexOption.IGNORE_CASE).containsMatchIn(info)) { "Только аниме" }
        val match = Regex("initCDN(Series|Movies)Events\\(\\s*(\\d+)\\s*,\\s*(\\d+)").find(doc.html())
            ?: throw java.io.IOException("Плеер HDRezka недоступен")
        return Page(doc, match.groupValues[2], match.groupValues[3], match.groupValues[1] == "Movies")
            .also { pages[animeId] = System.currentTimeMillis() to it }
    }
    override suspend fun catalogMetadata(item: ProviderAnime): ProviderAnime {
        val page = page(item.id)
        return item.copy(kind = if (page.movie) AnimeKind.MOVIE else AnimeKind.SERIES,
            originalTitle = page.doc.select(".b-post__origtitle").text(),
            genres = page.doc.select("a[itemprop=genre]").map { it.text() })
    }
    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        val page = page(animeId)
        val doc = page.doc
        val episodes = if (page.movie) listOf(ProviderEpisode("movie", 1, "Смотреть фильм")) else
            doc.select(".b-simple_episode__item").map { ep ->
                val season = ep.attr("data-season_id").toIntOrNull() ?: 1
                val number = ep.attr("data-episode_id").toIntOrNull()
                ProviderEpisode("$season:${ep.attr("data-episode_id")}", number, "Сезон $season · ${ep.text()}", season = season)
            }.distinctBy { it.id }
        return ProviderAnimeDetails(id, animeId, doc.select(".b-post__title h1").text(),
            doc.select(".b-post__origtitle").text(), doc.select(".b-post__description_text").text(),
            doc.selectFirst(".b-sidecover img, img[data-caption-title]")?.absUrl("src").orEmpty(),
            Regex("(?:19|20)\\d{2}").find(doc.select(".b-post__info").text())?.value.orEmpty(), displayName,
            doc.select("a[itemprop=genre]").map { it.text() }, episodes,
            if (page.movie) AnimeKind.MOVIE else AnimeKind.SERIES)
    }
    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> = supervisorScope {
        val page = page(animeId)
        val voices = page.doc.select("#translators-list > li.b-translator__item").map {
            it.attr("data-translator_id") to it.attr("title").ifBlank { it.text() }
        }.ifEmpty { listOf(page.translator to "Основная озвучка") }
        val limit = Semaphore(2)
        voices.map { (key, voice) -> async { limit.withPermit {
            try {
                val form = mutableMapOf("id" to page.id, "translator_id" to key,
                    "favs" to page.doc.select("#ctrl_favs").attr("value"), "action" to if (page.movie) "get_movie" else "get_stream")
                if (!page.movie) {
                    val parts = episodeId.split(':')
                    if (parts.size != 2) return@withPermit null
                    form["season"] = parts[0]; form["episode"] = parts[1]
                }
                val response = withTimeoutOrNull(5_000) { http.postForm("$SITE/ajax/get_cdn_series/?t=${System.currentTimeMillis()}", form,
                    mapOf("Referer" to animeId, "Origin" to SITE, "X-Requested-With" to "XMLHttpRequest")) } ?: return@withPermit null
                val json = JSONObject(response)
                if (!json.optBoolean("success")) return@withPermit null
                val streams = parseMediaList(json.optString("url"), animeId, voice)
                if (streams.isEmpty()) null else ProviderSource(key, voice, "HDRezka", streams)
            } catch (error: Exception) { currentCoroutineContext().ensureActive(); null }
        } } }.awaitAll().filterNotNull()
    }
    override suspend fun isAvailable() = runCatching { browse(1).isNotEmpty() }.getOrDefault(false)
    private companion object { const val SITE = "https://hdrezka-home.tv" }
}
