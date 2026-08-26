package ru.radiationx.anilibria.screen.animevost.home

import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import javax.inject.Inject

class AnimeVostNavigationViewModel @Inject constructor(
    private val repository: AnimeVostRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Каталог AnimeVost"

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val nav = repository.getNavigation()
        return buildList {
            nav.sections.forEach { link -> add(link.toCard("Раздел")) }
            nav.genres.forEach { link -> add(link.toCard("Жанр")) }
            nav.types.forEach { link -> add(link.toCard("Тип")) }
            nav.years.take(20).forEach { link -> add(link.toCard("Год")) }
        }.distinctBy { (it.type as? LibriaCard.Type.AnimeVostCatalog)?.path }
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) = cardRouter.navigate(card)

    private fun com.animevost.sdk.model.CatalogLink.toCard(kind: String) = LibriaCard(
        title = title,
        description = kind,
        image = "",
        type = LibriaCard.Type.AnimeVostCatalog(path = path, title = "AnimeVost — $title"),
    )
}
