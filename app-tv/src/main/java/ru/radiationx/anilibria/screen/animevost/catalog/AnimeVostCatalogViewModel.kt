package ru.radiationx.anilibria.screen.animevost.catalog

import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.quill.QuillExtra
import javax.inject.Inject

data class AnimeVostCatalogExtra(
    val path: String?,
    val title: String,
) : QuillExtra

class AnimeVostCatalogViewModel @Inject constructor(
    private val extra: AnimeVostCatalogExtra,
    private val repository: AnimeVostRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = extra.title

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> =
        repository.getCatalog(requestPage, path = extra.path).items.map { item ->
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
                        append("★ $it")
                    }
                },
                image = item.posterUrl.orEmpty(),
                type = LibriaCard.Type.AnimeVost(item.url),
            )
        }

    override fun onLibriaCardClick(card: LibriaCard) = cardRouter.navigate(card)
}
