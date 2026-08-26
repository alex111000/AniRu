package ru.radiationx.anilibria.provider

import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import ru.radiationx.anilibria.provider.impl.AniLibriaProvider
import ru.radiationx.anilibria.provider.impl.AnimeVostProvider
import ru.radiationx.anilibria.provider.impl.SameBandProvider
import ru.radiationx.anilibria.provider.impl.YummyAnimeProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class ProviderRegistry @Inject constructor(
    aniLibriaProvider: AniLibriaProvider,
    animeVostProvider: AnimeVostProvider,
    yummyAnimeProvider: YummyAnimeProvider,
    sameBandProvider: SameBandProvider,
) {

    private val providers: Map<ProviderId, AnimeProvider> = listOf(
        aniLibriaProvider,
        animeVostProvider,
        yummyAnimeProvider,
        sameBandProvider,
    ).associateBy { it.id }

    private data class HealthCache(val value: Boolean, val checkedAt: Long)
    private val healthCache = ConcurrentHashMap<ProviderId, HealthCache>()

    fun get(providerId: ProviderId): AnimeProvider =
        providers[providerId] ?: throw ProviderException("Unknown provider: ${providerId.wireId}")

    fun searchableProviders(): List<AnimeProvider> = providers.values.filter { it.capabilities.search }

    fun genericUiProviders(): List<AnimeProvider> = providers.values.filter {
        it.id == ProviderId.YUMMY_ANIME || it.id == ProviderId.SAMEBAND
    }

    suspend fun searchGenericProviders(query: String): Map<ProviderId, List<ProviderAnime>> = supervisorScope {
        genericUiProviders().associate { provider ->
            provider.id to async {
                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    runCatching { provider.search(query) }.getOrDefault(emptyList())
                }.orEmpty()
            }
        }.mapValues { (_, deferred) -> deferred.await() }
    }

    suspend fun isAvailable(providerId: ProviderId, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force) {
            healthCache[providerId]
                ?.takeIf { now - it.checkedAt < HEALTH_CACHE_MS }
                ?.let { return it.value }
        }
        val value = withTimeoutOrNull(HEALTH_TIMEOUT_MS) {
            runCatching { get(providerId).isAvailable() }.getOrDefault(false)
        } ?: false
        healthCache[providerId] = HealthCache(value, now)
        return value
    }

    /**
     * Playback safety net for generic providers. If one source has no usable
     * stream, search other providers for the same title and episode number.
     */
    suspend fun findAlternativeStreams(
        title: String,
        episodeNumber: Int?,
        excludeProviders: Set<ProviderId>,
    ): AlternativeStreams? = supervisorScope {
        val candidates = providers.values
            .filter { it.id !in excludeProviders && it.capabilities.search && it.capabilities.details && it.capabilities.playback }
            .map { provider ->
                async {
                    withTimeoutOrNull(FALLBACK_PROVIDER_TIMEOUT_MS) {
                        runCatching {
                            val match = provider.search(title)
                                .maxByOrNull { ProviderTitleMatcher.score(title, it.title, it.originalTitle) }
                                ?.takeIf { ProviderTitleMatcher.score(title, it.title, it.originalTitle) >= MIN_TITLE_SCORE }
                                ?: return@runCatching null
                            val details = provider.getDetails(match.id)
                            val episode = when {
                                episodeNumber != null -> details.episodes.firstOrNull { it.number == episodeNumber }
                                else -> details.episodes.firstOrNull()
                            } ?: return@runCatching null
                            val sources = provider.getSources(details.id, episode.id)
                            AlternativeStreams(provider.id, details, episode, sources)
                        }.getOrNull()
                    }
                }
            }
        candidates.mapNotNull { it.await() }
            .firstOrNull { alt -> alt.sources.any { it.streams.isNotEmpty() } }
    }

    data class AlternativeStreams(
        val provider: ProviderId,
        val details: ProviderAnimeDetails,
        val episode: ProviderEpisode,
        val sources: List<ProviderSource>,
    )

    private companion object {
        const val SEARCH_TIMEOUT_MS = 8_000L
        const val HEALTH_TIMEOUT_MS = 5_000L
        const val HEALTH_CACHE_MS = 5L * 60L * 1000L
        const val FALLBACK_PROVIDER_TIMEOUT_MS = 10_000L
        const val MIN_TITLE_SCORE = 90
    }
}
