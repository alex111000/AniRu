package ru.radiationx.anilibria.screen.main

import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderRegistry
import javax.inject.Inject

class MainProvidersViewModel @Inject constructor(
    private val registry: ProviderRegistry,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {
    override val defaultTitle: String = "Другие источники"

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> = supervisorScope {
        if (requestPage > 1) return@supervisorScope emptyList()
        val ids = listOf(ProviderId.YUMMY_ANIME, ProviderId.SAMEBAND)
        ids.map { id ->
            async {
                withTimeoutOrNull(8_000L) {
                    runCatching { registry.get(id).browse(1) }.getOrDefault(emptyList())
                }.orEmpty().take(20)
            }
        }.flatMap { it.await() }
            .distinctBy { it.provider to it.id }
            .map { item ->
                LibriaCard(
                    title = item.title,
                    description = item.extra.ifBlank { item.provider.uiName },
                    image = item.posterUrl,
                    type = LibriaCard.Type.Provider(item.provider.wireId, item.id),
                )
            }
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false
    override fun onLibriaCardClick(card: LibriaCard) = cardRouter.navigate(card)
}
