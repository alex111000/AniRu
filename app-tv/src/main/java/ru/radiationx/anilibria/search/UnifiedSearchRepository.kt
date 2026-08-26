package ru.radiationx.anilibria.search

import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.provider.ProviderAnime
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

data class UnifiedSearchResult(
    val aniLibria: List<LibriaCard>,
    val animeVost: List<LibriaCard>,
    val yummyAnime: List<LibriaCard>,
    val sameBand: List<LibriaCard>,
) {
    val isEmpty: Boolean
        get() = aniLibria.isEmpty() && animeVost.isEmpty() && yummyAnime.isEmpty() && sameBand.isEmpty()

    val all: List<LibriaCard>
        get() = aniLibria + animeVost + yummyAnime + sameBand
}

class UnifiedSearchRepository @Inject constructor(
    private val searchRepository: SearchRepository,
    private val animeVostRepository: AnimeVostRepository,
    private val providerRegistry: ProviderRegistry,
) {

    suspend fun search(query: String): UnifiedSearchResult = supervisorScope {
        val normalized = query.trim()
        if (normalized.length < 2) {
            return@supervisorScope UnifiedSearchResult(emptyList(), emptyList(), emptyList(), emptyList())
        }

        val aniLibriaDeferred = async {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                runCatching { searchRepository.fastSearch(normalized) }.getOrNull()
            }?.items.orEmpty()
                .distinctBy { it.id }
                .map { item ->
                    LibriaCard(
                        title = item.names.getOrNull(0).orEmpty(),
                        description = item.names.getOrNull(1).orEmpty(),
                        image = item.poster.orEmpty(),
                        type = LibriaCard.Type.Release(item.id),
                    )
                }
        }
        val animeVostDeferred = async {
            val items = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                buildList {
                    for (page in 1..ANIMEVOST_SEARCH_PAGES) {
                        val result = runCatching { animeVostRepository.search(normalized, page) }.getOrNull() ?: break
                        addAll(result.items)
                        if (page >= result.totalPages || result.items.isEmpty()) break
                    }
                }
            }.orEmpty()
            items.distinctBy { it.url }.map { item ->
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
                            append("★ ").append(it)
                        }
                    },
                    image = item.posterUrl.orEmpty(),
                    type = LibriaCard.Type.AnimeVost(item.url),
                )
            }
        }
        val genericDeferred = async { providerRegistry.searchGenericProviders(normalized) }

        val generic = genericDeferred.await()
        UnifiedSearchResult(
            aniLibria = aniLibriaDeferred.await(),
            animeVost = animeVostDeferred.await(),
            yummyAnime = generic[ProviderId.YUMMY_ANIME].orEmpty().map(::toCard),
            sameBand = generic[ProviderId.SAMEBAND].orEmpty().map(::toCard),
        )
    }

    private fun toCard(item: ProviderAnime): LibriaCard = LibriaCard(
        title = item.title,
        description = listOf(item.originalTitle, item.extra, item.year)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" • "),
        image = item.posterUrl,
        type = LibriaCard.Type.Provider(item.provider.wireId, item.id),
    )

    private companion object {
        const val SOURCE_TIMEOUT_MS = 8_000L
        const val ANIMEVOST_SEARCH_PAGES = 3
    }
}
