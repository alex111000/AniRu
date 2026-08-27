package ru.radiationx.anilibria.provider.impl

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import ru.radiationx.anilibria.provider.AnimeKind

/**
 * Native Kotlin port of the useful, public API flow documented by anicli-api.
 * No Python runtime or local proxy is required inside AniRu.
 */
class YummyAnimeProvider @Inject constructor(
    private val http: ProviderHttpClient,
    private val players: EmbeddedPlayerResolver,
) : AnimeProvider {

    override val id = ProviderId.YUMMY_ANIME
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(
        search = true,
        details = true,
        playback = true,
        multipleVoices = true,
        browse = true,
    )

    private data class RawVideo(
        val number: Int,
        val dubbing: String,
        val player: String,
        val iframeUrl: String,
    )

    private data class DetailsCache(val at: Long, val value: ProviderAnimeDetails)
    private data class VideosCache(val at: Long, val value: Map<Int, List<RawVideo>>)

    private val detailsCache = ConcurrentHashMap<String, DetailsCache>()
    private val videosCache = ConcurrentHashMap<String, VideosCache>()

    override suspend fun search(query: String): List<ProviderAnime> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        val url = "$API_BASE/anime?q=${encode(normalized)}&offset=0&limit=$SEARCH_LIMIT"
        val root = JSONObject(http.get(url, cacheControl = "max-age=60"))
        return root.optJSONArray("response").orEmptyObjects()
            .mapNotNull { it.toProviderAnime() }
            .distinctBy { it.id }
    }

    override suspend fun getDetails(animeId: String): ProviderAnimeDetails {
        val now = System.currentTimeMillis()
        detailsCache[animeId]
            ?.takeIf { now - it.at < DETAILS_CACHE_MS }
            ?.let { return it.value }

        val root = JSONObject(http.get("$API_BASE/anime?ids=${encode(animeId)}", cacheControl = "max-age=300"))
        val item = root.optJSONArray("response")?.optJSONObject(0)
            ?: throw ProviderContentNotFoundException(id, animeId)
        val videos = runCatching { loadVideos(animeId) }.getOrDefault(emptyMap())
        val episodes = videos.keys.sorted().map { number ->
            ProviderEpisode(
                id = number.toString(),
                number = number,
                title = "Серия $number",
                thumbnailUrl = poster(item),
            )
        }
        val rating = item.optJSONObject("rating")?.optDouble("average", Double.NaN)
            ?.takeUnless { it.isNaN() || it <= 0.0 }
        val genres = item.optJSONArray("genres").orEmptyObjects()
            .mapNotNull { it.optString("title").takeIf(String::isNotBlank) }
        val type = item.optJSONObject("type")?.optString("name").orEmpty()
        val status = item.optJSONObject("anime_status")?.optString("title").orEmpty()
        val year = item.optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty()
        val extra = listOfNotNull(
            displayName,
            year.takeIf(String::isNotBlank),
            type.takeIf(String::isNotBlank),
            status.takeIf(String::isNotBlank),
            rating?.let { "★ ${"%.1f".format(it)}" },
            episodes.takeIf { it.isNotEmpty() }?.let { "Серий: ${it.size}" },
            genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ).joinToString(" • ")

        val details = ProviderAnimeDetails(
            provider = id,
            id = animeId,
            title = item.optString("title").ifBlank { "YummyAnime" },
            description = item.optString("description"),
            posterUrl = poster(item),
            year = year,
            extra = extra,
            genres = genres,
            episodes = episodes,
            kind = AnimeKind.parse(type),
            rating = rating,
        )
        detailsCache[animeId] = DetailsCache(now, details)
        return details
    }

    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> = supervisorScope {
        val episodeNumber = episodeId.toIntOrNull() ?: return@supervisorScope emptyList()
        val videos = runCatching { loadVideos(animeId) }.getOrDefault(emptyMap())[episodeNumber].orEmpty()
        videos.map { raw ->
            async {
                val streams = withTimeoutOrNull(SOURCE_RESOLVE_TIMEOUT_MS) {
                    runCatching { resolve(raw, animeId, episodeNumber) }.getOrDefault(emptyList())
                }.orEmpty().distinctBy { it.stableKey }.sortedByDescending { it.quality }
                if (streams.isEmpty()) null else ProviderSource(
                    id = "${raw.player}:${raw.dubbing}:${raw.iframeUrl.hashCode()}",
                    title = raw.dubbing.ifBlank { raw.player.ifBlank { "YummyAnime" } },
                    player = raw.player,
                    streams = streams,
                )
            }
        }.awaitAll().filterNotNull().distinctBy { it.id }
    }

    private var fullCatalog: List<ProviderAnime> = emptyList()
    override suspend fun browse(page: Int): List<ProviderAnime> {
        if (page == 1 || fullCatalog.isEmpty()) {
            val root = JSONObject(http.get("$API_BASE/anime/catalog", cacheControl = "max-age=120"))
            val data = root.optJSONObject("response")?.optJSONArray("data") ?: root.optJSONArray("response")
            fullCatalog = data.orEmptyObjects().mapNotNull { it.toProviderAnime() }.distinctBy { it.id }
        }
        return fullCatalog.drop((page - 1) * 60).take(60)
    }

    override suspend fun isAvailable(): Boolean = runCatching {
        JSONObject(http.get("$API_BASE/anime/schedule", cacheControl = "no-cache"))
            .has("response")
    }.getOrDefault(false)

    private suspend fun loadVideos(animeId: String): Map<Int, List<RawVideo>> {
        val now = System.currentTimeMillis()
        videosCache[animeId]
            ?.takeIf { now - it.at < VIDEOS_CACHE_MS }
            ?.let { return it.value }

        val root = JSONObject(http.get("$API_BASE/anime/${encode(animeId)}/videos", cacheControl = "max-age=120"))
        val map = linkedMapOf<Int, MutableList<RawVideo>>()
        root.optJSONArray("response").orEmptyObjects().forEach { obj ->
            val number = obj.optString("number").toDoubleOrNull()?.toInt()
                ?: obj.optInt("number", -1).takeIf { it >= 0 }
                ?: return@forEach
            val data = obj.optJSONObject("data")
            val iframe = normalizeUrl(obj.optString("iframe_url"))
            if (iframe.isBlank()) return@forEach
            map.getOrPut(number) { mutableListOf() } += RawVideo(
                number = number,
                dubbing = data?.optString("dubbing").orEmpty(),
                player = data?.optString("player").orEmpty(),
                iframeUrl = iframe,
            )
        }
        val immutable = map.mapValues { it.value.toList() }
        videosCache[animeId] = VideosCache(now, immutable)
        return immutable
    }

    private suspend fun resolve(raw: RawVideo, animeId: String, episodeNumber: Int): List<ProviderStream> {
        val url = raw.iframeUrl
        if (url.contains("alloha", ignoreCase = true)) return emptyList()
        directStream(url, raw.dubbing)?.let { return listOf(it) }

        if (url.contains("/iframeCVH.html", ignoreCase = true)) {
            val cvh = resolveCdnVideoHub(url, animeId, episodeNumber, raw.dubbing)
            if (cvh.isNotEmpty()) return cvh
        }

        // Generic PlayerJS/direct-media fallback. It intentionally fails closed for
        // obfuscated players instead of passing an iframe URL to Media3.
        return players.resolve(url, YUMMY_SITE, raw.dubbing)
    }

    private suspend fun resolveCdnVideoHub(
        iframeUrl: String,
        animeId: String,
        episodeNumber: Int,
        fallbackDubbing: String,
    ): List<ProviderStream> {
        val iframeHtml = http.get(iframeUrl, headers = mapOf("Referer" to YUMMY_SITE))
        val doc = Jsoup.parse(iframeHtml, iframeUrl)
        val scriptUrl = doc.selectFirst("script[type=module][crossorigin][src], script[type=module][src]")
            ?.absUrl("src")
            .orEmpty()
        if (scriptUrl.isBlank()) return emptyList()
        val script = http.get(scriptUrl, headers = mapOf("Referer" to iframeUrl))
        val publisher = PUBLISHER_REGEX.find(script)?.groupValues?.getOrNull(1).orEmpty()
        val aggregator = AGGREGATOR_REGEX.find(script)?.groupValues?.getOrNull(1).orEmpty()
        if (publisher.isBlank() || aggregator.isBlank()) return emptyList()

        val query = parseQuery(iframeUrl)
        val titleId = query["anime_id"].orEmpty().ifBlank { animeId }
        val dubbingCode = decode(query["dubbing_code"].orEmpty()).ifBlank { fallbackDubbing }
        val requestedEpisode = query["episode"]?.toIntOrNull() ?: episodeNumber
        val playlistUrl = "$CVH_API/playlist?pub=${encode(publisher)}&aggr=${encode(aggregator)}&id=${encode(titleId)}"
        val playlist = JSONObject(http.get(playlistUrl, headers = mapOf("Referer" to iframeUrl)))
        val item = playlist.optJSONArray("items").orEmptyObjects().firstOrNull { candidate ->
            candidate.optInt("episode", -1) == requestedEpisode &&
                candidate.optString("voiceStudio").equals(dubbingCode, ignoreCase = true)
        } ?: return emptyList() // Never silently substitute another dubbing.
        val vkId = item.optString("vkId")
        if (vkId.isBlank()) return emptyList()
        return loadCvhVideo(vkId, iframeUrl, fallbackDubbing)
    }

    private suspend fun loadCvhVideo(vkId: String, referer: String, sourceTitle: String): List<ProviderStream> {
        val root = JSONObject(http.get("$CVH_API/video/${encode(vkId)}", headers = mapOf("Referer" to referer)))
        val sources = root.optJSONObject("sources") ?: return emptyList()
        val result = mutableListOf<ProviderStream>()
        QUALITY_KEYS.forEach { (key, quality) ->
            val url = sources.optString(key)
            if (url.isNotBlank()) {
                result += ProviderStream(
                    url = normalizeUrl(url),
                    quality = quality,
                    type = StreamType.MP4,
                    headers = mapOf("User-Agent" to ProviderHttpClient.USER_AGENT, "Referer" to referer),
                    sourceTitle = sourceTitle,
                )
            }
        }
        val maxQuality = result.maxOfOrNull { it.quality } ?: 720
        sources.optString("hlsUrl").takeIf(String::isNotBlank)?.let { url ->
            result += ProviderStream(
                url = normalizeUrl(url),
                quality = maxQuality,
                type = StreamType.HLS,
                headers = mapOf("User-Agent" to ProviderHttpClient.USER_AGENT, "Referer" to referer),
                sourceTitle = sourceTitle,
            )
        }
        sources.optString("dashUrl").takeIf(String::isNotBlank)?.let { url ->
            result += ProviderStream(
                url = normalizeUrl(url),
                quality = maxQuality,
                type = StreamType.DASH,
                headers = mapOf("User-Agent" to ProviderHttpClient.USER_AGENT, "Referer" to referer),
                sourceTitle = sourceTitle,
            )
        }
        return result.filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
    }

    private suspend fun resolveGenericIframe(url: String, sourceTitle: String): List<ProviderStream> {
        val html = http.get(url, headers = mapOf("Referer" to YUMMY_SITE))
        val normalized = html
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")
        val result = mutableListOf<ProviderStream>()

        PLAYER_FILE_REGEX.findAll(normalized).forEach { match ->
            parsePlayerFile(url, match.groupValues[1], sourceTitle).forEach(result::add)
        }
        DIRECT_MEDIA_REGEX.findAll(normalized).forEach { match ->
            val mediaUrl = match.value.replace("\\/", "/")
            directStream(mediaUrl, sourceTitle)?.let(result::add)
        }
        return result.distinctBy { it.stableKey }
    }

    private fun parsePlayerFile(baseUrl: String, value: String, sourceTitle: String): List<ProviderStream> = value
        .split(',')
        .mapNotNull { rawPart ->
            val raw = rawPart.trim()
            val quality = QUALITY_PREFIX.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val path = raw.replaceFirst(QUALITY_PREFIX_FULL, "").trim()
            val full = absoluteUrl(baseUrl, path)
            directStream(full, sourceTitle, quality)
        }

    private fun directStream(url: String, sourceTitle: String, qualityHint: Int = 0): ProviderStream? {
        val clean = normalizeUrl(url)
        val type = when {
            clean.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
            clean.contains(".mpd", ignoreCase = true) -> StreamType.DASH
            clean.contains(".mp4", ignoreCase = true) -> StreamType.MP4
            else -> return null
        }
        val quality = qualityHint.takeIf { it > 0 }
            ?: QUALITY_IN_URL.find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 0
        return ProviderStream(
            url = clean,
            quality = quality,
            type = type,
            headers = mapOf("User-Agent" to ProviderHttpClient.USER_AGENT, "Referer" to YUMMY_SITE),
            sourceTitle = sourceTitle,
        )
    }

    private fun JSONObject.toProviderAnime(): ProviderAnime? {
        val animeId = optInt("anime_id", -1).takeIf { it >= 0 }?.toString() ?: return null
        val title = optString("title").trim()
        if (title.isBlank()) return null
        val year = optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty()
        val status = optJSONObject("anime_status")?.optString("title").orEmpty()
        val type = optJSONObject("type")?.optString("name").orEmpty()
        return ProviderAnime(
            provider = id,
            id = animeId,
            title = title,
            description = optString("description"),
            posterUrl = poster(this),
            year = year,
            extra = listOf(displayName, year, type, status).filter { it.isNotBlank() }.joinToString(" • "),
            kind = AnimeKind.parse(type),
            genres = optJSONArray("genres").orEmptyObjects().map { it.optString("title") },
            rating = optJSONObject("rating")?.optDouble("average")?.takeIf { it.isFinite() && it > 0 },
        )
    }

    private fun poster(obj: JSONObject): String = normalizeUrl(
        obj.optJSONObject("poster")?.let { poster ->
            poster.optString("medium").ifBlank {
                poster.optString("big").ifBlank { poster.optString("fullsize") }
            }
        }.orEmpty()
    )

    private fun parseQuery(url: String): Map<String, String> = runCatching {
        URI(url).rawQuery.orEmpty().split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            pair.getOrNull(0)?.takeIf(String::isNotBlank)?.let { it to pair.getOrElse(1) { "" } }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun absoluteUrl(base: String, path: String): String = runCatching {
        when {
            path.startsWith("//") -> "https:$path"
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> URI(base).resolve(path).toString()
        }
    }.getOrDefault(path)

    private fun normalizeUrl(value: String): String = when {
        value.isBlank() -> ""
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YUMMY_SITE$value"
        else -> value
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> = buildList {
        val array = this@orEmptyObjects ?: return@buildList
        for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
    }

    private companion object {
        const val API_BASE = "https://api.yani.tv"
        const val YUMMY_SITE = "https://yummy-anime.ru"
        const val CVH_API = "https://plapi.cdnvideohub.com/api/v1/player/sv"
        const val SEARCH_LIMIT = 30
        const val DETAILS_CACHE_MS = 5L * 60L * 1000L
        const val VIDEOS_CACHE_MS = 2L * 60L * 1000L
        const val SOURCE_RESOLVE_TIMEOUT_MS = 9_000L
        const val CATALOG_LIMIT = 60

        val PUBLISHER_REGEX = Regex("\\\"data-publisher-id\\\":\\s?(\\d+)")
        val AGGREGATOR_REGEX = Regex("\\\"data-aggregator\\\":\\s?\\\"([^\\\"]+)\\\"")
        val PLAYER_FILE_REGEX = Regex("(?:file|src)\\s*[:=]\\s*[\\\"']([^\\\"']+(?:m3u8|mp4|mpd)[^\\\"']*)[\\\"']", RegexOption.IGNORE_CASE)
        val DIRECT_MEDIA_REGEX = Regex("https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mp4|mpd)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
        val QUALITY_PREFIX = Regex("\\[(\\d{3,4})p]", RegexOption.IGNORE_CASE)
        val QUALITY_PREFIX_FULL = Regex("^\\[\\d{3,4}p]", RegexOption.IGNORE_CASE)
        val QUALITY_IN_URL = Regex("(?:^|[^0-9])(360|480|720|1080|1440|2160)(?:p|[^0-9])", RegexOption.IGNORE_CASE)
        val QUALITY_KEYS = linkedMapOf(
            "mpegTinyUrl" to 144,
            "mpegLowestUrl" to 240,
            "mpegLowUrl" to 360,
            "mpegMediumUrl" to 480,
            "mpegHighUrl" to 720,
            "mpegFullHdUrl" to 1080,
            "mpegQhdUrl" to 1440,
            "mpeg2kUrl" to 2048,
            "mpeg4kUrl" to 2160,
        )
    }
}
