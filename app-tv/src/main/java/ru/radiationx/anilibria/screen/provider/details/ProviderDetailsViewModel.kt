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
import com.github.terrakok.cicerone.Router
import ru.radiationx.anilibria.provider.UnifiedCatalogRepository
import ru.radiationx.anilibria.provider.UnifiedLibraryRepository
import ru.radiationx.anilibria.screen.ProviderPlayerScreen
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ProviderDetailExtra(
    val providerId: String,
    val animeId: String,
) : QuillExtra

class ProviderDetailsViewModel @Inject constructor(
    private val extra: ProviderDetailExtra,
    private val registry: ProviderRegistry,
    private val localRepository: ProviderLocalRepository,
    private val guidedRouter: GuidedRouter,
    private val catalog: UnifiedCatalogRepository,
    private val library: UnifiedLibraryRepository,
    private val router: Router,
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
            runCatching { catalog.getAvailableDetails(providerId, extra.animeId) }
                .onSuccess { loaded ->
                    details = loaded
                    detailsData.value = loaded.toLibriaDetails(library.favorite(loaded.provider, loaded.id))
                    val visible = loaded.episodes
                    val progress = localRepository.getHistory().filter { it.provider == loaded.provider && it.animeId == loaded.id }.associateBy { it.episodeId }
                    episodesData.value = visible.map { episode ->
                        LibriaCard(
                            title = (if (progress[episode.id]?.isCompleted == true) "✓ " else "") + episode.title,
                            description = listOfNotNull(
                                loaded.provider.uiName,
                                "Сезон ${episode.season}",
                                episode.number?.let { "Серия $it" },
                                progress[episode.id]?.takeIf { !it.isCompleted }?.let { "${it.progressPercent}%" },
                            ).joinToString(" • "),
                            image = episode.thumbnailUrl.ifBlank { loaded.posterUrl },
                            type = LibriaCard.Type.ProviderEpisode(
                                providerId = loaded.provider.wireId,
                                animeId = loaded.id,
                                episodeId = episode.id,
                                episodeNumber = episode.number,
                                directPlay = true,
                            ),
                        )
                    }
                }
                .onFailure {
                    currentCoroutineContext().ensureActive()
                    Timber.e(it)
                    errorData.value = it.message ?: "Не удалось загрузить ${providerId.uiName}"
                }
            loadingData.value = false
        }
    }

    fun onPlayClick() {
        val current = details ?: return
        if (current.episodes.isEmpty()) return
        viewModelScope.launch {
            val episode = library.latestEpisode(current) ?: return@launch
            router.navigateTo(ProviderPlayerScreen(current.provider.wireId, current.id, episode.id, null))
        }
    }

    fun onFavoriteClick() {
        val current = details ?: return
        val favorite = library.toggleFavorite(current)
        detailsData.value = detailsData.value?.copy(isFavorite = favorite)
    }

    fun onCardClick(card: LibriaCard) {
        val type = card.type as? LibriaCard.Type.ProviderEpisode ?: return
        router.navigateTo(ProviderPlayerScreen(type.providerId, type.animeId, type.episodeId, null))
    }

    private fun ProviderAnimeDetails.toLibriaDetails(isFavorite: Boolean): LibriaDetails = LibriaDetails(
        id = ReleaseId(("${provider.wireId}:$id".hashCode() and Int.MAX_VALUE)),
        titleRu = title,
        titleEn = originalTitle,
        extra = extra.ifBlank {
            listOf(provider.uiName, year, genres.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" • ")
        },
        description = description,
        announce = "Источники: " + (catalog.group(provider, id)?.versions?.map { it.provider.uiName } ?: listOf(provider.uiName)).distinct().joinToString(", "),
        image = posterUrl,
        favoriteCount = "0",
        hasFullHd = false,
        isFavorite = isFavorite,
        hasEpisodes = episodes.isNotEmpty(),
        hasViewed = localRepository.getHistory().any { it.provider == provider && it.animeId == id && !it.isCompleted },
        hasWebPlayer = false,
    )

    private companion object {
        const val QUICK_EPISODES_LIMIT = 40
    }
}
