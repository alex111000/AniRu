package ru.radiationx.anilibria.screen.animevost.home

import com.animevost.sdk.model.CatalogSort
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import javax.inject.Inject

abstract class BaseAnimeVostSectionViewModel(
    private val repository: AnimeVostRepository,
    private val cardRouter: LibriaCardRouter,
    private val sort: CatalogSort,
) : BaseCardsViewModel() {

    override val preventClearOnRefresh: Boolean = true

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> =
        repository.getCuratedSection(sort).map { item ->
            LibriaCard(
                title = item.title,
                description = buildString {
                    item.originalTitle?.takeIf { it.isNotBlank() }?.let { append(it) }
                    item.episodeInfo?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" • ")
                        append(it)
                    }
                    item.rating?.let {
                        if (isNotEmpty()) append(" • ")
                        append("★ ")
                        append(it)
                    }
                },
                image = item.posterUrl.orEmpty(),
                type = LibriaCard.Type.AnimeVost(item.url),
            )
        }

    override fun hasMoreCards(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }
}

class AnimeVostLatestViewModel @Inject constructor(
    repository: AnimeVostRepository,
    cardRouter: LibriaCardRouter,
) : BaseAnimeVostSectionViewModel(repository, cardRouter, CatalogSort.DATE) {
    override val defaultTitle: String = "Новые серии"
}

class AnimeVostPopularViewModel @Inject constructor(
    repository: AnimeVostRepository,
    cardRouter: LibriaCardRouter,
) : BaseAnimeVostSectionViewModel(repository, cardRouter, CatalogSort.VIEWS) {
    override val defaultTitle: String = "Популярное"
}

class AnimeVostRatingViewModel @Inject constructor(
    repository: AnimeVostRepository,
    cardRouter: LibriaCardRouter,
) : BaseAnimeVostSectionViewModel(repository, cardRouter, CatalogSort.RATING) {
    override val defaultTitle: String = "Лучшее по рейтингу"
}

class AnimeVostDiscussedViewModel @Inject constructor(
    repository: AnimeVostRepository,
    cardRouter: LibriaCardRouter,
) : BaseAnimeVostSectionViewModel(repository, cardRouter, CatalogSort.COMMENTS) {
    override val defaultTitle: String = "Обсуждают"
}
