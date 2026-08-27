package ru.radiationx.anilibria.provider

import android.content.Context
import ru.radiationx.anilibria.animevost.AnimeVostFavoritesRepository
import ru.radiationx.anilibria.animevost.AnimeVostHistoryRepository
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.favorites.LocalFavoritesRepository
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.types.ReleaseId
import javax.inject.Inject

/** Read-through migration: legacy stores remain intact; new choices override them explicitly. */
class UnifiedLibraryRepository @Inject constructor(context: Context,
    private val catalog: UnifiedCatalogRepository,
    private val local: ProviderLocalRepository,
    private val vostFavorites: AnimeVostFavoritesRepository,
    private val vostHistory: AnimeVostHistoryRepository,
    private val nativeFavorites: LocalFavoritesRepository,
    private val nativeHistory: EpisodesCheckerHolder,
) {
    private val prefs = context.getSharedPreferences("aniru_unified_library_v2", Context.MODE_PRIVATE)
    private fun refs(provider: ProviderId, id: String): List<Pair<ProviderId, String>> =
        catalog.group(provider, id)?.versions?.map { it.provider to it.id } ?: listOf(provider to id)
    private fun key(ref: Pair<ProviderId, String>) = "${ref.first.wireId}|${ref.second}"
    fun favorite(provider: ProviderId, id: String): Boolean {
        val refs = refs(provider, id)
        val override = refs.mapNotNull { ref -> prefs.getString("favorite:${key(ref)}", null) }
            .maxByOrNull { it.substringBefore(':').toLongOrNull() ?: 0 }
        if (override != null) return override.substringAfter(':') == "true"
        return refs.any { (p, i) -> local.isFavorite(p, i) || when (p) {
            ProviderId.ANIMEVOST -> vostFavorites.isFavorite(i)
            ProviderId.ANILIBRIA -> i.toIntOrNull()?.let { nativeFavorites.isFavorite(ReleaseId(it)) } == true
            else -> false
        } }
    }
    fun toggleFavorite(details: ProviderAnimeDetails): Boolean {
        val selected = !favorite(details.provider, details.id)
        val edit = prefs.edit()
        refs(details.provider, details.id).forEach { edit.putString("favorite:${key(it)}", "${System.currentTimeMillis()}:$selected") }
        edit.apply()
        if (local.isFavorite(details.provider, details.id) != selected) local.toggleFavorite(ProviderLocalRepository.Favorite(
            details.provider, details.id, details.title, details.posterUrl, details.extra, System.currentTimeMillis()))
        return selected
    }
    fun voice(provider: ProviderId, id: String): String? = refs(provider, id)
        .mapNotNull { prefs.getString("voice:${key(it)}", null) }
        .maxByOrNull { it.substringBefore(':').toLongOrNull() ?: 0 }?.substringAfter(':')
    fun rememberVoice(provider: ProviderId, id: String, voice: String) {
        val edit = prefs.edit()
        refs(provider, id).forEach { edit.putString("voice:${key(it)}", "${System.currentTimeMillis()}:$voice") }; edit.apply()
    }
    data class Resume(val position: Long, val duration: Long, val at: Long, val completed: Boolean)
    suspend fun resume(provider: ProviderId, id: String, episode: ProviderEpisode): Resume? {
        val references = refs(provider, id)
        val found = local.getHistory().filter { history ->
            (history.provider to history.animeId) in references && history.season == episode.season &&
                history.special == episode.special && ((history.provider == provider && history.animeId == id && history.episodeId == episode.id) ||
                    (episode.number != null && history.numberLabel == episode.numberLabel))
        }.map { Resume(it.positionMs, it.durationMs, it.watchedAt, it.isCompleted) }.toMutableList()
        if (episode.season == 1 && !episode.special) references.forEach { (p, i) -> when (p) {
            ProviderId.ANIMEVOST -> vostHistory.getEpisodeProgress(i).filter { it.videoId == episode.id || (episode.number != null && it.episodeNumber == episode.number) }
                .forEach { found += Resume(it.positionMs, it.durationMs, it.watchedAt, it.isCompleted) }
            ProviderId.ANILIBRIA -> i.toIntOrNull()?.let { release ->
                nativeHistory.getEpisodes(ReleaseId(release)).filter { it.id.id.toString() == episode.numberLabel }
                    .forEach { found += Resume(it.seek, 0, it.lastAccessRaw, it.isViewed) }
            }
            else -> Unit
        } }
        return found.maxByOrNull { it.at }
    }
    private fun reference(card: LibriaCard): Pair<ProviderId, String>? = when (val type = card.type) {
        is LibriaCard.Type.Release -> ProviderId.ANILIBRIA to type.releaseId.id.toString()
        is LibriaCard.Type.AnimeVost -> ProviderId.ANIMEVOST to type.animeUrl
        is LibriaCard.Type.AnimeVostEpisode -> ProviderId.ANIMEVOST to type.animeUrl
        is LibriaCard.Type.Provider -> ProviderId.fromWireId(type.providerId)?.let { it to type.animeId }
        is LibriaCard.Type.ProviderEpisode -> ProviderId.fromWireId(type.providerId)?.let { it to type.animeId }
        else -> null
    }
    fun deduplicate(cards: List<LibriaCard>, favorites: Boolean = false): List<LibriaCard> = cards
        .filter { card -> !favorites || reference(card)?.let { favorite(it.first, it.second) } != false }
        .distinctBy { card -> reference(card)?.let { catalog.group(it.first, it.second)?.key ?: key(it) } ?: card.getId().toString() }
        .map { card ->
            val reference = reference(card) ?: return@map card
            when (val type = card.type) {
                is LibriaCard.Type.Release, is LibriaCard.Type.AnimeVost -> card.copy(type = LibriaCard.Type.Provider(reference.first.wireId, reference.second))
                is LibriaCard.Type.AnimeVostEpisode -> card.copy(type = LibriaCard.Type.ProviderEpisode(reference.first.wireId, reference.second,
                    type.videoId, type.episodeNumber, directPlay = true))
                else -> card
            }
        }
}
