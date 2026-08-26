package ru.radiationx.anilibria.animevost

import com.animevost.sdk.AnimeVostClient
import com.animevost.sdk.model.AnimeDetails
import com.animevost.sdk.model.AnimeEpisode
import com.animevost.sdk.model.AnimePage
import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.CatalogFilter
import com.animevost.sdk.model.CatalogSort
import com.animevost.sdk.model.NavigationData
import com.animevost.sdk.model.PlaylistEpisode
import com.animevost.sdk.model.ScheduleDay
import com.animevost.sdk.model.VideoSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class AnimeVostRepository @Inject constructor() {

    private val client = AnimeVostClient()
    private val catalogMutex = Mutex()
    private val poolMutex = Mutex()
    private val detailsLocks = ConcurrentHashMap<String, Mutex>()

    private data class DetailsCacheEntry(val value: AnimeDetails, val savedAt: Long)
    private data class PosterCacheEntry(val posterUrl: String?, val savedAt: Long)
    private val detailsCache = ConcurrentHashMap<String, DetailsCacheEntry>()
    private val posterCache = ConcurrentHashMap<String, PosterCacheEntry>()

    @Volatile private var cachedRecentPool: List<AnimePreview> = emptyList()
    @Volatile private var cachedRecentPoolAt: Long = 0L
    @Volatile private var cachedNavigation: NavigationData? = null
    @Volatile private var cachedNavigationAt: Long = 0L
    @Volatile private var cachedSchedule: List<ScheduleDay> = emptyList()
    @Volatile private var cachedScheduleAt: Long = 0L

    suspend fun getCatalog(
        page: Int = 1,
        sort: CatalogSort = CatalogSort.DATE,
        path: String? = null,
    ): AnimePage = catalogMutex.withLock {
        withTimeout(NETWORK_TIMEOUT_MS) {
            client.getAnimeList(
                page = page,
                filter = CatalogFilter(path = path, sortBy = sort),
            )
        }
    }

    suspend fun getCuratedSection(sort: CatalogSort): List<AnimePreview> {
        if (sort == CatalogSort.DATE) return getCatalog(page = 1).items

        // Rank curated rows locally from recent catalog pages. This keeps the TV
        // home screen independent from AnimeVost's DLE sort-cookie POST and avoids
        // firing several sort requests at the site at the same time.
        val pool = getRecentPool()
        return when (sort) {
            CatalogSort.VIEWS -> pool.sortedWith(
                compareByDescending<AnimePreview> { it.viewCount ?: 0 }
                    .thenByDescending { it.rating ?: 0.0 }
            )
            CatalogSort.RATING -> pool.sortedWith(
                compareByDescending<AnimePreview> { it.rating ?: 0.0 }
                    .thenByDescending { it.voteCount ?: 0 }
                    .thenByDescending { it.viewCount ?: 0 }
            )
            CatalogSort.COMMENTS -> pool.sortedWith(
                compareByDescending<AnimePreview> { it.commentCount ?: 0 }
                    .thenByDescending { it.viewCount ?: 0 }
            )
            CatalogSort.TITLE -> pool.sortedBy { it.title.lowercase() }
            CatalogSort.DATE -> pool
        }.take(CURATED_ITEMS)
    }

    private suspend fun getRecentPool(): List<AnimePreview> = poolMutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedRecentPool.isNotEmpty() && now - cachedRecentPoolAt < POOL_CACHE_MS) {
            return@withLock cachedRecentPool
        }

        val result = mutableListOf<AnimePreview>()
        for (page in 1..POOL_PAGES) {
            val items = runCatching { getCatalog(page = page).items }.getOrNull().orEmpty()
            if (items.isEmpty()) break
            result += items
        }
        cachedRecentPool = result.distinctBy { it.url }
        cachedRecentPoolAt = now
        cachedRecentPool
    }

    /**
     * Details are enriched with the full AnimeVost playlist. This avoids the old HTML
     * `var data` limitation on very long titles such as One Piece and Naruto.
     */
    suspend fun getDetails(url: String, forceRefresh: Boolean = false): AnimeDetails {
        val key = url.trim()
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            detailsCache[key]?.takeIf { now - it.savedAt < DETAILS_CACHE_MS }?.let { return it.value }
        }

        val lock = detailsLocks.getOrPut(key) { Mutex() }
        return lock.withLock {
            if (!forceRefresh) {
                detailsCache[key]?.takeIf { now - it.savedAt < DETAILS_CACHE_MS }?.let { return@withLock it.value }
            }

            val raw = withTimeout(NETWORK_TIMEOUT_MS) { client.getAnimeDetails(key) }
            val playlist = if (raw.id > 0) {
                runCatching {
                    withTimeout(PLAYLIST_TIMEOUT_MS) { client.getPlaylist(raw.id) }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val enriched = raw.copy(episodes = mergeEpisodes(raw, playlist))
            detailsCache[key] = DetailsCacheEntry(enriched, System.currentTimeMillis())
            enriched
        }
    }

    suspend fun getEpisodes(animeUrl: String, forceRefresh: Boolean = false): List<AnimeEpisode> =
        getDetails(animeUrl, forceRefresh).episodes

    /**
     * Returns fresh playback candidates. Direct playlist links are preferred, with the
     * legacy frame5 parser kept as a fallback for compatibility.
     */
    suspend fun getPlaybackSources(
        animeUrl: String,
        videoId: String,
        episodeNumber: Int?,
        forceRefresh: Boolean = false,
    ): List<VideoSource> {
        val details = getDetails(animeUrl, forceRefresh)
        val episode = details.episodes.firstOrNull { it.videoId == videoId }
            ?: episodeNumber?.let { number -> details.episodes.firstOrNull { it.number == number } }

        val result = mutableListOf<VideoSource>()
        episode?.directUrl?.let { result += it.toDirectSource("HD") }
        episode?.standardUrl?.let { result += it.toDirectSource("SD") }

        val legacyVideoId = episode?.videoId?.takeUnless { it.startsWith(SYNTHETIC_VIDEO_PREFIX) }
            ?: videoId.takeUnless { it.startsWith(SYNTHETIC_VIDEO_PREFIX) }
        if (!legacyVideoId.isNullOrBlank()) {
            result += runCatching {
                withTimeout(NETWORK_TIMEOUT_MS) { client.getVideoSources(legacyVideoId) }
            }.getOrDefault(emptyList())
        }
        return result.distinctBy { it.url }
    }

    suspend fun getVideoSources(videoId: String): List<VideoSource> =
        withTimeout(NETWORK_TIMEOUT_MS) { client.getVideoSources(videoId) }

    suspend fun search(query: String, page: Int = 1): AnimePage =
        withTimeout(NETWORK_TIMEOUT_MS) { client.searchAnime(query = query, page = page) }

    suspend fun getNavigation(forceRefresh: Boolean = false): NavigationData {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - cachedNavigationAt < META_CACHE_MS) {
            cachedNavigation?.let { return it }
        }
        val value = withTimeout(NETWORK_TIMEOUT_MS) { client.getNavigation() }
        cachedNavigation = value
        cachedNavigationAt = now
        return value
    }

    suspend fun getSchedule(forceRefresh: Boolean = false): List<ScheduleDay> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSchedule.isNotEmpty() && now - cachedScheduleAt < META_CACHE_MS) {
            return cachedSchedule
        }
        val value = withTimeout(NETWORK_TIMEOUT_MS) { client.getSchedule() }
        cachedSchedule = value
        cachedScheduleAt = now
        return value
    }

    suspend fun getRandomAnime(): AnimePreview? =
        withTimeout(NETWORK_TIMEOUT_MS) { client.getRandomAnime() }

    /**
     * Recent AnimeVost titles with real poster URLs. Home metadata rows use this
     * as a lightweight visual index so schedule entries can show posters without
     * issuing dozens of detail requests.
     */
    suspend fun getRecentAnimeForVisuals(): List<AnimePreview> = getRecentPool()

    /**
     * Resolve artwork for schedule entries without invoking the playlist endpoint.
     * Schedule markup on AnimeVost is usually text-only even though the anime detail
     * page has a poster. A separate short-lived cache prevents repeated page loads.
     */
    suspend fun getPosterForAnime(animeUrl: String, forceRefresh: Boolean = false): String? {
        val key = normalizeAnimeUrl(animeUrl)
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            posterCache[key]
                ?.takeIf { entry ->
                    val ttl = if (entry.posterUrl.isNullOrBlank()) POSTER_MISS_CACHE_MS else POSTER_CACHE_MS
                    now - entry.savedAt < ttl
                }
                ?.let { return it.posterUrl }

            detailsCache[animeUrl.trim()]
                ?.takeIf { now - it.savedAt < DETAILS_CACHE_MS }
                ?.value
                ?.posterUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { poster ->
                    posterCache[key] = PosterCacheEntry(poster, now)
                    return poster
                }
        }

        val poster = runCatching {
            withTimeout(POSTER_TIMEOUT_MS) {
                client.getAnimeDetails(animeUrl.trim()).posterUrl
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()

        posterCache[key] = PosterCacheEntry(poster, System.currentTimeMillis())
        return poster
    }

    private fun normalizeAnimeUrl(value: String): String =
        runCatching { URI(value.trim()).path.trimEnd('/').lowercase() }
            .getOrDefault(value.trim().substringBefore('?').substringBefore('#').trimEnd('/').lowercase())

    private fun mergeEpisodes(details: AnimeDetails, playlist: List<PlaylistEpisode>): List<AnimeEpisode> {
        if (playlist.isEmpty()) return details.episodes.sortedEpisodeList()

        val legacy = details.episodes
        val merged = playlist.mapIndexed { index, entry ->
            val matchingLegacy = entry.number?.let { number -> legacy.firstOrNull { it.number == number } }
                ?: legacy.firstOrNull { normalizeEpisodeName(it.name) == normalizeEpisodeName(entry.name) }
            val number = entry.number ?: matchingLegacy?.number
            AnimeEpisode(
                name = entry.name.ifBlank { matchingLegacy?.name ?: "${number ?: index + 1} серия" },
                videoId = matchingLegacy?.videoId
                    ?: "$SYNTHETIC_VIDEO_PREFIX${details.id}:${number ?: index + 1}",
                number = number,
                thumbnailUrl = entry.previewUrl ?: matchingLegacy?.thumbnailUrl ?: details.posterUrl,
                directUrl = entry.hdUrl,
                standardUrl = entry.standardUrl,
            )
        }.toMutableList()

        // Preserve specials or entries that exist only in the page parser.
        val knownLegacyIds = merged.map { it.videoId }.toSet()
        legacy.filterNot { it.videoId in knownLegacyIds }
            .filterNot { old -> old.number != null && merged.any { it.number == old.number } }
            .forEach { merged += it }
        return merged.sortedEpisodeList()
    }

    private fun List<AnimeEpisode>.sortedEpisodeList(): List<AnimeEpisode> =
        distinctBy { episode -> episode.number?.let { "n:$it" } ?: "v:${episode.videoId}" }
            .sortedWith(compareBy<AnimeEpisode> { it.number ?: Int.MAX_VALUE }.thenBy { it.name })

    private fun normalizeEpisodeName(value: String): String =
        value.lowercase().replace(Regex("\\s+"), "").replace("серия", "")

    private fun String.toDirectSource(fallbackQuality: String): VideoSource {
        val detected = Regex("/(2160|1440|1080|720|480|360)/").find(this)?.groupValues?.getOrNull(1)
        val host = runCatching { URI(this).host }.getOrNull()
        return VideoSource(
            quality = detected?.let { "${it}p" } ?: fallbackQuality,
            url = this,
            downloadUrl = null,
            host = host,
        )
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L
        const val PLAYLIST_TIMEOUT_MS = 12_000L
        const val POSTER_TIMEOUT_MS = 6_000L
        const val POOL_PAGES = 8
        const val CURATED_ITEMS = 40
        const val POOL_CACHE_MS = 10 * 60 * 1000L
        const val DETAILS_CACHE_MS = 5 * 60 * 1000L
        const val POSTER_CACHE_MS = 30 * 60 * 1000L
        const val POSTER_MISS_CACHE_MS = 60_000L
        const val META_CACHE_MS = 30 * 60 * 1000L
        const val SYNTHETIC_VIDEO_PREFIX = "playlist:"
    }
}
