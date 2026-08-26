package ru.radiationx.anilibria.screen.history

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import javax.inject.Inject

class ProviderHistoryViewModel @Inject constructor(
    private val repository: ProviderLocalRepository,
    private val router: LibriaCardRouter,
) : BaseCardsViewModel() {
    override val defaultTitle: String = "Другие источники"

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> = repository.getHistory()
        .groupBy { it.provider to it.animeId }
        .values
        .mapNotNull { list -> list.maxByOrNull { it.watchedAt } }
        .sortedByDescending { it.watchedAt }
        .map { item ->
            LibriaCard(
                title = item.animeTitle,
                description = buildString {
                    append(item.provider.uiName)
                    item.episodeNumber?.let { append(" • серия $it") }
                    if (item.progressPercent > 0) append(" • ${item.progressPercent}%")
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
            )
        }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false
    override fun onLibriaCardClick(card: LibriaCard) = router.navigate(card)
}
