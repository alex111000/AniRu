package ru.radiationx.anilibria.screen.provider.details

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.provider.ProviderAnimeDetails
import ru.radiationx.anilibria.provider.ProviderId
import ru.radiationx.anilibria.provider.ProviderLocalRepository
import ru.radiationx.anilibria.provider.ProviderRegistry
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.ProviderEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.ProviderSourcesGuidedScreen
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.quill.QuillExtra
import timber.log.Timber
import javax.inject.Inject

data class ProviderDetailExtra(
    val providerId: String,
    val animeId: String,
) : QuillExtra

class ProviderDetailsViewModel @Inject constructor(
    private val extra: ProviderDetailExtra,
    private val registry: ProviderRegistry,
    private val localRepository: ProviderLocalRepository,
    private val guidedRouter: GuidedRouter,
) : LifecycleViewModel() {

    val detailsData = MutableStateFlow<LibriaDetails?>(null)
    val episodesData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val loadingData = MutableStateFlow(false)
    val errorData = MutableStateFlow<String?>(null)

    private val providerId: ProviderId = requireNotNull(ProviderId.fromWireId(extra.providerId)) {
        "Unknown provider ${extra.providerId}"
    }
    private var details: ProviderAnimeDetails? = null

    override fun onColdCreate() {
        super.onColdCreate()
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            loadingData.value = true
            errorData.value = null
            runCatching { registry.get(providerId).getDetails(extra.animeId) }
                .onSuccess { loaded ->
                    details = loaded
                    detailsData.value = loaded.toLibriaDetails(localRepository.isFavorite(providerId, loaded.id))
                    val visible = if (loaded.episodes.size > QUICK_EPISODES_LIMIT) {
                        loaded.episodes.takeLast(QUICK_EPISODES_LIMIT)
                    } else loaded.episodes
                    episodesData.value = visible.map { episode ->
                        LibriaCard(
                            title = episode.title,
                            description = listOfNotNull(
                                providerId.uiName,
                                episode.number?.let { "Серия $it" },
                            ).joinToString(" • "),
                            image = episode.thumbnailUrl.ifBlank { loaded.posterUrl },
                            type = LibriaCard.Type.ProviderEpisode(
                                providerId = providerId.wireId,
                                animeId = loaded.id,
                                episodeId = episode.id,
                                episodeNumber = episode.number,
                            ),
                        )
                    }
                }
                .onFailure {
                    Timber.e(it)
                    errorData.value = it.message ?: "Не удалось загрузить ${providerId.uiName}"
                }
            loadingData.value = false
        }
    }

    fun onPlayClick() {
        val current = details ?: return
        if (current.episodes.isEmpty()) return
        guidedRouter.open(
            ProviderEpisodesGuidedScreen(
                providerId = providerId.wireId,
                animeId = current.id,
                currentEpisodeId = null,
                replacePlayer = false,
            )
        )
    }

    fun onFavoriteClick() {
        val current = details ?: return
        val favorite = localRepository.toggleFavorite(
            ProviderLocalRepository.Favorite(
                provider = providerId,
                animeId = current.id,
                title = current.title,
                posterUrl = current.posterUrl,
                extra = current.extra,
                savedAt = System.currentTimeMillis(),
            )
        )
        detailsData.value = detailsData.value?.copy(isFavorite = favorite)
    }

    fun onCardClick(card: LibriaCard) {
        val type = card.type as? LibriaCard.Type.ProviderEpisode ?: return
        guidedRouter.open(
            ProviderSourcesGuidedScreen(
                providerId = type.providerId,
                animeId = type.animeId,
                episodeId = type.episodeId,
                replacePlayer = false,
                currentSourceId = null,
            )
        )
    }

    private fun ProviderAnimeDetails.toLibriaDetails(isFavorite: Boolean): LibriaDetails = LibriaDetails(
        id = ReleaseId(("${provider.wireId}:$id".hashCode() and Int.MAX_VALUE)),
        titleRu = title,
        titleEn = originalTitle,
        extra = extra.ifBlank {
            listOf(provider.uiName, year, genres.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" • ")
        },
        description = description,
        announce = "Источник: ${provider.uiName}",
        image = posterUrl,
        favoriteCount = "0",
        hasFullHd = false,
        isFavorite = isFavorite,
        hasEpisodes = episodes.isNotEmpty(),
        hasViewed = false,
        hasWebPlayer = false,
    )

    private companion object {
        const val QUICK_EPISODES_LIMIT = 40
    }
}
