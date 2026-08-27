package ru.radiationx.anilibria.provider

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class ResolvedSource(val provider: ProviderId, val animeId: String, val episodeId: String, val source: ProviderSource)

/** A short race for the selected episode, not a global wait for every provider. */
class PlaybackResolver @Inject constructor(private val registry: ProviderRegistry, private val catalog: UnifiedCatalogRepository) {
    suspend fun resolve(details: ProviderAnimeDetails, episode: ProviderEpisode, preferredVoice: String?,
        all: Boolean = false, onlyProvider: ProviderId? = null): List<ResolvedSource> {
        val known = (listOf(details.asAnime()) + catalog.group(details.provider, details.id)?.versions.orEmpty()).distinctBy { it.reference }
        val providers = registry.searchableProviders().filter { catalog.enabled(it.id) && it.capabilities.playback &&
            (onlyProvider == null || it.id == onlyProvider) }.sortedBy { p -> known.indexOfFirst { it.provider == p.id }.let { if (it < 0) 100 else it } }
        val limiter = Semaphore(3)
        return boundedSourceRace(providers.map { provider -> suspend {
            limiter.withPermit {
                val direct = known.firstOrNull { it.provider == provider.id }
                val candidate = if (direct != null) {
                    if (direct.provider == details.provider && direct.id == details.id) details else catalog.getDetails(direct.provider, direct.id)
                } else {
                    val names = AnimeIdentity.names(details.asAnime())
                    val found = provider.search(details.originalTitle.ifBlank { details.title })
                        .filter { AnimeIdentity.names(it).intersect(names).isNotEmpty() }.take(2)
                    var matched: ProviderAnimeDetails? = null
                    for (item in found) {
                        val other = catalog.getDetails(provider.id, item.id)
                        if (AnimeIdentity.same(details.asAnime(), other.asAnime())) { matched = other; break }
                    }
                    matched ?: return@withPermit emptyList()
                }
                if (!AnimeIdentity.same(details.asAnime(), candidate.asAnime())) return@withPermit emptyList()
                val otherEpisode = if (candidate.provider == details.provider && candidate.id == details.id) {
                    candidate.episodes.firstOrNull { it.id == episode.id }
                } else if (details.kind == AnimeKind.MOVIE && candidate.kind == AnimeKind.MOVIE && candidate.episodes.size == 1) {
                    candidate.episodes.single()
                } else candidate.episodes.firstOrNull { AnimeIdentity.sameEpisode(episode, it) }
                if (otherEpisode == null) return@withPermit emptyList()
                provider.getSources(candidate.id, otherEpisode.id).filter { it.streams.isNotEmpty() }
                    .map { ResolvedSource(provider.id, candidate.id, otherEpisode.id, it) }
            }
        } }, if (all) 8_000 else 6_000, if (all) null else 450, preferredVoice)
    }
}

internal suspend fun boundedSourceRace(tasks: List<suspend () -> List<ResolvedSource>>, budgetMs: Long,
    graceMs: Long?, preferredVoice: String?): List<ResolvedSource> = supervisorScope {
    val results = Channel<List<ResolvedSource>>(Channel.UNLIMITED)
    val jobs = tasks.map { task -> launch {
        val value = try { task() } catch (error: Exception) { currentCoroutineContext().ensureActive(); emptyList() }
        results.send(value)
    } }
    val found = mutableListOf<ResolvedSource>()
    try {
        withTimeoutOrNull(budgetMs) {
            var remaining = tasks.size
            var ready = false
            while (remaining > 0 && !ready) {
                found += results.receive(); remaining--
                ready = graceMs != null && found.any { preferredVoice == null || AnimeIdentity.normalize(it.source.title) == AnimeIdentity.normalize(preferredVoice) }
            }
            if (ready && graceMs != null && remaining > 0) withTimeoutOrNull(graceMs) {
                repeat(remaining) { found += results.receive() }
            }
        }
    } finally { jobs.forEach { it.cancel() }; results.cancel() }
    found.distinctBy { "${it.provider.wireId}|${it.source.id}" }
}
