package ru.radiationx.anilibria.screen.main

import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import javax.inject.Inject

class MainAnimeVostViewModel @Inject constructor(
    private val repository: AnimeVostRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "AnimeVost — новые серии"
    override val preventClearOnRefresh: Boolean = true

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> =
        repository.getCatalog(requestPage).items.map { item ->
            LibriaCard(
                title = item.title,
                description = listOfNotNull(
                    item.originalTitle?.takeIf { it.isNotBlank() },
                    item.episodeInfo?.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
                image = item.posterUrl.orEmpty(),
                type = LibriaCard.Type.AnimeVost(item.url),
            )
        }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }
}
