package ru.radiationx.anilibria.provider.impl

import ru.radiationx.anilibria.provider.AnimeProvider
import ru.radiationx.anilibria.provider.ProviderAnime
import ru.radiationx.anilibria.provider.ProviderAnimeDetails
import ru.radiationx.anilibria.provider.ProviderCapabilities
import ru.radiationx.anilibria.provider.ProviderException
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderSource
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

/**
 * AniLibria keeps its mature native details/player flow. This adapter lets the
 * multi-provider engine treat its search as a provider without replacing the
 * upstream AniLibria domain model.
 */
class AniLibriaProvider @Inject constructor(
    private val searchRepository: SearchRepository,
) : AnimeProvider {

    override val id = ProviderId.ANILIBRIA
    override val displayName = id.uiName
    override val capabilities = ProviderCapabilities(
        search = true,
        details = false,
        playback = false,
        multipleVoices = false,
    )

    override suspend fun search(query: String): List<ProviderAnime> =
        searchRepository.fastSearch(query.trim()).items.map { item ->
            ProviderAnime(
                provider = id,
                id = item.id.id.toString(),
                title = item.names.getOrNull(0).orEmpty(),
                originalTitle = item.names.getOrNull(1).orEmpty(),
                posterUrl = item.poster.orEmpty(),
                extra = displayName,
            )
        }

    override suspend fun getDetails(animeId: String): ProviderAnimeDetails =
        throw ProviderException("AniLibria details use the native AniRu screen")

    override suspend fun getSources(animeId: String, episodeId: String): List<ProviderSource> =
        throw ProviderException("AniLibria playback uses the native AniRu player")

    override suspend fun isAvailable(): Boolean = runCatching {
        searchRepository.fastSearch("Naruto").items.isNotEmpty()
    }.getOrDefault(false)
}
