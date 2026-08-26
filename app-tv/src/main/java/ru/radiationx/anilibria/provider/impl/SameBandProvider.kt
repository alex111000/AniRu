package ru.radiationx.anilibria.provider.impl

import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.radiationx.anilibria.provider.AnimeProvider
import ru.radiationx.anilibria.provider.ProviderAnime
import ru.radiationx.anilibria.provider.ProviderAnimeDetails
import ru.radiationx.anilibria.provider.ProviderCapabilities
import ru.radiationx.anilibria.provider.ProviderContentNotFoundException
import ru.radiationx.anilibria.provider.ProviderEpisode
import ru.radiationx.anilibria.provider.ProviderHttpClient
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderSource
import ru.radiationx.anilibria.provider.ProviderStream
import ru.radiationx.anilibria.provider.StreamType
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class SameBandProvider @Inject constructor(
    private val http: ProviderHttpClient,
) : AnimeProvider {

    override val id: ProviderId = ProviderId.SAMEBAND
    override val displayName: String = id.uiName
    override val capabilities = ProviderCapabilities(
        search = true,
        details = true,
        playback = true,
        multipleVoices = false,
        browse = true,
    )

    private data class CacheEntry(val at: Long, val details: ProviderAnimeDetails)
    private val detailsCache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun search(query: String): List<ProviderAnime> {
        val normalized = query.trim()
        if (normalized.length < MIN_SEARCH_LENGTH) return emptyList()

        val html = http.postForm(
            "$BASE_URL/index.php?do=search",
            form = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to normalized,
            ),
            headers = mapOf("Referer" to BASE_URL),
        )
        return parseCards(html).take(MAX_SEARCH_RESULTS)
    }

    override suspend fun browse(page: Int): List<ProviderAnime> {
        if (page > 1) return emptyList()
        return parseCards(http.get("$BASE_URL/novinki", cacheControl = "max-age=120"))
    }

    private fun parseCards(html: String): List<ProviderAnime> {
        val doc = Jsoup.parse(html, BASE_URL)
        return doc.select(".col-auto")
            .mapNotNull { node ->
                val link = node.selectFirst(".image[href]")?.absUrl("href")
                    .orEmpty()
                    .ifBlank { node.selectFirst("a[href]")?.absUrl("href").orEmpty() }
                val title = node.selectFirst(".poster[title]")?.attr("title").orEmpty().trim()
                if (link.isBlank() || title.isBlank()) return@mapNotNull null
                ProviderAnime(
                    provider = id,
                    id = link,
                    title = title,
                    posterUrl = findImageUrl(node, "img.swiper-lazy, img"),
                    extra = displayName,
                )
            }
            .distinctBy { it.id }
    }

    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        val now = System.currentTimeMillis()
        detailsCache[animeId]
            ?.takeIf { now - it.at < DETAILS_CACHE_MS }
            ?.let { return it.details }

        val html = http.get(normalizeAnimeUrl(animeId), headers = mapOf("Referer" to BASE_URL))
        val doc = Jsoup.parse(html, BASE_URL)
        val title = doc.selectFirst("h1.p-0.m-0")?.text()?.trim().orEmpty()
        if (title.isBlank()) throw ProviderContentNotFoundException(id, animeId)

        val description = doc.select(".limiter p").joinToString(" ") { it.text() }.trim()
        val poster = findImageUrl(doc.body(), ".image > img, img.poster")
        val playerUrl = doc.selectFirst(".player > .player-content > iframe[src], iframe[src]")
            ?.absUrl("src")
            .orEmpty()

        val episodes = if (playerUrl.isNotBlank()) {
            runCatching { loadEpisodes(playerUrl) }.getOrDefault(emptyList())
        } else emptyList()
        val details = ProviderAnimeDetails(
            provider = id,
            id = animeId,
            title = title,
            description = description,
            posterUrl = poster,
            extra = "$displayName • ${episodes.size} серий",
            episodes = episodes.mapIndexed { index, parsed ->
                ProviderEpisode(
                    id = index.toString(),
                    number = index + 1,
                    title = parsed.title.ifBlank { "Серия ${index + 1}" },
                    thumbnailUrl = parsed.thumbnail,
                )
            },
        )
        playlistCache[animeId] = episodes
        detailsCache[animeId] = CacheEntry(now, details)
        return details
    }

    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> {
        val details = getDetails(animeId)
        val index = episodeId.toIntOrNull() ?: return emptyList()
        val parsed = playlistCache[animeId]?.getOrNull(index) ?: run {
            // Details may have been restored from metadata cache after process churn.
            detailsCache.remove(animeId)
            getDetails(animeId)
            playlistCache[animeId]?.getOrNull(index)
        } ?: return emptyList()

        val streams = parsed.streams
            .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
            .sortedByDescending { it.quality }
        if (streams.isEmpty()) return emptyList()
        return listOf(
            ProviderSource(
                id = "sameband-${episodeId}",
                title = "SameBand",
                player = "PlayerJS",
                streams = streams,
            )
        )
    }

    override suspend fun isAvailable(): Boolean = runCatching {
        http.get("$BASE_URL/novinki", cacheControl = "no-cache").isNotBlank()
    }.getOrDefault(false)

    private data class ParsedEpisode(
        val title: String,
        val thumbnail: String,
        val streams: List<ProviderStream>,
    )

    private val playlistCache = ConcurrentHashMap<String, List<ParsedEpisode>>()

    private suspend fun loadEpisodes(playerUrl: String): List<ParsedEpisode> {
        val playerHtml = http.get(playerUrl, headers = mapOf("Referer" to BASE_URL))
        val playlistRaw = PLAYER_FILE_REGEX.find(playerHtml)?.groupValues?.getOrNull(1).orEmpty()
        if (playlistRaw.isBlank()) return emptyList()
        val playlistUrl = absoluteUrl(playerUrl, playlistRaw)
        val rawJson = http.get(playlistUrl, headers = mapOf("Referer" to playerUrl))
        val array = JSONArray(rawJson)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val rawTitle = obj.optString("title")
                val displayTitle = Jsoup.parse(rawTitle).text()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .ifBlank { "Серия ${index + 1}" }
                val thumbnail = ""
                val streams = parseFileList(playlistUrl, obj.optString("file"))
                if (streams.isNotEmpty()) {
                    add(ParsedEpisode(displayTitle, thumbnail, streams))
                }
            }
        }
    }

    private fun parseFileList(baseUrl: String, value: String): List<ProviderStream> = value
        .split(',')
        .mapNotNull { part ->
            val raw = part.trim()
            if (raw.isBlank()) return@mapNotNull null
            val quality = QUALITY_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val path = raw.replaceFirst(QUALITY_PREFIX_REGEX, "").trim()
            if (path.isBlank()) return@mapNotNull null
            val url = absoluteUrl(baseUrl, path)
            ProviderStream(
                url = url,
                quality = quality,
                type = when {
                    url.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
                    url.contains(".mpd", ignoreCase = true) -> StreamType.DASH
                    url.contains(".mp4", ignoreCase = true) -> StreamType.MP4
                    else -> StreamType.UNKNOWN
                },
                headers = mapOf("Referer" to BASE_URL, "User-Agent" to ProviderHttpClient.USER_AGENT),
                sourceTitle = "SameBand",
            )
        }
        .distinctBy { it.url }

    private fun findImageUrl(root: Element, selector: String): String {
        val image = root.selectFirst(selector) ?: return ""
        val raw = sequenceOf("data-src", "data-original", "data-lazy-src", "src")
            .map { image.attr(it).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) }
            .orEmpty()
        if (raw.isBlank()) return ""
        return absoluteUrl(BASE_URL, raw)
    }

    private fun normalizeAnimeUrl(value: String): String = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        else -> absoluteUrl(BASE_URL, value)
    }

    private fun absoluteUrl(base: String, path: String): String = runCatching {
        when {
            path.startsWith("//") -> "https:$path"
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> URI(base).resolve(path).toString()
        }
    }.getOrDefault(path)

    private companion object {
        const val BASE_URL = "https://sameband.studio"
        const val MIN_SEARCH_LENGTH = 4
        const val MAX_SEARCH_RESULTS = 30
        const val DETAILS_CACHE_MS = 5L * 60L * 1000L
        val PLAYER_FILE_REGEX = Regex("Playerjs[^>]+file:\\s*[\\\"']([^>]+?)[\\\"']", RegexOption.IGNORE_CASE)
        val QUALITY_REGEX = Regex("\\[(\\d{3,4})p]", RegexOption.IGNORE_CASE)
        val QUALITY_PREFIX_REGEX = Regex("^\\[\\d{3,4}p]", RegexOption.IGNORE_CASE)
    }
}
