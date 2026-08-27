package ru.radiationx.anilibria.screen.history

import ru.radiationx.anilibria.animevost.AnimeVostFavoritesRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.favorites.LocalFavoritesRepository
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import ru.radiationx.data.interactors.ReleaseInteractor
import javax.inject.Inject

class LocalFavoritesViewModel @Inject constructor(
    private val localFavoritesRepository: LocalFavoritesRepository,
    private val animeVostFavoritesRepository: AnimeVostFavoritesRepository,
    private val providerLocalRepository: ProviderLocalRepository,
    private val releaseInteractor: ReleaseInteractor,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
    private val unifiedLibrary: ru.radiationx.anilibria.provider.UnifiedLibraryRepository,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Избранное"

    override fun onResume() {
        super.onResume()
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val aniLibria = localFavoritesRepository.getIds().mapNotNull { id ->
            releaseInteractor.getFull(id)?.let { release -> converter.toCard(release) }
        }
        val animeVost = animeVostFavoritesRepository.getFavorites().map { item ->
            LibriaCard(
                title = item.animeTitle,
                description = item.originalTitle,
                image = item.posterUrl,
                type = LibriaCard.Type.AnimeVost(item.animeUrl),
            )
        }
        val providers = providerLocalRepository.getFavorites().map { item ->
            LibriaCard(
                title = item.title,
                description = item.extra.ifBlank { item.provider.uiName },
                image = item.posterUrl,
                type = LibriaCard.Type.Provider(item.provider.wireId, item.animeId),
            )
        }
        return unifiedLibrary.deduplicate(providers + animeVost + aniLibria, favorites = true)
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }
}
