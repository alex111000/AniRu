package ru.radiationx.anilibria.screen.history

import ru.radiationx.anilibria.animevost.AnimeVostHistoryRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import javax.inject.Inject

class AnimeVostHistoryViewModel @Inject constructor(
    private val historyRepository: AnimeVostHistoryRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "AnimeVost — история"

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> =
        historyRepository.getHistory().map { item ->
            LibriaCard(
                title = item.animeTitle,
                description = buildString {
                    item.episodeNumber?.let { append("Серия $it") }
                    if (item.episodeName.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(item.episodeName)
                    }
                    if (item.progressPercent in 1..99) {
                        if (isNotEmpty()) append(" • ")
                        append("${item.progressPercent}%")
                    }
                },
                image = item.posterUrl,
                type = LibriaCard.Type.AnimeVostEpisode(
                    animeUrl = item.animeUrl,
                    videoId = item.videoId,
                    episodeName = item.episodeName,
                    episodeNumber = item.episodeNumber,
                ),
            )
        }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }
}
