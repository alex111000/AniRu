package ru.radiationx.anilibria.screen.history

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.repository.HistoryRepository
import javax.inject.Inject

class AniLibriaHistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "AniLibria — история"

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> =
        historyRepository.getReleases().items.map { converter.toCard(it) }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }
}
