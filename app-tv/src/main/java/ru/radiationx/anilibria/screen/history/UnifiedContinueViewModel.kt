package ru.radiationx.anilibria.screen.history

import ru.radiationx.anilibria.animevost.AnimeVostHistoryRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.HistoryRepository
import javax.inject.Inject

class UnifiedContinueViewModel @Inject constructor(
    private val releaseInteractor: ReleaseInteractor,
    private val historyRepository: HistoryRepository,
    private val episodesCheckerHolder: EpisodesCheckerHolder,
    private val animeVostHistoryRepository: AnimeVostHistoryRepository,
    private val providerLocalRepository: ProviderLocalRepository,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Продолжить просмотр"

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val aniLibriaTimed = runCatching {
            val accesses = episodesCheckerHolder.getEpisodes()
            val lastAccessByRelease = accesses
                .groupBy { it.id.releaseId }
                .mapValues { (_, values) -> values.maxByOrNull { it.lastAccessRaw } }
            val ids = lastAccessByRelease.keys
            if (ids.isEmpty()) return@runCatching emptyList<TimedCard>()
            historyRepository.getReleases().items
                .filter { it.id in ids }
                .map { release ->
                    val access = lastAccessByRelease[release.id]
                    TimedCard(
                        timestamp = access?.lastAccessRaw ?: 0L,
                        card = converter.toCard(release).copy(
                            description = access?.id?.id?.let { "AniLibria • серия $it" }.orEmpty(),
                        ),
                    )
                }
        }.getOrDefault(emptyList())

        val animeVostTimed = animeVostHistoryRepository.getHistory()
            .filterNot { it.isCompleted }
            .map { item ->
            TimedCard(
                timestamp = item.watchedAt,
                card = LibriaCard(
                    title = item.animeTitle,
                    description = buildString {
                        append("AnimeVost")
                        item.episodeNumber?.let { append(" • серия $it") }
                        if (item.progressPercent in 1..99) append(" • ${item.progressPercent}%")
                    },
                    image = item.posterUrl,
                    type = LibriaCard.Type.AnimeVostEpisode(
                        animeUrl = item.animeUrl,
                        videoId = item.videoId,
                        episodeName = item.episodeName,
                        episodeNumber = item.episodeNumber,
                    ),
                ),
            )
        }

        val providerTimed = providerLocalRepository.getHistory()
            .filterNot { it.isCompleted }
            .groupBy { it.provider to it.animeId }
            .values
            .mapNotNull { items -> items.maxByOrNull { it.watchedAt } }
            .map { item ->
                TimedCard(
                    timestamp = item.watchedAt,
                    card = LibriaCard(
                        title = item.animeTitle,
                        description = buildString {
                            append(item.provider.uiName)
                            item.episodeNumber?.let { append(" • серия $it") }
                            if (item.progressPercent in 1..99) append(" • ${item.progressPercent}%")
                        },
                        image = item.posterUrl,
                        type = LibriaCard.Type.ProviderEpisode(
                            providerId = item.provider.wireId,
                            animeId = item.animeId,
                            episodeId = item.episodeId,
                            episodeNumber = item.episodeNumber,
                            sourceId = item.sourceId,
                            directPlay = true,
                        ),
                    ),
                )
            }

        return (aniLibriaTimed + animeVostTimed + providerTimed)
            .sortedByDescending { it.timestamp }
            .map { it.card }
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private data class TimedCard(val timestamp: Long, val card: LibriaCard)
}
