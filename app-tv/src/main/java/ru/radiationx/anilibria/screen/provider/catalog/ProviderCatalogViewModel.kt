package ru.radiationx.anilibria.screen.provider.catalog

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.quill.QuillExtra
import javax.inject.Inject

data class ProviderCatalogExtra(val providerId: String, val title: String) : QuillExtra

class ProviderCatalogViewModel @Inject constructor(
    private val extra: ProviderCatalogExtra,
    private val registry: ProviderRegistry,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {
    override val defaultTitle: String = extra.title
    private val providerId = requireNotNull(ProviderId.fromWireId(extra.providerId))

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> =
        registry.get(providerId).browse(requestPage).map { item ->
            LibriaCard(
                title = item.title,
                description = item.extra.ifBlank { providerId.uiName },
                image = item.posterUrl,
                type = LibriaCard.Type.Provider(providerId.wireId, item.id),
            )
        }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun onLibriaCardClick(card: LibriaCard) = cardRouter.navigate(card)
}
