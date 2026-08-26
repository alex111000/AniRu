package ru.radiationx.anilibria.screen.animevost.details

import androidx.lifecycle.viewModelScope
import com.animevost.sdk.model.AnimeDetails
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.animevost.AnimeVostFavoriteItem
import ru.radiationx.anilibria.animevost.AnimeVostFavoritesRepository
import ru.radiationx.anilibria.animevost.AnimeVostRepository
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.AnimeVostEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.AnimeVostPlayerScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.quill.QuillExtra
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

data class AnimeVostDetailExtra(
    val animeUrl: String,
) : QuillExtra

class AnimeVostDetailsViewModel @Inject constructor(
    private val extra: AnimeVostDetailExtra,
    private val repository: AnimeVostRepository,
    private val favoritesRepository: AnimeVostFavoritesRepository,
    private val router: Router,
    private val guidedRouter: GuidedRouter,
) : LifecycleViewModel() {

    val detailsData = MutableStateFlow<LibriaDetails?>(null)
    val episodesData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val relatedData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val loadingData = MutableStateFlow(false)
    val errorData = MutableStateFlow<String?>(null)

    private var currentAnime: AnimeDetails? = null

    override fun onColdCreate() {
        super.onColdCreate()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            loadingData.value = true
            errorData.value = null

            coRunCatching {
                repository.getDetails(extra.animeUrl)
            }.onSuccess { anime ->
                currentAnime = anime
                detailsData.value = anime.toLibriaDetails(
                    isFavorite = favoritesRepository.isFavorite(anime.url)
                )

                // Do not create 1000+ Leanback cards for very long shows. The full list
                // is available through Смотреть / the episode picker; this row is quick access.
                val visibleEpisodes = if (anime.episodes.size > QUICK_EPISODES_LIMIT) {
                    anime.episodes.takeLast(QUICK_EPISODES_LIMIT)
                } else {
                    anime.episodes
                }
                episodesData.value = visibleEpisodes.map { episode ->
                    LibriaCard(
                        title = episode.name,
                        description = episode.number?.let { "Серия $it" }.orEmpty(),
                        image = episode.thumbnailUrl ?: anime.posterUrl.orEmpty(),
                        type = LibriaCard.Type.AnimeVostEpisode(
                            animeUrl = anime.url,
                            videoId = episode.videoId,
                            episodeName = episode.name,
                            episodeNumber = episode.number,
                        ),
                    )
                }

                relatedData.value = anime.relatedSeries.map { related ->
                    LibriaCard(
                        title = related.title,
                        description = related.description.orEmpty(),
                        image = anime.posterUrl.orEmpty(),
                        type = LibriaCard.Type.AnimeVost(related.url),
                    )
                }
            }.onFailure { error ->
                Timber.e(error)
                errorData.value = error.message ?: "Не удалось загрузить AnimeVost"
            }

            loadingData.value = false
        }
    }

    /**
     * Match AniLibria's TV behavior: Смотреть opens an episode chooser instead of
     * immediately starting the first episode.
     */
    fun onPlayClick() {
        if (episodesData.value.isEmpty()) return
        guidedRouter.open(
            AnimeVostEpisodesGuidedScreen(
                animeUrl = extra.animeUrl,
                currentVideoId = null,
                replacePlayer = false,
            )
        )
    }

    fun onFavoriteClick() {
        val anime = currentAnime ?: return
        val isFavorite = favoritesRepository.toggle(
            AnimeVostFavoriteItem(
                animeUrl = anime.url,
                animeTitle = anime.title,
                posterUrl = anime.posterUrl.orEmpty(),
                originalTitle = anime.originalTitle.orEmpty(),
                savedAt = System.currentTimeMillis(),
            )
        )
        detailsData.value = detailsData.value?.copy(isFavorite = isFavorite)
    }

    fun onCardClick(card: LibriaCard) {
        when (val type = card.type) {
            is LibriaCard.Type.AnimeVostEpisode -> navigateToEpisode(type)
            is LibriaCard.Type.AnimeVost -> router.navigateTo(
                ru.radiationx.anilibria.screen.AnimeVostDetailsScreen(type.animeUrl)
            )
            else -> Unit
        }
    }

    fun retry() {
        load()
    }

    private fun navigateToEpisode(episode: LibriaCard.Type.AnimeVostEpisode) {
        router.navigateTo(
            AnimeVostPlayerScreen(
                animeUrl = episode.animeUrl,
                videoId = episode.videoId,
                episodeName = episode.episodeName,
            )
        )
    }

    private fun AnimeDetails.toLibriaDetails(isFavorite: Boolean): LibriaDetails =
        LibriaDetails(
            id = ReleaseId(id),
            titleRu = title,
            titleEn = originalTitle.orEmpty(),
            extra = listOfNotNull(
                year?.takeIf { it.isNotBlank() },
                type?.takeIf { it.isNotBlank() },
                episodeInfo?.takeIf { it.isNotBlank() },
                episodes.takeIf { it.isNotEmpty() }?.let { "Серий: ${it.size}" },
                director?.takeIf { it.isNotBlank() }?.let { "Режиссёр: $it" },
                rating?.let { "★ $it${voteCount?.let { votes -> " ($votes)" }.orEmpty()}" },
                viewCount?.let { "Просмотров: $it" },
                commentCount?.let { "Комментариев: $it" },
                genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" • "),
            description = description.orEmpty(),
            announce = alternativeTitle.orEmpty(),
            image = posterUrl.orEmpty(),
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
